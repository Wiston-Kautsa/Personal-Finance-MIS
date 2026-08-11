package com.wk.pfmis.security;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.wk.pfmis.db.DatabaseHandler;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.function.Supplier;

public final class LoginCredentialStore {
    private static final LoginCredentialStore INSTANCE = new LoginCredentialStore();
    private static final String PREFERENCES_NODE = "com/wk/pfmis/controllers";
    private static final String USERNAME_KEY = "rememberedAccount";
    private static final String PASSWORD_BLOB_KEY = "rememberedPassword";
    private static final String PASSWORD_STORAGE_KEY = "rememberedPasswordStorage";
    private static final String STORAGE_NOTICE_KEY = "rememberedCredentialNotice";
    private static final String INSTALLATION_ID_KEY = "rememberedCredentialInstallationId";
    private static final String LEGACY_TOKEN_KEY = "rememberedLoginToken";
    private static final String WINDOWS_DPAPI_STORAGE = "windows-dpapi-v2";
    private static final String WINDOWS_DPAPI_LEGACY_STORAGE = "windows-dpapi-v1";
    private static final String ENTROPY_PREFIX = "PFMIS_LOGIN_CREDENTIALS|v1|";

    private final Preferences preferences;
    private final CredentialProtector credentialProtector;
    private final Supplier<String> legacyInstallationScopeSupplier;

    private LoginCredentialStore() {
        this(
                Preferences.userRoot().node(PREFERENCES_NODE),
                new WindowsDpapiCredentialProtector(),
                LoginCredentialStore::currentInstallationScope
        );
    }

    LoginCredentialStore(
            Preferences preferences,
            CredentialProtector credentialProtector,
            Supplier<String> legacyInstallationScopeSupplier
    ) {
        this.preferences = preferences;
        this.credentialProtector = credentialProtector;
        this.legacyInstallationScopeSupplier = legacyInstallationScopeSupplier;
    }

    public static LoginCredentialStore getInstance() {
        return INSTANCE;
    }

    public SaveResult save(String usernameOrEmail, char[] password) {
        String account = clean(usernameOrEmail);
        if (account.isBlank() || isBlank(password)) {
            clear();
            return new SaveResult(SaveStatus.NOT_SAVED, "");
        }

        preferences.put(USERNAME_KEY, account);
        preferences.remove(LEGACY_TOKEN_KEY);

        if (!isPasswordProtectionAvailable()) {
            clearPasswordOnly();
            preferences.put(STORAGE_NOTICE_KEY, secureSaveFailureMessage());
            flushWithStatus();
            return new SaveResult(
                    SaveStatus.USERNAME_ONLY,
                    secureSaveFailureMessage()
            );
        }

        byte[] passwordBytes = new byte[0];
        byte[] protectedBytes = new byte[0];
        try {
            passwordBytes = charsToUtf8(password);
            protectedBytes = protectPassword(passwordBytes);
            String protectedBlob = Base64.getEncoder().encodeToString(protectedBytes);
            preferences.put(PASSWORD_BLOB_KEY, protectedBlob);
            preferences.put(PASSWORD_STORAGE_KEY, WINDOWS_DPAPI_STORAGE);
            preferences.remove(STORAGE_NOTICE_KEY);
            boolean flushed = flushWithStatus();
            if (!flushed || !storedCredentialLooksValid(account, protectedBlob, passwordBytes)) {
                clearPasswordOnly();
                preferences.put(STORAGE_NOTICE_KEY, secureSaveFailureMessage());
                flushWithStatus();
                return new SaveResult(SaveStatus.USERNAME_ONLY, secureSaveFailureMessage());
            }
            return new SaveResult(
                    SaveStatus.PASSWORD_SAVED,
                    "Login credentials saved securely on this computer."
            );
        } catch (RuntimeException | LinkageError exception) {
            clearPasswordOnly();
            preferences.put(STORAGE_NOTICE_KEY, secureSaveFailureMessage());
            flushWithStatus();
            return new SaveResult(
                    SaveStatus.USERNAME_ONLY,
                    secureSaveFailureMessage()
            );
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(protectedBytes, (byte) 0);
        }
    }

    public LoadedCredentials load() {
        preferences.remove(LEGACY_TOKEN_KEY);

        String account = clean(preferences.get(USERNAME_KEY, ""));
        String blob = clean(preferences.get(PASSWORD_BLOB_KEY, ""));
        String notice = clean(preferences.get(STORAGE_NOTICE_KEY, ""));

        if (account.isBlank() && blob.isBlank()) {
            preferences.remove(STORAGE_NOTICE_KEY);
            flushQuietly();
            return LoadedCredentials.none();
        }

        if (blob.isBlank()) {
            flushQuietly();
            return new LoadedCredentials(account, new char[0], LoadStatus.USERNAME_ONLY, notice);
        }

        if (account.isBlank()) {
            clear();
            return new LoadedCredentials(
                    "",
                    new char[0],
                    LoadStatus.CORRUPTED,
                    "The saved password could not be restored. Please enter it again."
            );
        }

        if (!isPasswordProtectionAvailable()) {
            clearPasswordOnly();
            flushQuietly();
            return new LoadedCredentials(
                    account,
                    new char[0],
                    LoadStatus.USERNAME_ONLY,
                    "Secure password storage is unavailable on this operating system. Only the username was remembered."
            );
        }

        byte[] protectedBytes = new byte[0];
        byte[] passwordBytes = new byte[0];
        try {
            protectedBytes = Base64.getDecoder().decode(blob);
            UnprotectedPassword unprotected = unprotectPassword(protectedBytes);
            passwordBytes = unprotected.passwordBytes();
            char[] password = utf8ToChars(passwordBytes);
            if (isBlank(password)) {
                Arrays.fill(password, '\0');
                clear();
                return new LoadedCredentials(
                        "",
                        new char[0],
                        LoadStatus.CORRUPTED,
                        "The saved password could not be restored. Please enter it again."
                );
            }
            if (unprotected.usedLegacyEntropy()) {
                migrateLegacyPassword(account, password);
            }
            return new LoadedCredentials(account, password, LoadStatus.COMPLETE, "");
        } catch (RuntimeException | LinkageError exception) {
            clear();
            return new LoadedCredentials(
                    "",
                    new char[0],
                    LoadStatus.CORRUPTED,
                    "The saved password could not be restored. Please enter it again."
            );
        } finally {
            Arrays.fill(protectedBytes, (byte) 0);
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    public boolean hasSavedCredentials() {
        return !clean(preferences.get(USERNAME_KEY, "")).isBlank()
                || !clean(preferences.get(PASSWORD_BLOB_KEY, "")).isBlank();
    }

    public boolean hasSavedPassword() {
        return !clean(preferences.get(PASSWORD_BLOB_KEY, "")).isBlank();
    }

    public boolean isPasswordProtectionAvailable() {
        return credentialProtector.isAvailable();
    }

    public void clear() {
        preferences.remove(USERNAME_KEY);
        clearPasswordOnly();
        preferences.remove(LEGACY_TOKEN_KEY);
        preferences.remove(STORAGE_NOTICE_KEY);
        flushQuietly();
    }

    private void clearPasswordOnly() {
        preferences.remove(PASSWORD_BLOB_KEY);
        preferences.remove(PASSWORD_STORAGE_KEY);
    }

    private byte[] protectPassword(byte[] passwordBytes) {
        return credentialProtector.protect(passwordBytes, entropyBytes());
    }

    private UnprotectedPassword unprotectPassword(byte[] protectedBytes) {
        RuntimeException firstFailure = null;
        try {
            return new UnprotectedPassword(credentialProtector.unprotect(protectedBytes, entropyBytes()), false);
        } catch (RuntimeException exception) {
            firstFailure = exception;
        }

        byte[] legacyEntropy = legacyEntropyBytes();
        if (!Arrays.equals(legacyEntropy, entropyBytes())) {
            try {
                return new UnprotectedPassword(credentialProtector.unprotect(protectedBytes, legacyEntropy), true);
            } catch (RuntimeException ignored) {
                // The original failure has the most useful root cause for diagnostics.
            }
        }
        throw firstFailure;
    }

    private byte[] entropyBytes() {
        return (ENTROPY_PREFIX + stableInstallationId()).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] legacyEntropyBytes() {
        return (ENTROPY_PREFIX + clean(legacyInstallationScopeSupplier.get())).getBytes(StandardCharsets.UTF_8);
    }

    private String stableInstallationId() {
        String installationId = clean(preferences.get(INSTALLATION_ID_KEY, ""));
        if (!installationId.isBlank()) {
            return installationId;
        }
        installationId = UUID.randomUUID().toString();
        preferences.put(INSTALLATION_ID_KEY, installationId);
        flushQuietly();
        return installationId;
    }

    private static String currentInstallationScope() {
        try {
            return DatabaseHandler.applicationDataDirectory().toString();
        } catch (RuntimeException exception) {
            return System.getProperty("user.dir", "PFMIS");
        }
    }

    private byte[] charsToUtf8(char[] password) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        clearByteBuffer(encoded);
        return bytes;
    }

    private char[] utf8ToChars(byte[] bytes) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = new char[decoded.remaining()];
        decoded.get(chars);
        clearCharBuffer(decoded);
        return chars;
    }

    private void clearByteBuffer(ByteBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
    }

    private void clearCharBuffer(CharBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), '\0');
        }
    }

    private boolean storedCredentialLooksValid(String account, String protectedBlob, byte[] passwordBytes) {
        String storedAccount = clean(preferences.get(USERNAME_KEY, ""));
        String storedBlob = clean(preferences.get(PASSWORD_BLOB_KEY, ""));
        String storedScheme = clean(preferences.get(PASSWORD_STORAGE_KEY, ""));
        if (!account.equals(storedAccount) || protectedBlob.isBlank() || !protectedBlob.equals(storedBlob)) {
            return false;
        }
        if (!WINDOWS_DPAPI_STORAGE.equals(storedScheme)) {
            return false;
        }
        String plaintextPassword = new String(passwordBytes, StandardCharsets.UTF_8);
        try {
            return !storedBlob.equals(plaintextPassword) && !storedBlob.contains(plaintextPassword);
        } finally {
            plaintextPassword = "";
        }
    }

    private void migrateLegacyPassword(String account, char[] password) {
        try {
            save(account, password);
        } catch (RuntimeException exception) {
            // The legacy credential was still usable for this launch; keep it rather than breaking sign-in.
        }
    }

    private boolean isBlank(char[] value) {
        if (value == null || value.length == 0) {
            return true;
        }
        for (char character : value) {
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String secureSaveFailureMessage() {
        return "We could not securely save the password on this computer. Your username can still be remembered.";
    }

    private void flushQuietly() {
        flushWithStatus();
    }

    private boolean flushWithStatus() {
        try {
            preferences.flush();
            return true;
        } catch (BackingStoreException exception) {
            // Credential persistence must not block normal manual login.
            return false;
        }
    }

    public interface CredentialProtector {
        boolean isAvailable();

        byte[] protect(byte[] data, byte[] entropy);

        byte[] unprotect(byte[] protectedData, byte[] entropy);
    }

    private static final class WindowsDpapiCredentialProtector implements CredentialProtector {
        @Override
        public boolean isAvailable() {
            return Platform.isWindows();
        }

        @Override
        public byte[] protect(byte[] data, byte[] entropy) {
            return Crypt32Util.cryptProtectData(
                    data,
                    entropy,
                    0,
                    "PFMIS saved login",
                    null
            );
        }

        @Override
        public byte[] unprotect(byte[] protectedData, byte[] entropy) {
            return Crypt32Util.cryptUnprotectData(
                    protectedData,
                    entropy,
                    0,
                    null
            );
        }
    }

    private record UnprotectedPassword(byte[] passwordBytes, boolean usedLegacyEntropy) {
    }

    public enum SaveStatus {
        PASSWORD_SAVED,
        USERNAME_ONLY,
        NOT_SAVED
    }

    public enum LoadStatus {
        NONE,
        COMPLETE,
        USERNAME_ONLY,
        CORRUPTED
    }

    public record SaveResult(SaveStatus status, String userMessage) {
    }

    public record LoadedCredentials(
            String usernameOrEmail,
            char[] password,
            LoadStatus status,
            String userMessage
    ) implements AutoCloseable {
        private static LoadedCredentials none() {
            return new LoadedCredentials("", new char[0], LoadStatus.NONE, "");
        }

        public boolean hasPassword() {
            return password != null && password.length > 0;
        }

        @Override
        public void close() {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }
}

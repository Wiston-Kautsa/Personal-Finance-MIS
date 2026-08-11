package com.wk.pfmis.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginCredentialStoreTest {
    @Test
    void saveFlushesAndRestoresProtectedPasswordWithStableEntropy() throws Exception {
        Preferences preferences = testPreferences();
        LoginCredentialStore store = new LoginCredentialStore(preferences, new FakeProtector(true), () -> "first-path");

        LoginCredentialStore.SaveResult result = store.save("tester@example.com", "SecretPass123".toCharArray());

        assertEquals(LoginCredentialStore.SaveStatus.PASSWORD_SAVED, result.status());
        assertEquals("tester@example.com", preferences.get("rememberedAccount", ""));
        assertEquals("windows-dpapi-v2", preferences.get("rememberedPasswordStorage", ""));
        String blob = preferences.get("rememberedPassword", "");
        assertFalse(blob.isBlank());
        assertFalse(blob.contains("SecretPass123"));

        LoginCredentialStore reopened = new LoginCredentialStore(preferences, new FakeProtector(true), () -> "different-path");
        try (LoginCredentialStore.LoadedCredentials loaded = reopened.load()) {
            assertEquals(LoginCredentialStore.LoadStatus.COMPLETE, loaded.status());
            assertEquals("tester@example.com", loaded.usernameOrEmail());
            assertArrayEquals("SecretPass123".toCharArray(), loaded.password());
        }
        preferences.removeNode();
    }

    @Test
    void unavailableProtectorStoresUsernameOnlyWithInlineWarningMessage() throws Exception {
        Preferences preferences = testPreferences();
        LoginCredentialStore store = new LoginCredentialStore(preferences, new FakeProtector(false), () -> "path");

        LoginCredentialStore.SaveResult result = store.save("tester", "SecretPass123".toCharArray());

        assertEquals(LoginCredentialStore.SaveStatus.USERNAME_ONLY, result.status());
        assertTrue(result.userMessage().contains("could not securely save"));
        assertTrue(store.hasSavedCredentials());
        assertFalse(store.hasSavedPassword());
        try (LoginCredentialStore.LoadedCredentials loaded = store.load()) {
            assertEquals(LoginCredentialStore.LoadStatus.USERNAME_ONLY, loaded.status());
            assertEquals("tester", loaded.usernameOrEmail());
            assertTrue(loaded.userMessage().contains("could not securely save"));
        }
        preferences.removeNode();
    }

    @Test
    void corruptedPasswordBlobClearsSavedCredentials() throws Exception {
        Preferences preferences = testPreferences();
        preferences.put("rememberedAccount", "tester");
        preferences.put("rememberedPassword", "not-base64%");
        preferences.put("rememberedPasswordStorage", "windows-dpapi-v2");
        preferences.flush();
        LoginCredentialStore store = new LoginCredentialStore(preferences, new FakeProtector(true), () -> "path");

        try (LoginCredentialStore.LoadedCredentials loaded = store.load()) {
            assertEquals(LoginCredentialStore.LoadStatus.CORRUPTED, loaded.status());
            assertTrue(loaded.userMessage().contains("could not be restored"));
        }

        assertFalse(store.hasSavedCredentials());
        preferences.removeNode();
    }

    private Preferences testPreferences() throws Exception {
        Preferences preferences = Preferences.userRoot().node("com/wk/pfmis/tests/remember-" + UUID.randomUUID());
        preferences.clear();
        return preferences;
    }

    private static final class FakeProtector implements LoginCredentialStore.CredentialProtector {
        private static final byte[] PREFIX = "PFMIS".getBytes(StandardCharsets.UTF_8);
        private final boolean available;

        private FakeProtector(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public byte[] protect(byte[] data, byte[] entropy) {
            if (!available) {
                throw new IllegalStateException("Protector unavailable");
            }
            byte[] transformed = transform(data, entropy);
            byte[] output = Arrays.copyOf(PREFIX, PREFIX.length + transformed.length);
            System.arraycopy(transformed, 0, output, PREFIX.length, transformed.length);
            return output;
        }

        @Override
        public byte[] unprotect(byte[] protectedData, byte[] entropy) {
            if (!available || protectedData.length < PREFIX.length) {
                throw new IllegalStateException("Protector unavailable");
            }
            for (int index = 0; index < PREFIX.length; index++) {
                if (protectedData[index] != PREFIX[index]) {
                    throw new IllegalStateException("Invalid protected payload");
                }
            }
            byte[] payload = Arrays.copyOfRange(protectedData, PREFIX.length, protectedData.length);
            return transform(payload, entropy);
        }

        private byte[] transform(byte[] data, byte[] entropy) {
            byte[] output = Arrays.copyOf(data, data.length);
            for (int index = 0; index < output.length; index++) {
                output[index] = (byte) (output[index] ^ entropy[index % entropy.length]);
            }
            return output;
        }
    }
}

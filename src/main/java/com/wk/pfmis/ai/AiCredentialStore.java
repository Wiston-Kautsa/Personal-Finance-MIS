package com.wk.pfmis.ai;

import com.wk.pfmis.security.UserSession;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.prefs.Preferences;

public final class AiCredentialStore {
    private static final String API_KEY_PREF = "active_external_api_key";
    private static final String KEY_PURPOSE = "PFMIS-AI-KEY-v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(AiCredentialStore.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private AiCredentialStore() {
    }

    public static void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            clearApiKey();
            return;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, localSecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            PREFERENCES.put(preferenceKey(), Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save AI API key securely.", exception);
        }
    }

    public static String loadApiKey() {
        String stored = PREFERENCES.get(preferenceKey(), "");
        if (stored.isBlank()) {
            return "";
        }
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) {
                return "";
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, localSecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }

    public static void clearApiKey() {
        PREFERENCES.remove(preferenceKey());
    }


    private static String preferenceKey() {
        int userId = UserSession.getWorkspaceUserId();
        return API_KEY_PREF + "_workspace_" + userId;
    }

    private static SecretKeySpec localSecretKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String localMaterial = KEY_PURPOSE
                + "|"
                + System.getProperty("user.name", "")
                + "|"
                + System.getProperty("user.home", "")
                + "|"
                + System.getProperty("os.name", "")
                + "|workspace=" + UserSession.getWorkspaceUserId();
        return new SecretKeySpec(digest.digest(localMaterial.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}

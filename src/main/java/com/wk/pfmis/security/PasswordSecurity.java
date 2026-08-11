package com.wk.pfmis.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordSecurity {
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordSecurity() {
    }

    public static PasswordRecord hash(String password) {
        validatePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new PasswordRecord(
                Base64.getEncoder().encodeToString(derive(password, salt, DEFAULT_ITERATIONS)),
                Base64.getEncoder().encodeToString(salt),
                DEFAULT_ITERATIONS
        );
    }

    public static boolean verify(String password, String expectedHash, String saltBase64, int iterations) {
        if (password == null || expectedHash == null || saltBase64 == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] actual = derive(password, salt, Math.max(iterations, 100_000));
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean needsRehash(int iterations) {
        return iterations < DEFAULT_ITERATIONS;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (char character : password.toCharArray()) {
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
        }
        if (!upper || !lower || !digit) {
            throw new IllegalArgumentException("Password must include an uppercase letter, a lowercase letter, and a number.");
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
            } finally {
                specification.clearPassword();
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable on this computer.", exception);
        }
    }

    public record PasswordRecord(String hash, String salt, int iterations) {
    }
}

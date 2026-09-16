package com.billing.simple.billsoft.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashPassword(String password) {
        if (password == null) return null;
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hashedPassword);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean checkPassword(String candidate, String stored) {
        if (stored == null || stored.isEmpty()) {
            return candidate == null || candidate.isEmpty();
        }
        if (candidate == null) return false;
        try {
            if (stored.contains(":")) {
                String[] parts = stored.split(":", 2);
                byte[] salt = Base64.getDecoder().decode(parts[0]);
                byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt);
                byte[] actualHash = md.digest(candidate.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(expectedHash, actualHash);
            }
        } catch (Exception ignored) {}
        // Fallback for legacy plain-text passwords
        return stored.equals(candidate);
    }
}

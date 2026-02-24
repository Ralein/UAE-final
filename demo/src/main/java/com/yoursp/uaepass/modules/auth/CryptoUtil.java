package com.yoursp.uaepass.modules.auth;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility.
 * <ul>
 * <li>12-byte random IV prepended to ciphertext</li>
 * <li>128-bit GCM authentication tag</li>
 * <li>Output: Base64(IV || ciphertext || tag)</li>
 * </ul>
 * Used to encrypt access tokens stored in Redis and sensitive fields.
 */
public final class CryptoUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {
        // utility class
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     *
     * @param plaintext the string to encrypt
     * @param key       32-byte key (will be hashed / padded to 32 bytes if needed)
     * @return Base64-encoded string containing IV + ciphertext + GCM tag
     */
    public static String encryptAES256(String plaintext, String key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKey secretKey = deriveKey(key);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * Decrypt a Base64-encoded AES-256-GCM ciphertext.
     *
     * @param ciphertext Base64-encoded IV + ciphertext + GCM tag
     * @param key        the same key used for encryption
     * @return decrypted plaintext
     */
    public static String decryptAES256(String ciphertext, String key) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            SecretKey secretKey = deriveKey(key);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * Derive a 256-bit AES key from the given string.
     * Uses SHA-256 hash to ensure exactly 32 bytes.
     */
    private static SecretKey deriveKey(String key) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }
}

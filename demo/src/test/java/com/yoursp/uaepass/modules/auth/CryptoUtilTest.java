package com.yoursp.uaepass.modules.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    private static final String TEST_KEY = "my-super-secret-test-key-32bytes!!!";

    @Test
    @DisplayName("encrypt → decrypt roundtrip should return original plaintext")
    void encryptDecryptRoundTrip() {
        String plaintext = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-access-token";

        String encrypted = CryptoUtil.encryptAES256(plaintext, TEST_KEY);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = CryptoUtil.decryptAES256(encrypted, TEST_KEY);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("same plaintext should produce different ciphertexts (random IV)")
    void differentCiphertextsForSamePlaintext() {
        String plaintext = "same-input-text";

        String encrypted1 = CryptoUtil.encryptAES256(plaintext, TEST_KEY);
        String encrypted2 = CryptoUtil.encryptAES256(plaintext, TEST_KEY);

        assertNotEquals(encrypted1, encrypted2, "Each encryption should use a unique IV");

        // Both should decrypt to the same value
        assertEquals(plaintext, CryptoUtil.decryptAES256(encrypted1, TEST_KEY));
        assertEquals(plaintext, CryptoUtil.decryptAES256(encrypted2, TEST_KEY));
    }

    @Test
    @DisplayName("decryption with wrong key should throw exception")
    void decryptWithWrongKey() {
        String plaintext = "sensitive-data";
        String encrypted = CryptoUtil.encryptAES256(plaintext, TEST_KEY);

        assertThrows(RuntimeException.class,
                () -> CryptoUtil.decryptAES256(encrypted, "wrong-key-completely-different!!"));
    }

    @Test
    @DisplayName("decrypt tampered ciphertext should throw exception")
    void decryptTamperedCiphertext() {
        String plaintext = "do-not-tamper";
        String encrypted = CryptoUtil.encryptAES256(plaintext, TEST_KEY);

        // Tamper with the Base64 ciphertext
        char[] chars = encrypted.toCharArray();
        chars[20] = (chars[20] == 'A') ? 'B' : 'A'; // flip a character
        String tampered = new String(chars);

        assertThrows(RuntimeException.class, () -> CryptoUtil.decryptAES256(tampered, TEST_KEY));
    }

    @Test
    @DisplayName("empty string should encrypt and decrypt successfully")
    void emptyStringRoundTrip() {
        String plaintext = "";
        String encrypted = CryptoUtil.encryptAES256(plaintext, TEST_KEY);
        String decrypted = CryptoUtil.decryptAES256(encrypted, TEST_KEY);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("unicode text should encrypt and decrypt correctly")
    void unicodeRoundTrip() {
        String plaintext = "محمد أحمد — 日本語テスト — 🇦🇪";
        String encrypted = CryptoUtil.encryptAES256(plaintext, TEST_KEY);
        String decrypted = CryptoUtil.decryptAES256(encrypted, TEST_KEY);
        assertEquals(plaintext, decrypted);
    }
}

package com.yulinlin.security.crypto;

import java.nio.charset.StandardCharsets;

/** Thread-safe AES text encryption contract used by web request and response advice. */
public interface AesCrypto {

    String algorithm();

    String encryptBase64(byte[] plaintext);

    byte[] decryptBase64(String base64Ciphertext);

    default String encryptBase64(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        return encryptBase64(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    default String decryptBase64ToString(String base64Ciphertext) {
        return new String(decryptBase64(base64Ciphertext), StandardCharsets.UTF_8);
    }
}

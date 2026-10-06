package com.yulinlin.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Compatibility AES-CBC implementation using the configured fixed IV. */
public final class AesCbcCrypto implements AesCrypto {

    public static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private static final int IV_BYTES = 16;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final SecretKey key;
    private final IvParameterSpec iv;

    public AesCbcCrypto(byte[] key, byte[] iv) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES-CBC key must contain 16, 24 or 32 bytes");
        }
        if (iv == null || iv.length != IV_BYTES) {
            throw new IllegalArgumentException("AES-CBC IV must contain exactly 16 bytes");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
        this.iv = new IvParameterSpec(iv.clone());
    }

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    @Override
    public String encryptBase64(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            return BASE64_ENCODER.encodeToString(cipher.doFinal(plaintext));
        } catch (GeneralSecurityException error) {
            throw new CryptoException("Cannot encrypt data", error);
        }
    }

    @Override
    public byte[] decryptBase64(String base64Ciphertext) {
        if (base64Ciphertext == null || base64Ciphertext.isBlank()) {
            throw new CryptoException("Invalid encrypted data");
        }
        try {
            byte[] ciphertext = BASE64_DECODER.decode(base64Ciphertext.trim());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new CryptoException("Invalid encrypted data", error);
        }
    }
}

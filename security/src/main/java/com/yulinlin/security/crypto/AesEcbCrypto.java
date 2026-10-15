package com.yulinlin.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Simple AES-ECB compatibility implementation without an IV. */
public final class AesEcbCrypto implements AesCrypto {

    public static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final SecretKey key;

    public AesEcbCrypto(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES-ECB key must contain 16, 24 or 32 bytes");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    @Override
    public String encryptBase64(byte[] plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext must not be null");
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
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
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new CryptoException("Invalid encrypted data", error);
        }
    }
}

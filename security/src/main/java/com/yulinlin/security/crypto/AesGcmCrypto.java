package com.yulinlin.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Thread-safe AES-256-GCM encryption service based on the JDK crypto provider.
 */
public final class AesGcmCrypto implements AesCrypto {

    public static final String VERSION = "1";
    public static final String ALGORITHM = "A256GCM";
    public static final int KEY_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SecretKey key;
    private final String deviceType;
    private final SecureRandom secureRandom;

    public AesGcmCrypto(byte[] key, String deviceType) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-GCM key must contain exactly 32 bytes");
        }
        if (deviceType == null || deviceType.isBlank()) {
            throw new IllegalArgumentException("deviceType must not be blank");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
        this.deviceType = deviceType;
        this.secureRandom = new SecureRandom();
    }

    public static AesGcmCrypto fromBase64Key(String base64Key, String deviceType) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("AES key must be configured");
        }
        try {
            return new AesGcmCrypto(Base64.getDecoder().decode(base64Key.trim()), deviceType);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "AES key must be a Base64 encoded 32-byte key", error);
        }
    }

    public static String generateKeyBase64() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_BYTES * Byte.SIZE);
            return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        } catch (GeneralSecurityException error) {
            throw new CryptoException("Cannot generate AES key", error);
        }
    }

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    public CryptoEnvelope encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public CryptoEnvelope encrypt(byte[] plaintext) {
        EncryptedData encrypted = encryptData(plaintext);
        return new CryptoEnvelope(
                VERSION,
                deviceType,
                ALGORITHM,
                URL_ENCODER.encodeToString(encrypted.iv()),
                URL_ENCODER.encodeToString(encrypted.ciphertext()));
    }

    /** Encrypts data as Base64(iv + ciphertext + authentication tag). */
    @Override
    public String encryptBase64(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        return encryptBase64(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String encryptBase64(byte[] plaintext) {
        EncryptedData encrypted = encryptData(plaintext);
        byte[] result = new byte[encrypted.iv().length + encrypted.ciphertext().length];
        System.arraycopy(encrypted.iv(), 0, result, 0, encrypted.iv().length);
        System.arraycopy(encrypted.ciphertext(), 0, result, encrypted.iv().length, encrypted.ciphertext().length);
        return BASE64_ENCODER.encodeToString(result);
    }

    public String decryptToString(CryptoEnvelope envelope) {
        return new String(decrypt(envelope), StandardCharsets.UTF_8);
    }

    public byte[] decrypt(CryptoEnvelope envelope) {
        validate(envelope);
        try {
            byte[] iv = URL_DECODER.decode(envelope.iv());
            if (iv.length != IV_BYTES) {
                throw new CryptoException("Invalid encrypted data");
            }
            byte[] ciphertext = URL_DECODER.decode(envelope.data());
            return decryptData(iv, ciphertext);
        } catch (CryptoException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new CryptoException("Invalid encrypted data", error);
        }
    }

    @Override
    public String decryptBase64ToString(String base64Ciphertext) {
        return new String(decryptBase64(base64Ciphertext), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] decryptBase64(String base64Ciphertext) {
        if (base64Ciphertext == null || base64Ciphertext.isBlank()) {
            throw new CryptoException("Invalid encrypted data");
        }
        try {
            byte[] encrypted = BASE64_DECODER.decode(base64Ciphertext.trim());
            if (encrypted.length < IV_BYTES + TAG_BYTES) {
                throw new CryptoException("Invalid encrypted data");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[encrypted.length - IV_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, IV_BYTES);
            System.arraycopy(encrypted, IV_BYTES, ciphertext, 0, ciphertext.length);
            return decryptData(iv, ciphertext);
        } catch (CryptoException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new CryptoException("Invalid encrypted data", error);
        }
    }

    private byte[] aad() {
        return (VERSION + "\n" + deviceType + "\n" + ALGORITHM).getBytes(StandardCharsets.UTF_8);
    }

    private EncryptedData encryptData(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad());
            return new EncryptedData(iv, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException error) {
            throw new CryptoException("Cannot encrypt data", error);
        }
    }

    private byte[] decryptData(byte[] iv, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad());
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException error) {
            throw new CryptoException("Invalid encrypted data", error);
        }
    }

    private void validate(CryptoEnvelope envelope) {
        if (envelope == null
                || !VERSION.equals(envelope.version())
                || !deviceType.equals(envelope.deviceType())
                || !ALGORITHM.equals(envelope.algorithm())
                || envelope.iv() == null
                || envelope.data() == null) {
            throw new CryptoException("Invalid encrypted data");
        }
    }

    private record EncryptedData(byte[] iv, byte[] ciphertext) {
    }
}

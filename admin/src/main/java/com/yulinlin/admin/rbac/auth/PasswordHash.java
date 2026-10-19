package com.yulinlin.admin.rbac.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用 JDK PBKDF2，哈希中包含算法版本、迭代数、随机盐和校验值。 */
public final class PasswordHash {
    private static final int ITERATIONS = 600000;
    private static final int KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    public String encode(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("密码长度必须为 12 到 128 个字符");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String password, String encoded) {
        if (password == null || password.length() > 128 || encoded == null) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (iterations < ITERATIONS || iterations > 2000000 || salt.length != 16 || expected.length != 32) {
            return false;
        }
        byte[] actual = derive(password, salt, iterations);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("密码哈希初始化失败", error);
        } finally {
            spec.clearPassword();
        }
    }
}

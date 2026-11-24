package com.yulinlin.mcp.token;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 令牌格式 {@code ylmcp_{keyId}_{secret}}：
 * keyId 为 12 位小写字母数字，明文入库用于查找和展示；secret 为 32 字节随机数（base64url），只存 SHA-256。
 */
public final class McpTokenCodec {
    public static final String PREFIX = "ylmcp_";
    public static final int KEY_LENGTH = 12;
    private static final int SECRET_LENGTH = 43;
    private static final char[] KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final Pattern KEY = Pattern.compile("[a-z0-9]{" + KEY_LENGTH + "}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{" + SECRET_LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpTokenCodec() { }

    public record Parsed(String keyId, String secret) {
        @Override public String toString() { return "McpToken[" + keyId + "]"; }
    }

    public static String newKeyId() {
        var chars = new char[KEY_LENGTH];
        for (int i = 0; i < chars.length; i++) chars[i] = KEY_CHARS[RANDOM.nextInt(KEY_CHARS.length)];
        return new String(chars);
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String format(String keyId, String secret) { return PREFIX + keyId + "_" + secret; }

    /** 列表展示用，不含 secret。 */
    public static String display(String keyId) { return PREFIX + keyId + "_****"; }

    /** 不是本格式时返回 null（交给其他认证器）。secret 本身含 '_'，所以按固定长度拆分。 */
    public static Parsed parse(String token) {
        if (token == null || token.length() != PREFIX.length() + KEY_LENGTH + 1 + SECRET_LENGTH || !token.startsWith(PREFIX))
            return null;
        String keyId = token.substring(PREFIX.length(), PREFIX.length() + KEY_LENGTH);
        if (token.charAt(PREFIX.length() + KEY_LENGTH) != '_') return null;
        String secret = token.substring(PREFIX.length() + KEY_LENGTH + 1);
        if (!KEY.matcher(keyId).matches() || !SECRET.matcher(secret).matches()) return null;
        return new Parsed(keyId, secret);
    }

    public static boolean isKeyId(String value) { return value != null && KEY.matcher(value).matches(); }

    public static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 常量时间比较，防止时序攻击。 */
    public static boolean sameHash(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}

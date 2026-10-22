package com.yulinlin.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 无状态工具，不维护第二套会话/凭证映射。 */
public final class SessionSupport {
    private SessionSupport() { }
    public static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " 不能为空或包含首尾空白");
        }
        return value;
    }
    /** 单向摘要只作为管理标识；不能作为鉴权凭证。原生凭证应使用高熵随机值。 */
    public static String id(String namespace, String nativeId) {
        try {
            return namespace + "_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((namespace + "\u0000" + nativeId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String cursorPrefix(String backend, SessionQuery query) {
        String scope = backend + ":" + field(query.userId()) + field(query.deviceType());
        return id("page", scope).substring(0, 29) + ":";
    }
    private static String field(String value) { return value == null ? "-1:" : value.length() + ":" + value; }
    public static int[] cursor(String backend, SessionQuery query, int dimensions) {
        if (query.cursor() == null) return new int[dimensions];
        String prefix = cursorPrefix(backend, query);
        try {
            if (!query.cursor().startsWith(prefix)) throw new IllegalArgumentException();
            String[] parts = query.cursor().substring(prefix.length()).split(":", -1);
            if (parts.length != dimensions) throw new IllegalArgumentException();
            int[] result = new int[dimensions];
            for (int i = 0; i < dimensions; i++) {
                result[i] = Integer.parseInt(parts[i]);
                if (result[i] < 0) throw new IllegalArgumentException();
            }
            return result;
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("无效或不匹配的会话分页游标"); }
    }
}

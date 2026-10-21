package com.yulinlin.data.core.http;

import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** 保留 URL 中已有的转义字节；新 query 参数作为原始值严格编码一次。 */
final class HttpRequestUri {

    private static final String RESERVED = ":/?#[]@!$&'()*+,;=";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HttpRequestUri() { }

    static URI create(String url, MultiValueMap<String, String> query) {
        String encoded = encodeUrl(url);
        if (query.isEmpty()) return URI.create(encoded);
        int fragmentAt = encoded.indexOf('#');
        String fragment = fragmentAt < 0 ? "" : encoded.substring(fragmentAt);
        String base = fragmentAt < 0 ? encoded : encoded.substring(0, fragmentAt);
        StringBuilder result = new StringBuilder(base);
        int queryStart = base.indexOf('?');
        for (var entry : query.entrySet()) {
            for (String value : entry.getValue()) {
                if (queryStart < 0) {
                    queryStart = result.length();
                    result.append('?');
                } else if (result.length() > queryStart + 1 && result.charAt(result.length() - 1) != '&') {
                    result.append('&');
                }
                result.append(UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(UriUtils.encode(value, StandardCharsets.UTF_8));
            }
        }
        return URI.create(result.append(fragment).toString());
    }

    private static String encodeUrl(String url) {
        // 不解析/重组已有 query，保留重复参数顺序、裸参数、转义大小写和签名内容。
        StringBuilder result = new StringBuilder(url.length());
        int schemeEnd = url.indexOf("://");
        int authorityStart = url.startsWith("//") ? 2 : schemeEnd < 0 ? -1 : schemeEnd + 3;
        int authorityEnd = authorityStart;
        while (authorityEnd >= 0 && authorityEnd < url.length()
                && "/?#".indexOf(url.charAt(authorityEnd)) < 0) authorityEnd++;
        for (int offset = 0; offset < url.length();) {
            int c = url.codePointAt(offset);
            if (c == '%' && offset + 2 < url.length()
                    && hex(url.charAt(offset + 1)) && hex(url.charAt(offset + 2))) {
                result.append(url, offset, offset + 3);
                offset += 3;
                continue;
            }
            boolean bracket = c == '[' || c == ']';
            boolean allowedBracket = !bracket || offset >= authorityStart && offset < authorityEnd;
            if (allowedBracket && (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c < 128 && ("-._~".indexOf(c) >= 0 || RESERVED.indexOf(c) >= 0))) {
                result.append((char) c);
            } else {
                if (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE) {
                    throw new IllegalArgumentException("URL 包含不完整的 Unicode 字符");
                }
                for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) {
                    int value = b & 0xff;
                    result.append('%').append(HEX[value >>> 4]).append(HEX[value & 15]);
                }
            }
            offset += Character.charCount(c);
        }
        return result.toString();
    }

    private static boolean hex(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }
}

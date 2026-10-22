package com.yulinlin.security.crypto;

import org.springframework.http.HttpHeaders;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次接口加解密调用的只读请求上下文。
 * deviceType 始终规范化为合法小写设备名；requestHeaders 始终是原请求头的只读副本。
 */
public record CryptoContext(String deviceType, HttpHeaders requestHeaders) {

    private static final Pattern DEVICE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public CryptoContext {
        deviceType = normalizeDeviceType(deviceType);
        Objects.requireNonNull(requestHeaders, "requestHeaders");
        HttpHeaders copiedHeaders = new HttpHeaders();
        requestHeaders.forEach(copiedHeaders::addAll);
        requestHeaders = HttpHeaders.readOnlyHttpHeaders(copiedHeaders);
    }

    /** 供非 Web 场景手动调用，使用空请求头。 */
    public static CryptoContext of(String deviceType) {
        return new CryptoContext(deviceType, new HttpHeaders());
    }

    /** 空值、空白或格式非法时使用 unknown。 */
    public static String normalizeDeviceType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) return "unknown";
        String normalized = deviceType.trim().toLowerCase(Locale.ROOT);
        return DEVICE_NAME.matcher(normalized).matches() ? normalized : "unknown";
    }
}

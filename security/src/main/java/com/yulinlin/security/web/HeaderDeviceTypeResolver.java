package com.yulinlin.security.web;

import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.crypto.CryptoContext;
import org.springframework.http.HttpHeaders;

public final class HeaderDeviceTypeResolver implements DeviceTypeResolver {

    private final String headerName;

    public HeaderDeviceTypeResolver(SecurityCryptoProperties properties) {
        if (properties.getDeviceHeader() == null || properties.getDeviceHeader().isBlank()) {
            throw new IllegalArgumentException("yulinlin.security.crypto.device-header must not be blank");
        }
        this.headerName = properties.getDeviceHeader().trim();
    }

    @Override
    public String resolve(HttpHeaders headers) {
        return headers.getFirst(headerName);
    }

    /** 统一入口规范化；是否支持某个合法设备名由实际 CryptoCodec 决定。 */
    static String normalize(String device) {
        return CryptoContext.normalizeDeviceType(device);
    }
}

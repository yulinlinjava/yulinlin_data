package com.yulinlin.regression.security;

import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import com.yulinlin.security.web.CryptoResponseBodyAdvice;
import com.yulinlin.security.web.HeaderDeviceTypeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.http.MockHttpInputMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只覆盖配置对象与 Advice 行为；不启动 Spring、不执行 YAML 属性绑定或连接外部服务。 */
class CryptoWithoutKeysTest {
    @Test
    void absentEmptyAndBlankKeysDoNotEnableCrypto() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        assertFalse(properties.hasDeviceKeys());
        properties.setDevices(null);
        assertFalse(properties.hasDeviceKeys());
        SecurityCryptoProperties.Device device = new SecurityCryptoProperties.Device();
        properties.setDevices(Map.of("unknown", device));
        assertFalse(properties.hasDeviceKeys());
        device.setKey("");
        assertFalse(properties.hasDeviceKeys());
        device.setKey(" \t ");
        assertFalse(properties.hasDeviceKeys());
        device.setKey("0123456789abcdef");
        assertTrue(properties.hasDeviceKeys());
        properties.setEnabled(false);
        assertTrue(properties.hasDeviceKeys()); // 有密钥时仍需要兼容解密。
    }

    @Test
    void noKeysNeverReadsRequestOrChangesResponseEvenWithAnnotation() throws Exception {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setMaxRequestSize(null);
        properties.setDeviceHeader(null);
        CryptoRequestBodyAdvice requestAdvice = new CryptoRequestBodyAdvice(null, null, properties);
        CryptoResponseBodyAdvice responseAdvice = new CryptoResponseBodyAdvice(null, null, null, properties);
        MethodParameter parameter = parameter(0);
        MethodParameter returnType = parameter(-1);
        assertFalse(requestAdvice.supports(parameter, String.class, StringHttpMessageConverter.class));
        assertFalse(responseAdvice.supports(returnType, StringHttpMessageConverter.class));

        HttpInputMessage input = mock(HttpInputMessage.class);
        assertSame(input, requestAdvice.beforeBodyRead(
                input, parameter, String.class, StringHttpMessageConverter.class));
        verifyNoInteractions(input); // 不读取正文，不改请求头。

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Object body = new Object();
        assertSame(body, responseAdvice.beforeBodyWrite(body, returnType, MediaType.TEXT_PLAIN,
                StringHttpMessageConverter.class, request, response));
        verifyNoInteractions(request, response); // 不附加加密或设备响应头。
    }

    @Test
    void partialConfigurationStillFailsInsteadOfSilentlyDisabling() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        SecurityCryptoProperties.Device device = new SecurityCryptoProperties.Device();
        device.setKey("0123456789abcdef");
        properties.setDevices(Map.of("unknown", device));
        assertTrue(properties.hasDeviceKeys());
        assertThrows(IllegalArgumentException.class, () -> new DeviceCryptoManager(properties)); // 缺少 CBC IV
        device.setIv("abcdef0123456789");
        properties.setDevices(Map.of("web", device));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCryptoManager(properties)); // 缺少 unknown
    }

    @Test
    void configuredKeysKeepCompatibleDecryptionWhenEnabledIsFalse() throws Exception {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        SecurityCryptoProperties.Device device = new SecurityCryptoProperties.Device();
        device.setKey("0123456789abcdef");
        device.setIv("abcdef0123456789");
        properties.setDevices(Map.of("unknown", device));
        properties.setEnabled(false);
        DeviceCryptoManager manager = new DeviceCryptoManager(properties);
        CryptoRequestBodyAdvice advice = new CryptoRequestBodyAdvice(
                manager, new HeaderDeviceTypeResolver(properties), properties);
        String json = "{\"enabled\":false}";
        String encrypted = manager.getRequired("unknown").encryptBase64(json.getBytes(StandardCharsets.UTF_8));
        HttpInputMessage result = advice.beforeBodyRead(new MockHttpInputMessage(encrypted),
                parameter(0), String.class, StringHttpMessageConverter.class);
        assertEquals(json, new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static MethodParameter parameter(int index) throws Exception {
        return new MethodParameter(Endpoint.class.getDeclaredMethod("call", String.class), index);
    }

    @ApiCrypto
    private static class Endpoint {
        public String call(String body) { return body; }
    }
}

package com.yulinlin.regression.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.lang.security.CryptoDataResponse;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoException;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import com.yulinlin.security.web.CryptoResponseBodyAdvice;
import com.yulinlin.security.web.HeaderDeviceTypeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅直接验证公开协议及正文行为，不启动应用、不进行 YAML 属性绑定或连接外部服务。 */
class CryptoCodecTest {
    private final SecurityCryptoProperties properties = new SecurityCryptoProperties();
    private final RecordingCodec codec = new RecordingCodec();

    @Test
    void customCodecWorksWithoutKeysAndReceivesUnmodifiedCiphertext() throws Exception {
        assertFalse(properties.hasDeviceKeys());
        CryptoRequestBodyAdvice advice = requestAdvice(codec);
        assertTrue(advice.supports(parameter("strict", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        String ciphertext = "  \"custom + / payload\"\r\n";
        assertEquals("{\"ok\":true}", read(advice, ciphertext, " Partner_1 ", "strict"));
        assertEquals(ciphertext, codec.ciphertext);
        assertEquals("partner_1", codec.device);

        read(advice, ciphertext, null, "strict");
        assertEquals("unknown", codec.device);
        read(advice, ciphertext, "invalid/device", "strict");
        assertEquals("unknown", codec.device);
    }

    @Test
    void compatibilityModeAcceptsJsonAndStillDecryptsCiphertext() throws Exception {
        properties.setEnabled(false);
        CryptoRequestBodyAdvice advice = requestAdvice(codec);
        String json = "  {\"ok\":false}  ";
        assertEquals(json, read(advice, json, "web", "strict"));
        assertNull(codec.ciphertext);
        assertEquals("{\"ok\":true}", read(advice, "cipher", "web", "strict"));
        assertEquals("cipher", codec.ciphertext);

        CryptoResponseBodyAdvice responses = responseAdvice(codec);
        assertFalse(responses.supports(parameter("strict", -1), MappingJackson2HttpMessageConverter.class));
        Object original = new Object();
        assertSame(original, responses.beforeBodyWrite(original, parameter("strict", -1),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class, null, null));

        properties.setEnabled(true);
        codec.ciphertext = null;
        assertEquals(json, read(requestAdvice(codec), json, "web", "compatible"));
        assertNull(codec.ciphertext);
        assertFalse(responseAdvice(codec).supports(parameter("compatible", -1),
                MappingJackson2HttpMessageConverter.class));
    }

    @Test
    void strictModeRejectsPlaintextAndDecryptFailuresNeverFallBack() throws Exception {
        CryptoRequestBodyAdvice advice = requestAdvice(codec);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> read(advice, "{}", "web", "strict")).getStatusCode().value());
        codec.failure = new CryptoException("拒绝测试密文");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> read(advice, "broken", "web", "strict")).getStatusCode().value());
        codec.failure = null;
        codec.decoded = null;
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> read(advice, "broken", "web", "strict")).getStatusCode().value());
    }

    @Test
    void customCodecStillHonorsEncryptedAndDecryptedSizeLimits() {
        properties.setMaxRequestSize(DataSize.ofBytes(4));
        CryptoRequestBodyAdvice advice = requestAdvice(codec);
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> read(advice, "12345", "web", "strict")).getStatusCode().value());
        assertNull(codec.ciphertext);
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> read(advice, "x", "web", "strict")).getStatusCode().value());
    }

    @Test
    void responseEncryptsOnlyDataAndPreservesRawStringBytes() throws Exception {
        DataResponse body = new DataResponse("字符串内容");
        HttpHeaders headers = new HttpHeaders();
        headers.set(CryptoResponseBodyAdvice.CRYPTO_HEADER, "must-not-claim-AES");
        Object result = write(responseAdvice(codec), body, request("/api/value", "WEB"), headers);
        assertSame(body, result);
        assertEquals("custom-cipher", body.getData());
        assertEquals(200, body.code);
        assertTrue(body.crypt);
        assertEquals("字符串内容", new String(codec.plaintext, StandardCharsets.UTF_8));
        assertEquals("web", headers.getFirst("Device-Type"));
        assertNull(headers.getFirst(CryptoResponseBodyAdvice.CRYPTO_HEADER));

        write(responseAdvice(codec), Map.of("count", 2), request("/api/value", "web"), new HttpHeaders());
        assertEquals(2, new ObjectMapper().readTree(codec.plaintext).get("count").intValue());

        codec.plaintext = null;
        DataResponse empty = new DataResponse(null);
        assertSame(empty, write(responseAdvice(codec), empty, request("/api/value", "web"), new HttpHeaders()));
        assertNull(codec.plaintext);
        assertFalse(empty.crypt);
    }

    @Test
    void invalidEncryptedResponsesAreRejectedRatherThanReturnedAsPlaintext() {
        for (String invalid : new String[]{null, "", "  ", "{}", "  []"}) {
            codec.encoded = invalid;
            assertThrows(CryptoException.class, () -> write(responseAdvice(codec), Map.of("id", 1),
                    request("/api/value", "web"), new HttpHeaders()));
        }
    }

    @Test
    void noCodecOrExcludedPathDoesNotTransformBodies() throws Exception {
        CryptoRequestBodyAdvice noRequestCodec = new CryptoRequestBodyAdvice(null, null, properties);
        CryptoResponseBodyAdvice noResponseCodec = new CryptoResponseBodyAdvice(null, null, null, properties);
        assertFalse(noRequestCodec.supports(parameter("strict", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        assertFalse(noResponseCodec.supports(parameter("strict", -1), MappingJackson2HttpMessageConverter.class));
        HttpInputMessage input = mock(HttpInputMessage.class);
        assertSame(input, noRequestCodec.beforeBodyRead(input, parameter("strict", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        verifyNoInteractions(input);

        Object original = new Object();
        assertSame(original, write(responseAdvice(codec), original,
                request("/monitor/data", "web"), new HttpHeaders()));
        assertNull(codec.plaintext);
        ServerHttpRequest excluded = request("/monitor/data", "web");
        assertSame(excluded, requestAdvice(codec).beforeBodyRead(excluded, parameter("strict", 0),
                String.class, MappingJackson2HttpMessageConverter.class));
        verify(excluded, never()).getBody();
    }

    @Test
    void defaultAesRetainsTransportCompatibilityAndResponseHeaders() throws Exception {
        SecurityCryptoProperties.Device device = new SecurityCryptoProperties.Device();
        device.setKey("0123456789abcdef");
        device.setIv("abcdef0123456789");
        properties.setDevices(Map.of("unknown", device));
        DeviceCryptoManager defaults = new DeviceCryptoManager(properties);
        String json = "{\"enabled\":false}";
        String encrypted = defaults.encrypt("web", json.getBytes(StandardCharsets.UTF_8));
        String transported = "\"" + encrypted.replace('+', ' ') + "\"\r\n";
        assertEquals(json, read(requestAdvice(defaults), transported, "web", "strict"));
        HttpHeaders headers = new HttpHeaders();
        Object response = write(responseAdvice(defaults), Map.of("enabled", false),
                request("/api/value", "web"), headers);
        assertEquals("unknown", headers.getFirst("Device-Type"));
        assertEquals("AES/CBC/PKCS5Padding", headers.getFirst(CryptoResponseBodyAdvice.CRYPTO_HEADER));
        assertFalse(new ObjectMapper().readTree(defaults.decrypt("unknown", (String) response))
                .get("enabled").booleanValue());
    }

    private CryptoRequestBodyAdvice requestAdvice(CryptoCodec selected) {
        return new CryptoRequestBodyAdvice(selected, new HeaderDeviceTypeResolver(properties), properties);
    }

    private CryptoResponseBodyAdvice responseAdvice(CryptoCodec selected) {
        return new CryptoResponseBodyAdvice(selected, new HeaderDeviceTypeResolver(properties),
                new ObjectMapper(), properties);
    }

    private String read(CryptoRequestBodyAdvice advice, String body, String device, String method) throws Exception {
        MockHttpInputMessage input = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        if (device != null) input.getHeaders().set("Device-Type", device);
        HttpInputMessage decoded = advice.beforeBodyRead(input, parameter(method, 0), String.class,
                MappingJackson2HttpMessageConverter.class);
        return new String(decoded.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private Object write(CryptoResponseBodyAdvice advice, Object body,
                         ServerHttpRequest request, HttpHeaders headers) throws Exception {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);
        return advice.beforeBodyWrite(body, parameter("strict", -1), MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class, request, response);
    }

    private ServerHttpRequest request(String path, String device) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Device-Type", device);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("https://example.invalid" + path));
        return request;
    }

    private static MethodParameter parameter(String method, int index) throws Exception {
        return new MethodParameter(Endpoint.class.getDeclaredMethod(method, String.class), index);
    }

    private static class Endpoint {
        @ApiCrypto public String strict(String body) { return body; }
        @ApiCrypto(false) public String compatible(String body) { return body; }
    }

    private static class DataResponse implements CryptoDataResponse {
        private final int code = 200;
        private Object data;
        private boolean crypt;
        private DataResponse(Object data) { this.data = data; }
        @Override public Object getData() { return data; }
        @Override public void onCryptAfter(Object encryptedData) { data = encryptedData; crypt = true; }
    }

    /** 仅用于记录输入的测试替身，不是加密算法。 */
    private static class RecordingCodec implements CryptoCodec {
        private String device;
        private String ciphertext;
        private byte[] plaintext;
        private String encoded = "custom-cipher";
        private byte[] decoded = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        private CryptoException failure;
        @Override public String encrypt(String deviceType, byte[] value) {
            device = deviceType;
            plaintext = value.clone();
            return encoded;
        }
        @Override public byte[] decrypt(String deviceType, String value) {
            device = deviceType;
            ciphertext = value;
            if (failure != null) throw failure;
            return decoded;
        }
    }
}

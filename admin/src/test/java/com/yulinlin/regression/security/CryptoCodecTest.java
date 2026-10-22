package com.yulinlin.regression.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.lang.security.CryptoDataResponse;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;
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
    void globalEncryptionIncludesUnannotatedEndpoints() throws Exception {
        CryptoRequestBodyAdvice requests = requestAdvice(codec);
        CryptoResponseBodyAdvice responses = responseAdvice(codec);
        assertTrue(requests.supports(parameter("plain", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        assertTrue(responses.supports(parameter("plain", -1), MappingJackson2HttpMessageConverter.class));
        assertEquals("{\"ok\":true}", read(requests, "custom-cipher", "web", "plain"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> read(requests, "{}", "web", "plain")).getStatusCode().value());

        DataResponse body = new DataResponse("无注解接口");
        assertSame(body, write(responses, body, request("/api/plain", "web"),
                new HttpHeaders(), parameter("plain", -1)));
        assertEquals("custom-cipher", body.getData());
        assertTrue(body.crypt);
        assertEquals("无注解接口", codec.plaintext);
    }

    @Test
    void globalCompatibilityIncludesEveryAnnotationChoice() throws Exception {
        properties.setEnabled(false);
        CryptoRequestBodyAdvice requests = requestAdvice(codec);
        CryptoResponseBodyAdvice responses = responseAdvice(codec);
        for (String method : new String[]{"plain", "strict", "compatible"}) {
            codec.ciphertext = null;
            codec.plaintext = null;
            assertTrue(requests.supports(parameter(method, 0), String.class,
                    MappingJackson2HttpMessageConverter.class));
            assertFalse(responses.supports(parameter(method, -1), MappingJackson2HttpMessageConverter.class));
            assertEquals("{}", read(requests, "{}", "web", method));
            assertNull(codec.ciphertext);
            assertEquals("{\"ok\":true}", read(requests, "cipher", "web", method));
            DataResponse body = new DataResponse("保持明文");
            assertSame(body, write(responses, body, request("/api/plain", "web"),
                    new HttpHeaders(), parameter(method, -1)));
            assertEquals("保持明文", body.getData());
            assertFalse(body.crypt);
            assertNull(codec.plaintext);
        }
    }

    @Test
    void methodExceptionsOverrideClassSettingsButNotGlobalDisable() throws Exception {
        assertEndpointMode(StrictClassEndpoint.class, "plain", true);
        assertEndpointMode(StrictClassEndpoint.class, "compatible", false);
        assertEndpointMode(CompatibleClassEndpoint.class, "plain", false);
        assertEndpointMode(CompatibleClassEndpoint.class, "strict", true);
        properties.setEnabled(false);
        assertEndpointMode(CompatibleClassEndpoint.class, "strict", false);
    }

    private void assertEndpointMode(Class<?> endpoint, String method, boolean strict) throws Exception {
        MethodParameter input = parameter(endpoint, method, 0);
        MethodParameter output = parameter(endpoint, method, -1);
        CryptoRequestBodyAdvice requests = requestAdvice(codec);
        CryptoResponseBodyAdvice responses = responseAdvice(codec);
        assertTrue(requests.supports(input, String.class, MappingJackson2HttpMessageConverter.class));
        assertEquals(strict, responses.supports(output, MappingJackson2HttpMessageConverter.class));
        if (strict) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> read(requests, "{}", "web", input)).getStatusCode().value());
        } else {
            assertEquals("{}", read(requests, "{}", "web", input));
        }
        assertEquals("{\"ok\":true}", read(requests, "cipher", "web", input));
        DataResponse body = new DataResponse("正文");
        assertSame(body, write(responses, body, request("/api/value", "web"), new HttpHeaders(), output));
        assertEquals(strict, body.crypt);
        assertEquals(strict ? "custom-cipher" : "正文", body.getData());
    }

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
    void customCodecReceivesReadOnlyRequestHeadersInBothDirections() throws Exception {
        MockHttpInputMessage input = new MockHttpInputMessage("cipher".getBytes(StandardCharsets.UTF_8));
        input.getHeaders().set("Device-Type", "WEB");
        input.getHeaders().set("X-Key-Id", "request-key");
        requestAdvice(codec).beforeBodyRead(input, parameter("strict", 0), String.class,
                MappingJackson2HttpMessageConverter.class);

        assertEquals("web", codec.context.deviceType());
        assertEquals("request-key", codec.context.requestHeaders().getFirst("X-Key-Id"));
        assertThrows(UnsupportedOperationException.class,
                () -> codec.context.requestHeaders().set("X-Key-Id", "modified"));
        input.getHeaders().set("X-Key-Id", "changed-after-decrypt");
        assertEquals("request-key", codec.context.requestHeaders().getFirst("X-Key-Id"));

        ServerHttpRequest request = request("/api/value", "APP");
        request.getHeaders().set("X-Key-Id", "response-key");
        write(responseAdvice(codec), Map.of("ok", true), request, new HttpHeaders());

        assertEquals("app", codec.context.deviceType());
        assertEquals("response-key", codec.context.requestHeaders().getFirst("X-Key-Id"));
        request.getHeaders().set("X-Key-Id", "changed-after-encrypt");
        assertEquals("response-key", codec.context.requestHeaders().getFirst("X-Key-Id"));
    }

    @Test
    void cryptoContextAlwaysContainsNormalizedDeviceType() {
        assertEquals("unknown", CryptoContext.of(null).deviceType());
        assertEquals("unknown", CryptoContext.of("  ").deviceType());
        assertEquals("unknown", CryptoContext.of("invalid/device").deviceType());
        assertEquals("partner_1", CryptoContext.of(" Partner_1 ").deviceType());
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
        assertEquals("{\"ok\":true}", read(requestAdvice(codec), "cipher", "web", "compatible"));
        DataResponse body = new DataResponse("例外接口返回明文");
        assertSame(body, write(responseAdvice(codec), body, request("/api/value", "web"),
                new HttpHeaders(), parameter("compatible", -1)));
        assertFalse(body.crypt);
        assertEquals("例外接口返回明文", body.getData());
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
        assertEquals("字符串内容", codec.plaintext);
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
        assertFalse(noRequestCodec.supports(parameter("plain", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        assertFalse(noResponseCodec.supports(parameter("plain", -1), MappingJackson2HttpMessageConverter.class));
        HttpInputMessage input = mock(HttpInputMessage.class);
        assertSame(input, noRequestCodec.beforeBodyRead(input, parameter("strict", 0), String.class,
                MappingJackson2HttpMessageConverter.class));
        verifyNoInteractions(input);

        Object original = new Object();
        assertSame(original, write(responseAdvice(codec), original,
                request("/monitor/data", "web"), new HttpHeaders(), parameter("plain", -1)));
        assertNull(codec.plaintext);
        ServerHttpRequest excluded = request("/monitor/data", "web");
        assertSame(excluded, requestAdvice(codec).beforeBodyRead(excluded, parameter("plain", 0),
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
        String encrypted = defaults.encrypt(CryptoContext.of("web"), json);
        String transported = "\"" + encrypted.replace('+', ' ') + "\"\r\n";
        assertEquals(json, read(requestAdvice(defaults), transported, "web", "plain"));
        HttpHeaders headers = new HttpHeaders();
        Object response = write(responseAdvice(defaults), Map.of("enabled", false),
                request("/api/value", "web"), headers, parameter("plain", -1));
        assertEquals("unknown", headers.getFirst("Device-Type"));
        assertEquals("AES/CBC/PKCS5Padding", headers.getFirst(CryptoResponseBodyAdvice.CRYPTO_HEADER));
        assertFalse(new ObjectMapper().readTree(defaults.decrypt(CryptoContext.of("unknown"), (String) response))
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
        return read(advice, body, device, parameter(method, 0));
    }

    private String read(CryptoRequestBodyAdvice advice, String body, String device, MethodParameter inputType) throws Exception {
        MockHttpInputMessage input = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        if (device != null) input.getHeaders().set("Device-Type", device);
        HttpInputMessage decoded = advice.beforeBodyRead(input, inputType, String.class,
                MappingJackson2HttpMessageConverter.class);
        return new String(decoded.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private Object write(CryptoResponseBodyAdvice advice, Object body,
                         ServerHttpRequest request, HttpHeaders headers) throws Exception {
        return write(advice, body, request, headers, parameter("strict", -1));
    }

    private Object write(CryptoResponseBodyAdvice advice, Object body,
                         ServerHttpRequest request, HttpHeaders headers, MethodParameter returnType) {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);
        return advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON,
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
        return parameter(Endpoint.class, method, index);
    }

    private static MethodParameter parameter(Class<?> endpoint, String method, int index) throws Exception {
        return new MethodParameter(endpoint.getDeclaredMethod(method, String.class), index);
    }

    private static class Endpoint {
        public String plain(String body) { return body; }
        @ApiCrypto public String strict(String body) { return body; }
        @ApiCrypto(false) public String compatible(String body) { return body; }
    }

    @ApiCrypto
    private static class StrictClassEndpoint {
        public String plain(String body) { return body; }
        @ApiCrypto(false) public String compatible(String body) { return body; }
    }

    @ApiCrypto(false)
    private static class CompatibleClassEndpoint {
        public String plain(String body) { return body; }
        @ApiCrypto public String strict(String body) { return body; }
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
        private CryptoContext context;
        private String ciphertext;
        private String plaintext;
        private String encoded = "custom-cipher";
        private String decoded = "{\"ok\":true}";
        private CryptoException failure;
        @Override public String encrypt(CryptoContext context, String value) {
            this.context = context;
            device = context.deviceType();
            plaintext = value;
            return encoded;
        }
        @Override public String decrypt(CryptoContext context, String value) {
            this.context = context;
            device = context.deviceType();
            ciphertext = value;
            if (failure != null) throw failure;
            return decoded;
        }
    }
}

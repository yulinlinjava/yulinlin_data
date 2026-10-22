package com.yulinlin.security.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.lang.security.CryptoDataResponse;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;
import com.yulinlin.security.crypto.CryptoException;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

@ControllerAdvice
@ConditionalOnBean(CryptoCodec.class)
public final class CryptoResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    public static final String CRYPTO_HEADER = "X-Yulinlin-Crypto";

    private final CryptoCodec cryptoCodec;
    private final DeviceTypeResolver deviceTypeResolver;
    private final ObjectMapper objectMapper;
    private final CryptoPathExclusions pathExclusions;
    private final boolean configured;
    private final boolean enabled;
    private final String deviceHeader;

    public CryptoResponseBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties) {
        this(cryptoCodec, deviceTypeResolver, objectMapper, properties,
                cryptoCodec != null ? new CryptoPathExclusions(properties) : null);
    }

    public CryptoResponseBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties,
            CryptoPathExclusions pathExclusions) {
        this.cryptoCodec = cryptoCodec;
        this.deviceTypeResolver = deviceTypeResolver;
        this.objectMapper = objectMapper;
        this.pathExclusions = pathExclusions;
        this.configured = cryptoCodec != null;
        if (configured) {
            Objects.requireNonNull(deviceTypeResolver, "deviceTypeResolver");
            Objects.requireNonNull(objectMapper, "objectMapper");
            Objects.requireNonNull(pathExclusions, "pathExclusions");
        }
        this.enabled = properties.isEnabled();
        if (configured && (properties.getDeviceHeader() == null || properties.getDeviceHeader().isBlank())) {
            throw new IllegalArgumentException("yulinlin.security.crypto.device-header must not be blank");
        }
        this.deviceHeader = configured ? properties.getDeviceHeader().trim() : null;
    }

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return configured && CryptoAnnotations.encryptionRequired(returnType, enabled)
                && !pathExclusions.isCurrentRequestExcluded();
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!configured || !CryptoAnnotations.encryptionRequired(returnType, enabled)
                || pathExclusions.isExcluded(request)) return body;
        boolean stringResponse = StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType);
        if (!stringResponse && !isJson(selectedContentType)) {
            throw new CryptoException("接口加密仅支持 JSON 或字符串响应，请将文件等接口加入 yulinlin.security.crypto.exclude-paths");
        }
        if (body instanceof CryptoDataResponse dataResponse && dataResponse.getData() == null) {
            return body;
        }
        String device;
        String algorithm = null;
        try {
            device = HeaderDeviceTypeResolver.normalize(deviceTypeResolver.resolve(request.getHeaders()));
            // 保留默认 AES 的设备回退及算法响应头；不为自定义协议猜测算法。
            if (cryptoCodec instanceof DeviceCryptoManager defaults) {
                device = defaults.resolveDevice(device);
                algorithm = defaults.getRequired(device).algorithm();
            }
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid crypto device");
        }
        try {
            CryptoContext context = new CryptoContext(device, request.getHeaders());
            Object result;
            if (body instanceof CryptoDataResponse dataResponse) {
                String ciphertext = encrypt(context, dataResponse.getData());
                dataResponse.onCryptAfter(ciphertext);
                result = body;
            } else {
                String ciphertext = encrypt(context, body);
                result = stringResponse ? objectMapper.writeValueAsString(ciphertext) : ciphertext;
            }
            HttpHeaders headers = response.getHeaders();
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (algorithm != null) headers.set(CRYPTO_HEADER, algorithm);
            else headers.remove(CRYPTO_HEADER);
            headers.set(deviceHeader, device);
            return result;
        } catch (JsonProcessingException error) {
            throw new CryptoException("Cannot encrypt response", error);
        }
    }

    private String encrypt(CryptoContext context, Object value) throws JsonProcessingException {
        String ciphertext = cryptoCodec.encrypt(context, plaintext(value));
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new CryptoException("CryptoCodec.encrypt 必须返回非空密文");
        }
        String inspected = ciphertext.stripLeading();
        if (inspected.startsWith("{") || inspected.startsWith("[")) {
            throw new CryptoException("接口密文不能以 { 或 [ 开头，请返回文本密文而非 JSON 对象或数组");
        }
        return ciphertext;
    }

    private String plaintext(Object value) throws JsonProcessingException {
        return value instanceof String text
                ? text
                : objectMapper.writeValueAsString(value);
    }

    private boolean isJson(MediaType mediaType) {
        if (mediaType == null || MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return subtype != null && subtype.endsWith("+json");
    }
}

package com.yulinlin.security.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.lang.security.CryptoDataResponse;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.AesCrypto;
import com.yulinlin.security.crypto.CryptoException;
import com.yulinlin.security.crypto.DeviceCryptoManager;
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

@ControllerAdvice
public final class CryptoResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    public static final String CRYPTO_HEADER = "X-Yulinlin-Crypto";

    private final DeviceCryptoManager cryptoManager;
    private final DeviceTypeResolver deviceTypeResolver;
    private final ObjectMapper objectMapper;
    private final String deviceHeader;

    public CryptoResponseBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties) {
        this.cryptoManager = cryptoManager;
        this.deviceTypeResolver = deviceTypeResolver;
        this.objectMapper = objectMapper;
        if (properties.getDeviceHeader() == null || properties.getDeviceHeader().isBlank()) {
            throw new IllegalArgumentException("yulinlin.security.crypto.device-header must not be blank");
        }
        this.deviceHeader = properties.getDeviceHeader().trim();
    }

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        ApiCrypto annotation = CryptoAnnotations.find(returnType);
        return annotation != null && annotation.value();
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        boolean stringResponse = StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType);
        if (!stringResponse && !isJson(selectedContentType)) {
            throw new CryptoException("@ApiCrypto response only supports JSON or String response bodies");
        }
        String device;
        AesCrypto crypto;
        try {
            device = cryptoManager.resolveDevice(deviceTypeResolver.resolve(request.getHeaders()));
            crypto = cryptoManager.getRequired(device);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid crypto device");
        }
        try {
            Object result;
            if (body instanceof CryptoDataResponse dataResponse) {
                String ciphertext = crypto.encryptBase64(objectMapper.writeValueAsBytes(dataResponse.getData()));
                dataResponse.onCryptAfter(ciphertext);
                result = body;
            } else {
                String ciphertext = crypto.encryptBase64(objectMapper.writeValueAsBytes(body));
                result = stringResponse ? objectMapper.writeValueAsString(ciphertext) : ciphertext;
            }
            HttpHeaders headers = response.getHeaders();
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(CRYPTO_HEADER, crypto.algorithm());
            headers.set(deviceHeader, device);
            return result;
        } catch (JsonProcessingException error) {
            throw new CryptoException("Cannot encrypt response", error);
        }
    }

    private boolean isJson(MediaType mediaType) {
        if (mediaType == null || MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return subtype != null && subtype.endsWith("+json");
    }
}

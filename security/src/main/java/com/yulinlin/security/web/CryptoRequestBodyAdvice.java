package com.yulinlin.security.web;

import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.AesCrypto;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@ControllerAdvice
public final class CryptoRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final DeviceCryptoManager cryptoManager;
    private final DeviceTypeResolver deviceTypeResolver;
    private final long maxRequestBytes;

    public CryptoRequestBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties) {
        this.cryptoManager = cryptoManager;
        this.deviceTypeResolver = deviceTypeResolver;
        if (properties.getMaxRequestSize() == null || properties.getMaxRequestSize().toBytes() < 0) {
            throw new IllegalArgumentException("yulinlin.security.crypto.max-request-size must not be negative");
        }
        this.maxRequestBytes = properties.getMaxRequestSize().toBytes();
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        ApiCrypto annotation = CryptoAnnotations.find(methodParameter);
        return annotation != null;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] requestBody = StreamUtils.copyToByteArray(inputMessage.getBody());
        checkSize(requestBody.length);
        ApiCrypto annotation = CryptoAnnotations.find(parameter);
        boolean encryptionRequired = annotation != null && annotation.value();

        String content = new String(requestBody, StandardCharsets.UTF_8).trim();
        boolean json = content.startsWith("{") || content.startsWith("[");
        if (json) {
            if (encryptionRequired) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted request is required");
            }
            return bodyMessage(inputMessage.getHeaders(), requestBody, false);
        }

        try {
            String device = deviceTypeResolver.resolve(inputMessage.getHeaders());
            AesCrypto crypto = cryptoManager.getRequired(device);
            byte[] plaintext = crypto.decryptBase64(content);
            checkSize(plaintext.length);
            return bodyMessage(inputMessage.getHeaders(), plaintext, true);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw invalidEncryptedRequest();
        }
    }

    private ResponseStatusException invalidEncryptedRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid encrypted request");
    }

    private void checkSize(int size) {
        if (size > maxRequestBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Encrypted request is too large");
        }
    }

    private HttpInputMessage bodyMessage(HttpHeaders originalHeaders, byte[] body, boolean decrypted) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        if (decrypted) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        headers.setContentLength(body.length);
        return new HttpInputMessage() {
            @Override
            public ByteArrayInputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}

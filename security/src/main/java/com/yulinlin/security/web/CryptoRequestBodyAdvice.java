package com.yulinlin.security.web;

import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.crypto.AesCrypto;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.crypto.CryptoConfiguredCondition;
import org.springframework.context.annotation.Conditional;
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
@Conditional(CryptoConfiguredCondition.class)
public final class CryptoRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final DeviceCryptoManager cryptoManager;
    private final DeviceTypeResolver deviceTypeResolver;
    private final CryptoPathExclusions pathExclusions;
    private final boolean configured;
    private final boolean enabled;
    private final long maxRequestBytes;

    public CryptoRequestBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties) {
        this(cryptoManager, deviceTypeResolver, properties,
                properties.hasDeviceKeys() ? new CryptoPathExclusions(properties) : null);
    }

    public CryptoRequestBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties,
            CryptoPathExclusions pathExclusions) {
        this.cryptoManager = cryptoManager;
        this.deviceTypeResolver = deviceTypeResolver;
        this.pathExclusions = pathExclusions;
        this.configured = properties.hasDeviceKeys();
        this.enabled = properties.isEnabled();
        if (configured && (properties.getMaxRequestSize() == null || properties.getMaxRequestSize().toBytes() < 0)) {
            throw new IllegalArgumentException("yulinlin.security.crypto.max-request-size must not be negative");
        }
        this.maxRequestBytes = configured ? properties.getMaxRequestSize().toBytes() : 0;
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (!configured) return false;
        ApiCrypto annotation = CryptoAnnotations.find(methodParameter);
        return annotation != null && !pathExclusions.isCurrentRequestExcluded();
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (!configured || pathExclusions.isExcluded(inputMessage)) return inputMessage;
        byte[] requestBody = StreamUtils.copyToByteArray(inputMessage.getBody());
        checkSize(requestBody.length);
        ApiCrypto annotation = CryptoAnnotations.find(parameter);
        boolean encryptionRequired = enabled && annotation != null && annotation.value();

        String content = new String(requestBody, StandardCharsets.UTF_8);
        String inspectedContent = content.strip();
        boolean json = inspectedContent.startsWith("{") || inspectedContent.startsWith("[");
        if (json) {
            if (encryptionRequired) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted request is required");
            }
            return bodyMessage(inputMessage.getHeaders(), requestBody, false);
        }

        try {
            String ciphertext = normalizeCiphertext(content);
            String device = deviceTypeResolver.resolve(inputMessage.getHeaders());
            AesCrypto crypto = cryptoManager.getRequired(device);
            byte[] plaintext = crypto.decryptBase64(ciphertext);
            checkSize(plaintext.length);
            return bodyMessage(inputMessage.getHeaders(), plaintext, true);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw invalidEncryptedRequest(error);
        }
    }

    /**
     * Accepts raw Base64, Axios JSON-string Base64, and form-decoded Base64 where '+' became a space.
     * Only the transport-specific ASCII space is repaired; every other invalid character is rejected
     * later by the strict Base64 decoder.
     */
    private String normalizeCiphertext(String content) {
        String trimmed = content.strip();
        String ciphertext = content;
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"'
                && trimmed.charAt(trimmed.length() - 1) == '"') {
            ciphertext = trimmed.substring(1, trimmed.length() - 1);
            // Standard Base64 never needs JSON escaping. Reject other JSON strings instead of guessing.
            if (ciphertext.indexOf('\\') >= 0 || ciphertext.indexOf('"') >= 0) {
                throw new IllegalArgumentException("Encrypted JSON string contains escapes");
            }
        }
        // application/x-www-form-urlencoded decoders commonly turn Base64 '+' into an ASCII space.
        return ciphertext
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace(' ', '+');
    }

    private ResponseStatusException invalidEncryptedRequest(Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid encrypted request", cause);
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

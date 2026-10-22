package com.yulinlin.security.web;

import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.Objects;

@ControllerAdvice
@ConditionalOnBean(CryptoCodec.class)
@ConditionalOnProperty(prefix = "yulinlin.security.crypto", name = "advice-enabled",
        havingValue = "true", matchIfMissing = true)
public final class CryptoRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final CryptoCodec cryptoCodec;
    private final DeviceTypeResolver deviceTypeResolver;
    private final CryptoPathExclusions pathExclusions;
    private final boolean configured;
    private final boolean enabled;
    private final long maxRequestBytes;

    public CryptoRequestBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties) {
        this(cryptoCodec, deviceTypeResolver, properties,
                cryptoCodec != null ? new CryptoPathExclusions(properties) : null);
    }

    public CryptoRequestBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties,
            CryptoPathExclusions pathExclusions) {
        this.cryptoCodec = cryptoCodec;
        this.deviceTypeResolver = deviceTypeResolver;
        this.pathExclusions = pathExclusions;
        this.configured = cryptoCodec != null;
        if (configured) {
            Objects.requireNonNull(deviceTypeResolver, "deviceTypeResolver");
            Objects.requireNonNull(pathExclusions, "pathExclusions");
        }
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
        // 兼容模式也必须读取密文，无注解并不表示跳过解密。
        return configured && !pathExclusions.isCurrentRequestExcluded();
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
        boolean encryptionRequired = CryptoAnnotations.encryptionRequired(parameter, enabled);

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
            String device = HeaderDeviceTypeResolver.normalize(deviceTypeResolver.resolve(inputMessage.getHeaders()));
            CryptoContext context = new CryptoContext(device, inputMessage.getHeaders());
            String plaintext = Objects.requireNonNull(cryptoCodec.decrypt(context, content),
                    "CryptoCodec.decrypt 不能返回 null");
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            checkSize(plaintextBytes.length);
            return bodyMessage(inputMessage.getHeaders(), plaintextBytes, true);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw invalidEncryptedRequest(error);
        }
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

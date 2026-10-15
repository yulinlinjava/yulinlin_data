package com.yulinlin.security.crypto;

import com.yulinlin.security.SecurityCryptoProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Immutable device-to-key registry created once during application startup. */
public final class DeviceCryptoManager {

    private static final Pattern DEVICE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String UNKNOWN_DEVICE = "unknown";

    private final Map<String, AesCrypto> devices;

    public DeviceCryptoManager(SecurityCryptoProperties properties) {
        Map<String, SecurityCryptoProperties.Device> configured = properties.getDevices();
        if (configured == null || configured.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configure yulinlin.security.crypto.devices including the reserved unknown device");
        }

        Map<String, AesCrypto> registered = new LinkedHashMap<>();
        for (Map.Entry<String, SecurityCryptoProperties.Device> entry : configured.entrySet()) {
            String device = normalizeRequired(entry.getKey(), "device name");
            SecurityCryptoProperties.Device configuration = entry.getValue();
            if (configuration == null) {
                throw new IllegalArgumentException("Missing encryption configuration for device: " + device);
            }
            if (registered.containsKey(device)) {
                throw new IllegalArgumentException("Duplicate normalized device name: " + device);
            }
            registered.put(device, createCrypto(
                    configuration.getAlgorithm(),
                    configuration.getKey(),
                    configuration.getKeyEncoding(),
                    configuration.getIv(),
                    configuration.getIvEncoding(),
                    device));
        }
        if (!registered.containsKey(UNKNOWN_DEVICE)) {
            throw new IllegalArgumentException(
                    "Fallback crypto device is not configured: yulinlin.security.crypto.devices.unknown");
        }
        this.devices = Map.copyOf(registered);
    }

    public String resolveDevice(String device) {
        if (device == null || device.isBlank()) return UNKNOWN_DEVICE;
        String resolved = device.trim().toLowerCase(Locale.ROOT);
        if (!DEVICE_NAME.matcher(resolved).matches()) return UNKNOWN_DEVICE;
        if (devices.containsKey(resolved)) return resolved;
        return UNKNOWN_DEVICE;
    }

    public AesCrypto getRequired(String device) {
        return devices.get(resolveDevice(device));
    }

    private static AesCrypto createCrypto(
            String algorithm,
            String key,
            SecurityCryptoProperties.Encoding keyEncoding,
            String iv,
            SecurityCryptoProperties.Encoding ivEncoding,
            String device) {
        try {
            byte[] keyBytes = decodeRequired(
                    key, keyEncoding, "encryption key for device " + device);
            if (algorithm == null || algorithm.isBlank()) {
                throw new IllegalArgumentException("algorithm must not be blank");
            }
            if (AesGcmCrypto.ALGORITHM.equalsIgnoreCase(algorithm)
                    || "AES/GCM/NoPadding".equalsIgnoreCase(algorithm)) {
                if (iv != null && !iv.isBlank()) {
                    throw new IllegalArgumentException("AES-GCM does not accept a configured IV");
                }
                return new AesGcmCrypto(keyBytes, device);
            }
            if (AesCbcCrypto.ALGORITHM.equalsIgnoreCase(algorithm)) {
                byte[] configuredIv = decodeOptional(iv, ivEncoding, "IV for device " + device);
                if (configuredIv == null) {
                    throw new IllegalArgumentException("AES-CBC requires an IV");
                }
                return new AesCbcCrypto(keyBytes, configuredIv);
            }
            throw new IllegalArgumentException("Unsupported AES algorithm: " + algorithm);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid crypto configuration for device: " + device, error);
        }
    }

    private static byte[] decodeOptional(
            String value,
            SecurityCryptoProperties.Encoding encoding,
            String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return decodeRequired(value, encoding, name);
    }

    private static byte[] decodeRequired(
            String value,
            SecurityCryptoProperties.Encoding encoding,
            String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (encoding == null) {
            throw new IllegalArgumentException(name + " encoding must not be null");
        }
        try {
            return switch (encoding) {
                case BASE64 -> Base64.getDecoder().decode(value.trim());
                case UTF8 -> value.getBytes(StandardCharsets.UTF_8);
            };
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(name + " is not valid " + encoding + " data", error);
        }
    }

    private static String normalizeRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!DEVICE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    name + " must match " + DEVICE_NAME.pattern() + ": " + value);
        }
        return normalized;
    }
}

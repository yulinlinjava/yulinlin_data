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

    private final Map<String, AesCrypto> devices;
    private final String defaultDevice;

    public DeviceCryptoManager(SecurityCryptoProperties properties) {
        this.defaultDevice = normalizeRequired(properties.getDefaultDevice(), "default-device");
        Map<String, SecurityCryptoProperties.Device> configured = properties.getDevices();
        boolean hasDevices = configured != null && !configured.isEmpty();
        boolean hasLegacyKey = properties.getKey() != null && !properties.getKey().isBlank();
        if (hasDevices && hasLegacyKey) {
            throw new IllegalArgumentException(
                    "Configure either yulinlin.security.crypto.key or yulinlin.security.crypto.devices, not both");
        }

        Map<String, AesCrypto> registered = new LinkedHashMap<>();
        if (hasDevices) {
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
        } else {
            if (!hasLegacyKey) {
                throw new IllegalArgumentException(
                        "Configure yulinlin.security.crypto.devices or yulinlin.security.crypto.key");
            }
            registered.put(defaultDevice, createCrypto(
                    properties.getAlgorithm(),
                    properties.getKey(),
                    properties.getKeyEncoding(),
                    properties.getIv(),
                    properties.getIvEncoding(),
                    defaultDevice));
        }
        if (!registered.containsKey(defaultDevice)) {
            throw new IllegalArgumentException("Default crypto device is not configured: " + defaultDevice);
        }
        this.devices = Map.copyOf(registered);
    }

    public String resolveDevice(String device) {
        String resolved = device == null || device.isBlank()
                ? defaultDevice
                : normalizeRequired(device, "device header");
        if (!devices.containsKey(resolved)) {
            throw new IllegalArgumentException("Unknown crypto device: " + resolved);
        }
        return resolved;
    }

    public AesCrypto getRequired(String device) {
        return devices.get(resolveDevice(device));
    }

    public String getDefaultDevice() {
        return defaultDevice;
    }

    public AesCrypto getDefaultCrypto() {
        return devices.get(defaultDevice);
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

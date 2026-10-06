package com.yulinlin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("yulinlin.security.crypto")
public class SecurityCryptoProperties {

    /** Single-device algorithm. Ignored when devices are configured. */
    private String algorithm = "AES/GCM/NoPadding";

    /** Single-device key encoding. Ignored when devices are configured. */
    private Encoding keyEncoding = Encoding.BASE64;

    /** Single-device CBC IV. Ignored when devices are configured. */
    private String iv;

    /** Single-device CBC IV encoding. Ignored when devices are configured. */
    private Encoding ivEncoding = Encoding.UTF8;

    /** Optional single-device key, decoded according to keyEncoding. */
    private String key;

    /** Request header used to select the device key. */
    private String deviceHeader = "X-Device-Type";

    /** Device used when the request header is absent. */
    private String defaultDevice = "web";

    /** Independent encryption configuration for every device type. */
    private Map<String, Device> devices = new LinkedHashMap<>();

    /** Maximum encrypted or decrypted request body size. */
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Encoding getKeyEncoding() {
        return keyEncoding;
    }

    public void setKeyEncoding(Encoding keyEncoding) {
        this.keyEncoding = keyEncoding;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public Encoding getIvEncoding() {
        return ivEncoding;
    }

    public void setIvEncoding(Encoding ivEncoding) {
        this.ivEncoding = ivEncoding;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDeviceHeader() {
        return deviceHeader;
    }

    public void setDeviceHeader(String deviceHeader) {
        this.deviceHeader = deviceHeader;
    }

    public String getDefaultDevice() {
        return defaultDevice;
    }

    public void setDefaultDevice(String defaultDevice) {
        this.defaultDevice = defaultDevice;
    }

    public Map<String, Device> getDevices() {
        return devices;
    }

    public void setDevices(Map<String, Device> devices) {
        this.devices = devices;
    }

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public enum Encoding {
        BASE64,
        UTF8
    }

    public static class Device {

        private String algorithm = "AES/GCM/NoPadding";
        private Encoding keyEncoding = Encoding.BASE64;
        private String key;
        private String iv;
        private Encoding ivEncoding = Encoding.UTF8;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public Encoding getKeyEncoding() {
            return keyEncoding;
        }

        public void setKeyEncoding(Encoding keyEncoding) {
            this.keyEncoding = keyEncoding;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getIv() {
            return iv;
        }

        public void setIv(String iv) {
            this.iv = iv;
        }

        public Encoding getIvEncoding() {
            return ivEncoding;
        }

        public void setIvEncoding(Encoding ivEncoding) {
            this.ivEncoding = ivEncoding;
        }
    }

}

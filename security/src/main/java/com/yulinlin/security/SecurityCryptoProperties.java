package com.yulinlin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("yulinlin.security.crypto")
public class SecurityCryptoProperties {

    /** 是否注册 MVC 请求解密和响应加密 Advice；关闭不影响 CryptoCodec 的注册与手动调用。 */
    private boolean adviceEnabled = true;

    /** Advice 启用且有 CryptoCodec 时全局要求密文请求并加密响应；false 时仍兼容解密。 */
    private boolean enabled = true;

    /** Request header used to select the device key. */
    private String deviceHeader = "Device-Type";

    /** Independent encryption configuration for every device type. */
    private Map<String, Device> devices = new LinkedHashMap<>();

    /** Maximum encrypted or decrypted request body size. */
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    /** Server-controlled paths that must bypass both request decryption and response encryption. */
    private List<String> excludePaths = new ArrayList<>(List.of("/monitor/**"));

    /** 是否配置默认 AES 密钥；自定义 CryptoCodec 不依赖此配置。 */
    public boolean hasDeviceKeys() {
        return devices != null && devices.values().stream()
                .anyMatch(device -> device != null && device.getKey() != null && !device.getKey().isBlank());
    }

    public boolean isAdviceEnabled() {
        return adviceEnabled;
    }

    public void setAdviceEnabled(boolean adviceEnabled) {
        this.adviceEnabled = adviceEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDeviceHeader() {
        return deviceHeader;
    }

    public void setDeviceHeader(String deviceHeader) {
        this.deviceHeader = deviceHeader;
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

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }

    public enum Encoding {
        BASE64,
        UTF8
    }

    public static class Device {

        private String algorithm = "AES/CBC/PKCS5Padding";
        private Encoding keyEncoding = Encoding.UTF8;
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

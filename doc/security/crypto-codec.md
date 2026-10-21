# 自定义接口加解密

[文档首页](../README.md) / [接口安全](README.md)

提供一个 CryptoCodec Bean，即可替换接口加密和解密，无需修改 Controller，也无需配置框架的 AES 密钥。

## 公开接口

```java
package com.yulinlin.security.crypto;

public interface CryptoCodec {
    String encrypt(String deviceType, byte[] plaintext);
    byte[] decrypt(String deviceType, String ciphertext);
}
```

encrypt 返回文本密文，decrypt 返回明文字节。对象由框架先转 JSON，字符串直接转 UTF-8；R 响应只处理 data，其他字段保持不变。Codec 不负责 JSON 序列化、登录、权限或响应包装。

## 注册业务实现

放在业务配置类中，下面 BusinessCryptoService 是业务自己的加解密服务，不是框架类：

```java
import com.yulinlin.security.crypto.CryptoCodec;
import org.springframework.context.annotation.Bean;

@Bean
public CryptoCodec cryptoCodec(BusinessCryptoService service) {
    return new CryptoCodec() {
        @Override
        public String encrypt(String deviceType, byte[] plaintext) {
            return service.encrypt(deviceType, plaintext);
        }

        @Override
        public byte[] decrypt(String deviceType, String ciphertext) {
            return service.decrypt(deviceType, ciphertext);
        }
    };
}
```

业务服务负责安全算法、密钥管理和密文格式，不能把 Base64 编码当作加密。实现必须线程安全，失败抛 CryptoException，不返回原始明文兜底。

| 场景 | 选择 |
| --- | --- |
| 有自定义 CryptoCodec Bean | 使用自定义实现，不要求配置 devices、key 或 iv |
| 无自定义 Bean，有设备密钥 | 使用默认多设备 AES |
| 无自定义 Bean，也无设备密钥 | 请求和响应均不做加解密 |

通常只提供一个 CryptoCodec Bean；多个时必须用 @Primary 明确选择。设备密钥配置不会将自定义实现与默认实现叠加使用。

## 保留的配置与行为

```yaml
yulinlin:
  security:
    crypto:
      enabled: true
      device-header: Device-Type
      exclude-paths:
        - /monitor/**
      max-request-size: 1MB
```

自定义实现仍使用 @ApiCrypto。enabled=false 或 @ApiCrypto(false) 表示请求兼容明文/密文、响应明文，不是禁用解密；排除路径则请求和响应都不处理。没有注解的接口不参与。

deviceType 已去除首尾空白并转换为小写，只接受 1～32 位字母、数字、下划线或短横线。请求头缺失、空白或格式非法时传入 unknown；其他合法设备名原样传给自定义实现，不根据 YAML 设备列表过滤。业务决定未知设备使用哪组密钥或拒绝请求，设备类型不是身份凭证。

## 密文协议约定

- 本接口支持 UTF-8 文本密文，不接收二进制报文或 JSON 密文对象。
- 兼容模式把去除首尾空白后以 `{`、`[` 开头的正文当作明文 JSON；严格模式拒绝此类明文。其他正文才交给 decrypt，因此 encrypt 不能返回以 `{`、`[` 开头的内容。
- decrypt 接收到的是原始正文字符串，包括空白、换行、引号和加号；框架不会提前改成 Base64、去引号或将空格替换为加号。这些兼容规则由自己的协议决定。
- encrypt 必须返回非空且非纯空白的密文；decrypt 不得返回 null。解密失败返回 HTTP 400，不再降级成明文；加密失败不返回未加密数据。
- 收到的正文和解密结果都受 max-request-size 限制；自定义实现还应避免无界分配内存。
- 响应仍使用 JSON 字符串，R.data 为密文时 crypt=true。客户端按接口原始类型解密为字符串或 JSON。
- 自定义实现的响应回写规范化的设备名，不自动发送 X-Yulinlin-Crypto 算法头；该头只用于默认 AES 协议。自定义客户端按双方约定识别密文，不依赖 AES 专属响应头。

请求和响应使用同一个 Codec。只改变一个方向的格式时，也必须保证另一个方向与客户端协议兼容。

## 手动调用

注入 CryptoCodec 即可，与接口自动加解密使用相同的 Bean：

```java
byte[] plaintext = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
String encrypted = cryptoCodec.encrypt("web", plaintext);
String decrypted = new String(cryptoCodec.decrypt("web", encrypted),
        java.nio.charset.StandardCharsets.UTF_8);
```

手动调用时由调用方提供规范设备名、控制大小并处理异常；不会自动应用 @ApiCrypto、路径排除、JSON 序列化或响应包装。

---

下一步：[默认加密配置与前端对接](crypto.md)。

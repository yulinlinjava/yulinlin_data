package com.yulinlin.security.crypto;

/**
 * 接口文本加解密公开协议。业务提供此类型的 Bean 即可替换默认 AES 实现，无需配置框架设备密钥。
 * 实现必须线程安全；设备名已规范化为小写，缺失或格式非法时为 unknown。
 * 自定义实现自行选择密钥并处理未知设备，不依据框架 AES 配置筛选设备。
 */
public interface CryptoCodec {

    /**
     * 明文字节转为非空文本密文，不得以 { 或 [ 开头。
     * 对象的 JSON 序列化、字符串的 UTF-8 转换及响应包装由框架负责。
     * 返回值应与 decrypt 使用同一协议；失败抛 CryptoException，不返回明文兜底。
     */
    String encrypt(String deviceType, byte[] plaintext);

    /**
     * 收到的原始 UTF-8 密文正文转为明文字节，不得返回 null。
     * 正文未预先进行 Base64 清洗、去引号或空白改写，由实现决定是否兼容这些传输形式。
     * 失败抛 CryptoException；框架拒绝请求，不降级为明文。
     */
    byte[] decrypt(String deviceType, String ciphertext);
}

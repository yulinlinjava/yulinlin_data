package com.yulinlin.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖接口的全局加密默认行为。无注解接口同样参与，方法配置优先于类配置。
 * 排除路径始终跳过请求解密和响应加密。
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiCrypto {

    /**
     * {@code true}：与无注解的全局默认行为相同，enabled=true 时要求请求加密、响应加密。
     * {@code false}：允许明文 JSON，密文请求仍解密，响应保持明文。
     * 全局 enabled=false 时，无论注解取值或是否有注解，均兼容明密文请求、返回明文。
     * 没有自定义 CryptoCodec 且未配置设备密钥时，框架完全不处理加解密。
     */
    boolean value() default true;
}

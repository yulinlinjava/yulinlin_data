package com.yulinlin.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables transparent JSON request decryption and response encryption.
 * Method-level settings override class-level settings.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiCrypto {

    /**
     * {@code true}: the request must be encrypted and the response is encrypted.
     * {@code false}: the request may be plaintext, but an encrypted request is
     * still decrypted; the response remains plaintext.
     */
    boolean value() default true;
}

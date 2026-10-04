package com.yulinlin.repository;

import com.yulinlin.repository.BeanDefinitionRegister;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import({BeanDefinitionRegister.class,RepositoryAutoConfig.class})
public @interface JoinRepositoryScan {


    String[] value() default {} ;


}

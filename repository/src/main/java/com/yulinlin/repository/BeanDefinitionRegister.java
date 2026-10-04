package com.yulinlin.repository;


import com.yulinlin.repository.proxy.MethodParseManager;
import com.yulinlin.repository.session.RepositorySession;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;


public class BeanDefinitionRegister implements ImportBeanDefinitionRegistrar {



    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        String[] stringArray;




        AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(JoinRepositoryScan.class.getName()));

        stringArray = annoAttrs.getStringArray("value");

       if(stringArray.length == 0) {
           String packName =  ClassUtils.getPackageName(importingClassMetadata.getClassName());
           stringArray = new String[]{packName};
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RepositoryScannerConfigurer.class);
        builder.addConstructorArgValue(stringArray);
        registry.registerBeanDefinition("repositoryScannerConfigurer",builder.getBeanDefinition());

    }



}
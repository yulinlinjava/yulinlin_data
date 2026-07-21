package com.yulinlin.repository;

import com.yulinlin.data.lang.util.ScanUtils;
import com.yulinlin.repository.anno.JoinRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Set;

public class RepositoryScannerConfigurer implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware, BeanNameAware {

    private String[] basePackages;

    public RepositoryScannerConfigurer(String[] basePackages) {
        this.basePackages = basePackages;
    }


    @Override
    public void setBeanName(String s) {

    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {

        for (String basePackage : basePackages) {
            Set<Class<?>> classes =  ScanUtils.scanClass(basePackage,true);
            for (Class<?> aClass : classes) {
                if(aClass.isAnnotationPresent(JoinRepository.class)){
                    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RepositoryFactory.class);
                    builder.addConstructorArgValue(aClass);

                    beanDefinitionRegistry.registerBeanDefinition(aClass.getSimpleName(),builder.getBeanDefinition());

                }
            }
        }


    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }
}

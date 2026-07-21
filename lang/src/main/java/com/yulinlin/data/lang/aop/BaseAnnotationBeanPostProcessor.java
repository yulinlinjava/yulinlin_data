package com.yulinlin.data.lang.aop;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

//在配置类或文件中创建该Bean
  public abstract class BaseAnnotationBeanPostProcessor extends AbstractAdvisingBeanPostProcessor implements MethodInterceptor {

    public BaseAnnotationBeanPostProcessor() {
        setBeforeExistingAdvisors(true);
        this.advisor = new AnnotationAdvisor(this.getAnnotation(),this.getMethodInterceptor());
    }

    public abstract Class<? extends Annotation> getAnnotation() ;

    protected  MethodInterceptor getMethodInterceptor(){
        return this;
    }



    public  static    <E extends Annotation> E getInvocationAnnotation(MethodInvocation invocation, Class<E> anno){
        //获取目标类
        Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
        //获取指定方法
        Method specificMethod = ClassUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        //获取真正执行的方法,可能存在桥接方法
        final Method userDeclaredMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);

        //获取方法上注解
        E async = AnnotatedElementUtils.findMergedAnnotation(userDeclaredMethod, anno);
        if (async == null) {
            async = AnnotatedElementUtils.findMergedAnnotation(targetClass, anno);
        }

        return async;

    }



    public static   class AnnotationAdvisor extends AbstractPointcutAdvisor {
        private Advice advice;
        private Pointcut pointcut;

        //传入你自定义注解和MethodInterceptor实现
        public AnnotationAdvisor(Class<? extends Annotation> annotationType, MethodInterceptor interceptor){
            this.advice = interceptor;
            this.pointcut = buildPointcut(annotationType);
        }

        @Override
        public Pointcut getPointcut() {

            return this.pointcut;
        }

        @Override
        public Advice getAdvice() {

            return this.advice;
        }

        private static Pointcut buildPointcut(Class<? extends Annotation> annotationType) {

            ComposablePointcut result = null;
            //类级别
            Pointcut cpc = new AnnotationMatchingPointcut(annotationType, true);
            //方法级别
            Pointcut mpc = AnnotationMatchingPointcut.forMethodAnnotation(annotationType);
            //对于类和方法上都可以添加注解的情况
            //类上的注解,最终会将注解绑定到每个方法上
            if (result == null) {
                result = new ComposablePointcut(cpc);
            }
            return result.union(mpc);
        }
    }

}
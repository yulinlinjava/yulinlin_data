package com.yulinlin.mcp.tool;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Discovers managed methods after all normal singletons and their AOP proxies are ready.
 * 方法参数：至多一个请求 DTO，另可声明一个 {@link McpCallContext} 参数（读取调用方和令牌属性）。
 */
public final class AnnotatedMcpTools implements SmartInitializingSingleton {
    private final ConfigurableListableBeanFactory beans;
    private final ObjectMapper json;
    private final Validator validator;
    private final McpToolRegistry registry;

    public AnnotatedMcpTools(ConfigurableListableBeanFactory beans,ObjectMapper json,Validator validator,McpToolRegistry registry) {
        this.beans=beans;this.json=json;this.validator=validator;this.registry=registry;
    }
    @Override public void afterSingletonsInstantiated() {
        for(String name:beans.getBeanNamesForType(Object.class,false,false)) {
            if(!beans.isSingleton(name))continue;
            Object bean=beans.getBean(name);
            Class<?> target=AopProxyUtils.ultimateTargetClass(bean);
            var methods=MethodIntrospector.selectMethods(target,(MethodIntrospector.MetadataLookup<McpTool>)
                method->AnnotatedElementUtils.findMergedAnnotation(method,McpTool.class));
            methods.forEach((method,annotation)->register(bean,target,method,annotation));
        }
    }
    private void register(Object bean,Class<?> target,Method method,McpTool annotation) {
        int contextIndex=-1,inputIndex=-1;
        for(int i=0;i<method.getParameterCount();i++){
            if(method.getParameterTypes()[i]==McpCallContext.class){if(contextIndex>=0)inputIndex=-2;contextIndex=i;}
            else{if(inputIndex!=-1)inputIndex=-2;else inputIndex=i;}
        }
        if(!Modifier.isPublic(method.getModifiers())||Modifier.isStatic(method.getModifiers())||method.getParameterCount()>2||inputIndex==-2)
            throw new IllegalArgumentException("@McpTool requires a public instance method with at most one DTO and one McpCallContext: "+method);
        if(annotation.description().isBlank())throw new IllegalArgumentException("@McpTool description is required: "+method);
        Method callable=AopUtils.selectInvocableMethod(method,bean.getClass());
        org.springframework.util.ReflectionUtils.makeAccessible(callable);
        final int dtoIndex=inputIndex,ctxIndex=contextIndex,count=method.getParameterCount();
        JavaType input=dtoIndex<0?null:type(ResolvableType.forMethodParameter(method,dtoIndex,target));
        JavaType output=type(ResolvableType.forMethodReturnType(method,target));
        var inputSchema=new JavaToolSchemas(json).input(input);
        var outputSchema=new JavaToolSchemas(json).output(output);
        registry.register(new McpToolDefinition(annotation.name(),annotation.description(),inputSchema,outputSchema,annotation.readOnly(),(context,args)->{
            Object[] values=new Object[count];
            if(ctxIndex>=0)values[ctxIndex]=context;
            if(dtoIndex>=0){
                try { values[dtoIndex]=json.readerFor(input).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(args); }
                catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("工具参数无法转换为请求对象");}
                if(!validator.validate(values[dtoIndex]).isEmpty())throw new IllegalArgumentException("工具参数未通过 Bean Validation 校验");
            }
            if(!validator.forExecutables().validateParameters(bean,callable,values).isEmpty())throw new IllegalArgumentException("工具方法参数校验失败");
            Object value;
            try { value=callable.invoke(bean,values); }
            catch(InvocationTargetException error){if(error.getCause() instanceof Exception e)throw e;if(error.getCause() instanceof Error e)throw e;throw error;}
            if(!validator.forExecutables().validateReturnValue(bean,callable,value).isEmpty())throw new IllegalStateException("工具返回值校验失败，操作可能已完成");
            return value;
        }));
    }
    private JavaType type(ResolvableType resolved) {
        Class<?> raw=resolved.resolve();
        if(raw==null||raw==Object.class||resolved.hasUnresolvableGenerics())throw new IllegalArgumentException("@McpTool requires a concrete Java type: "+resolved);
        if(resolved.isArray())return json.getTypeFactory().constructArrayType(type(resolved.getComponentType()));
        if(resolved.hasGenerics())return json.getTypeFactory().constructParametricType(raw,Arrays.stream(resolved.getGenerics()).map(this::type).toArray(JavaType[]::new));
        return json.constructType(raw);
    }
}

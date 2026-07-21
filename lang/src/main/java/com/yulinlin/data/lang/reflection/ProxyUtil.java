package com.yulinlin.data.lang.reflection;

import lombok.SneakyThrows;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;

public class ProxyUtil {




    public static Object getProxyInstance(Class clazz, Callback... callbacks) {
        //1.工具类
        Enhancer en = new Enhancer();
        //2.设置父类
        en.setSuperclass(clazz);
        //3.设置回调函数
        en.setCallbacks(callbacks);
        Object source = en.create();

        //4.创建子类(代理对象)
        return source;
    }

    @SneakyThrows
    //得到代理类型
    public static Class getProxyClass(Class clazz) {
        String name = clazz.getName();
        int i = name.indexOf("$$");
        if(i == -1){
            return clazz;
        }

        name =  name.substring(0,i);

        return Class.forName(name);

    }


}

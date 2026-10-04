package com.yulinlin.admin;

import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.reflection.SerializableUtil;
import com.yulinlin.data.lang.util.RandomUtil;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class A {

    public static class B implements Serializable {
        private String a="dasd";

        private int b =RandomUtil.randomInt(1000);

        private Date date = new Date();
    }

    public static void runnable(Consumer<Object> runnable) {
        Object data = new B();



        long x = System.currentTimeMillis();

        for(int i = 0;i<10000;i++){
           runnable.accept(data);

        }

        long y = System.currentTimeMillis();

        System.out.println(y-x);
    }


    public static void main(String[] args) {


        Object key="a.b".split("\\.");

        int a=0;

    }
}

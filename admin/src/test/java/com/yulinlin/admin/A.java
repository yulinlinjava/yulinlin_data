package com.yulinlin.admin;

import com.yulinlin.data.core.http.HttpUtil;
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


        String json= """
                {
                  "project": "bb",
                  "token": "yes@)978*53",
                  "userId": "1",
                  "title": "示例视频一",
                  "videoUrl": "https://mmbppbs.nndybj.com/mstorage2/slice_video/20260908/485391/index.m3u8?wsSecret=d9885b65ea2061a07d30fc9cbc87fec2&wsTime=1791971256&ip=115.134.107.110",
                  "picture": "https://uim.9uu22.com/storage/picture/20260816/1120306a812c7e0b5b6.jpg"
                }
                """;

        String bodyAsString = HttpUtil.post("https://miyun.space/api/put")
                .json(JsonUtil.parseJson(json, Map.class))
                .execute().getBodyAsString();

        int a=0;

    }
}

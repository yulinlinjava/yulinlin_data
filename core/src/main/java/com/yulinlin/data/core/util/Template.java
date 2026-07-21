package com.yulinlin.data.core.util;

import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {

    private String sql;

    private static final String pattern0 = "(\\$\\{)(.*?)(})";

    private static final Pattern tagPattern0 = Pattern.compile(pattern0);

    private Template(String sql) {
        this.sql = sql;
    }



    /**
     * $处理
     * @param params
     */
    private String before(  Map params ){
        String sql = this.sql;
        Matcher matcher= tagPattern0.matcher(sql);
        while (matcher.find()) {
            String str = matcher.group().replace(" ","");
           String key = str.substring(2,str.length() - 1);


            Object value =      ReflectionUtil.invokeGetter(params,key);

            if(value != null){
                sql =  sql.replace(str,value.toString());
            }

        }

        return sql;
    }



    private String render(Map<String,Object> map){
        if(sql.contains("$")){
            return this.before(map);
        }
        return sql;

    }


    public static String render(String str,Map<String,Object> map){
        return new Template(str).render(map);
    }



}

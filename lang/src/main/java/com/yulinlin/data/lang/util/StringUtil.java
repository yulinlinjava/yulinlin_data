package com.yulinlin.data.lang.util;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

public class StringUtil implements Serializable {

    private static final int charMargin = 32;
    /**
     * 删除换行符
     * @param str
     * @return
     */
    public static String removeLine(String str){
        int length = str.length();
        StringBuilder content = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char value = str.charAt(i);
            if (value != ' ' && value != '\t' && value != '\r' && value != '\n'
                    && value != '\f' && value != '\u000B') content.append(value);
        }
        return content.length() == length ? str : content.toString();
    }

    public static boolean isNull(String s){
        return s == null || s.length() == 0;
    }
    public static boolean isNotNull(String s){

        return !isNull(s);
    }

  public static boolean isLowerCaseFirstOne(char c) {
      return c >= 'a' && c <= 'z';
  }

        //首字母转小写
    public static char toLowerCaseFirstOne(char c){
        if(c >= 'A' && c <= 'Z'){
            c += charMargin;
        }
        return c;
    }
    //首字母转大写
    public static char toUpperCaseFirstOne(char c){
        if(c >= 'a' && c <= 'z'){
            c -= charMargin;
        }
        return c;
    }
    //首字母转小写
    public static String toLowerCaseFirstOne(String s){
        char[] cs = s.toCharArray();
        cs[0] = toLowerCaseFirstOne(cs[0]);

        return new String(cs);
    }


    //首字母转大写
    public static String toUpperCaseFirstOne(String s){
        char[] cs = s.toCharArray();
        cs[0] = toUpperCaseFirstOne(cs[0]);

        return new String(cs);
    }


    public static String renderString(String tel, Map<String,Object> data){
        for (Map.Entry<String, Object> entry : data.entrySet()) {
          tel =   tel.replace("${"+entry.getKey()+"}",entry.getValue().toString());
        }
        return tel;
    }


    public static String javaToColumn(String columnName){
        char[] cs = columnName.toCharArray();
        StringBuilder sb = new StringBuilder(columnName.length() + 8);
        for(char c:cs){
            if(c >='A' && c<='Z'){
                sb.append('_').append(toLowerCaseFirstOne(c));
            }else{
                sb.append(c);
            }

        }
        return sb.toString();
    }

    public static String columnToJava(String columnName){
        char[] cs = columnName.toCharArray();
        StringBuilder sb = new StringBuilder(columnName.length());
        boolean b = false;
        for(char c:cs){
            if(c == '_'){
                b = true;
                continue;
            }
            if(b){
                c = toUpperCaseFirstOne(c);
                b=false;
            }
            sb.append(c);

        }
        return sb.toString();
    }


    public static String tableToClass(String tableName){
        tableName = columnToJava(tableName);
        return toUpperCaseFirstOne(tableName);
    }

    public static String methodToFieldName(Method method){
        String name  = method.getName();
        if(name.startsWith("get")){
            name=  name.substring(3);
        }else if(name.startsWith("is")){
            name = name.substring(2);
        }

        return toLowerCaseFirstOne(name);
    }

}

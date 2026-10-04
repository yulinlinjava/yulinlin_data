package com.yulinlin.data.lang.util;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil implements Serializable {

    static Pattern p = Pattern.compile("\\s*|\t|\r|\n");
    private static final int charMargin = 32;
    /**
     * 删除换行符
     * @param str
     * @return
     */
    public static String removeLine(String str){
        Matcher m = p.matcher(str);
        String content = m.replaceAll("");
        return content;
    }

    public static boolean isNull(String s){
        return s == null || s.length() == 0;
    }
    public static boolean isNotNull(String s){

        return !isNull(s);
    }

  public static boolean isLowerCaseFirstOne(char c) {
      return c >= 'A' && c <= 'Z';
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
        StringBuffer sb = new StringBuffer();
        for(char c:cs){
            if(c >='A' && c<='Z'){
                sb.append("_"+toLowerCaseFirstOne(c));
            }else{
                sb.append(c);
            }

        }
        return sb.toString();
    }

    public static String columnToJava(String columnName){
        char[] cs = columnName.toCharArray();
        StringBuffer sb = new StringBuffer();
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

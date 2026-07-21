package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.util.StringUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.util.GenericClass;
import com.yulinlin.repository.dao.BaseRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WhereParseUtil {

    private static Pattern pattern;
    private static List<String> ignore = Arrays.asList("","All","List","One","Batch");
    private static List<String> symbols = Arrays.asList("And","Or","Not");
    private static List<Class> baseTypes = Arrays.asList(int.class,Long.class,Object.class);
    private static String regex="";
    static {


        for (ConditionEnum conditionEnum : ConditionEnum.values()) {

            if(regex.length() > 0){
                regex+="|";
            }
            regex+=StringUtil.toUpperCaseFirstOne( conditionEnum.name());
        }
        for (String symbol : symbols) {

            if(regex.length() > 0){
                regex+="|";
            }
            regex+=StringUtil.toUpperCaseFirstOne(symbol);
        }

        pattern= Pattern.compile(regex);
    }

    /**
     * 获取方法返回类型
     * @param method
     * @param obj
     * @return
     */
    public static Class forMethodReturnType(Method method, Object obj) {

        Class returnType =  method.getReturnType();


        Class clazz;
        Class root =  ProxyUtil.getProxyClass(obj.getClass());


        GenericClass genericClass = GenericClass.newInstance(root);


                if (Collection.class.isAssignableFrom(returnType)) {

                    clazz = genericClass.asMethod(method)

                            .asMethodReturnType()
                            .getGeneric(0)
                            .getRawType();
                }else {


                    if(baseTypes.contains(returnType )){
                        Class  asClass =  method.getDeclaringClass();
                        if(asClass == root){
                            asClass = BaseRepository.class;
                        }
                        clazz =  genericClass
                                .as(asClass)
                                .getGeneric(0)
                                .getRawType();
                    }else {
                        clazz = returnType;

                    }




                }

        if(clazz == null){
            throw new RuntimeException("获取类型失败,请使用具体泛型或继承BaseRepository");
        }

        return clazz;

    }
    public static void parseWhere(String whereSql,Object[] args,IConditionWrapper wrapper){

        Matcher matcher =  pattern.matcher(whereSql);
        int start = 0;
        int i = 0;
        while (matcher.find()){
            String field  =  StringUtil.toLowerCaseFirstOne( whereSql.substring(start, matcher.start()));

            String condition =  matcher.group();

            if(symbols.contains(condition)){

                if(condition.equals("And")){
                    wrapper.and();
                }else if(condition.equals("Or")){
                    wrapper.or();
                }else if(condition.equals("Not")){
                    wrapper.not();
                }
                start=matcher.end();
                continue;
            }
            if(ConditionEnum.isNull.name().equals(condition)){
                wrapper.isNull(field);
            }else {
                condition =  StringUtil.toLowerCaseFirstOne(condition);

                wrapper.condition(field,ConditionEnum.valueOf(condition),args[i++]);
            }

        }

        if(!ignore.contains(whereSql)){
            if(i == 0){
                throw new RuntimeException("条件语法异常,至少一个条件匹配,请检查:"+whereSql);
            }
        }


    }

}

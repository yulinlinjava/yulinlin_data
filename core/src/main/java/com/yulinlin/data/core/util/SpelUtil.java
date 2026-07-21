package com.yulinlin.data.core.util;

import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;

public class SpelUtil {

   static ExpressionParser parser = new SpelExpressionParser();


    public static  <E> E parse(Object bean, String expressionStr){


        Expression expression = parser.parseExpression(expressionStr);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if(bean instanceof Map){
            context.setVariables((Map)bean);
        }else {
            context.setRootObject(bean);
        }


        return (E)expression.getValue(context);
    }


    public static void main(String[] args) {
        long x = System.currentTimeMillis();

        for(int i = 0;i<100000;i++){
            HashMap<String, Object> data = new HashMap<>();

            data.put("a","a");
           //Object s =  ReflectionUtil.parse(data,"a");
           Object a  = SpelUtil.parse(data,"#a+'user'");
           int c  = 0;
        }

        long y = System.currentTimeMillis();

        System.out.println(y-x);
    }

}

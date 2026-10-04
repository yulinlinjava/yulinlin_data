package com.yulinlin.elasticsearch.util;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.AbstractCondition;
import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.base.*;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.node.predicate.Predicates;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import lombok.Data;

import java.util.*;

public class JsScriptUtil {
    private static String pre ="params.";
    private static String and =" && ";


    private static String or =" || ";

    private static String buildKey(String name){
        return pre+name;
    }
    private  static Object buildValue(Object value){
        return value;
    }


    private static StringBuffer expression(String name,String oper, Object value){
        StringBuffer sb = new StringBuffer();
        sb.append(buildKey(name));
        sb.append(oper);
        sb.append(buildValue(value));
        return sb;
    }

    @Data
    public static class  Node{
        private String js;
        private Set<String> keys;

        public Node(    ICondition root) {
            this.keys = new HashSet<>();
            this.js = parseJs(root).toString();

        }



        private   StringBuffer parseJs(     ICondition root){
            StringBuffer sb = new StringBuffer();

            if(root instanceof Predicates){
                String symbol ;
                if(root instanceof And){
                    symbol = and;
                }else if(root instanceof Or){
                    symbol = or;
                }else {
                    throw new NoticeException("未知符号");
                }
                Predicates predicates = (Predicates)root;
                for (ICondition condition : predicates.getList()) {
                    if(sb.length() > 0){
                        sb.append(symbol);
                    }
                    StringBuffer val =  parseJs(condition);
                    sb.append(val);
                }
                return sb;
            }else if(root instanceof Between){
                Between between =(Between) root;
                String name = between.getKey();
                Object[] vs  = between.getValue().toArray();
                keys.add(name);
                String sql =  expression(name," >= ",vs[0]) + and+
                        expression(name," < ",vs[1]);
                sb.append(sql);
                return sb;
            }else if(root instanceof Not){
                Not not = (Not)root;
                sb.append(" !");
                sb.append(parseJs(not));
                return sb;
            }
            else if(root instanceof AbstractCondition){
                AbstractCondition condition =(AbstractCondition) root;
                String name = condition.getKey();
                keys.add(name);
                String op = symbol.get(root.getClass());
                return expression(name,op,condition.getValue());
            }if(root instanceof Nil){
                return sb;
            } else {
                throw new NoticeException("不支持解析");
            }

        }

    }



    private static Map<Class,String> symbol = new HashMap<>();

    static {
        symbol.put(Eq.class," == ");
        symbol.put(Ne.class," != ");
        symbol.put(Gt.class," > ");
        symbol.put(Gte.class," >= ");
        symbol.put(Lt.class," < ");
        symbol.put(Lte.class," <= ");
    }





    public static Node parse(IConditionWrapper wrapper){
        ICondition condition = wrapper.getCondition();

        Node node = new Node(condition);

        if(node.js.isEmpty()){
            return null;
        }
        return node;
    }

}

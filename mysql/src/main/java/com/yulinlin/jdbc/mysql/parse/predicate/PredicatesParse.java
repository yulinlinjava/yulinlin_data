package com.yulinlin.jdbc.mysql.parse.predicate;

import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.predicate.Predicates;
import com.yulinlin.data.core.node.base.Nil;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class PredicatesParse<E extends Predicates>  implements IParse<E> {

    public  String getSeparator(){
        return " and ";
    }

    public String getPrefix(){
        return " ( ";
    }
    public String getSuffix(){
        return " ) ";
    }


    private static AtomicInteger atomicInteger = new AtomicInteger();

    @Override
    public String parse(E condition, IParamsContext params, IParseManager parseManager) {
        List<ICondition> list =  condition.getList();
        list =  list.stream().filter(row -> !(row instanceof Nil)).collect(Collectors.toList());

        if(list.isEmpty()){
            return null;
        }
        if(list.size() == 1){
            return (String)parseManager.parse(list.get(0),params);
        }
        String sql="";
        for (ICondition node : list) {
            atomicInteger.addAndGet(1);

            try {
                String val = (String)  parseManager.parse(node,params);

                if(val != null){
                    if(sql.length() > 0){
                        sql+=getSeparator();
                    }
                    sql  +=val;
                }
            }finally {
                atomicInteger.addAndGet(-1);
            }

        }
        if(sql.length() == 0){
            return null;
        }

        if(atomicInteger.get() > 0){
            return getPrefix()+ sql +getSuffix();
        }else {
            return  sql;
        }


    }
}

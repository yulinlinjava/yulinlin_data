package com.yulinlin.jdbc.mysql.parse;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.enums.SqlKeys;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Stack;

public class AliasUtil {

    private static ThreadLocal<Stack<Boolean>> local = ThreadLocal.withInitial(() -> new Stack<>());


    public static void push(boolean open){
        local.get().push(open);
    }

    public static void pop(){
        local.get().pop();
    }
    public static boolean ok(){
        if(local.get().size() == 0){
            return true;
        }
        Boolean aBoolean = local.get().get(0);
        return aBoolean;
    }

    public static String parse(String name, IParamsContext params) {
        if(ok()){
            String val =
                    params.toColumn(
                            name
                    );

            return MysqlJsonUtil.json_where(val);
        }else {
            return name;

        }

    }

    public static boolean supportAlias(IParamsContext params){
        if(params.getRequestType() == RequestType.select ||
                params.getRequestType() == RequestType.count ||
                params.getRequestType() == RequestType.group
                ){
            return true;
        }
        return false;

    }

    public static String parse(AbstractMetaNode node, IParamsContext params) {

        String name = (String) node.get(SqlKeys.name);
        if(name != null){
            return name;
        }


        if(node instanceof MetaNode){
            MetaNode m =(MetaNode) node;
            name  = m.getKey();
            if(ok()){
                String val =
                        params.toColumn(
                                name
                        );
                val=  params.parse(val).toString();

                if(params.getRequestType() == RequestType.select ||
                        params.getRequestType() == RequestType.count ||
                        params.getRequestType() == RequestType.group
                        ){
                    return val;
                }else {
                    String[] split = val.split("\\.");

                    return split[split.length-1];
                }

       /*         if(MysqlJsonUtil.isEmpty()){
                    return val;
                }



                return MysqlJsonUtil.json_where(val);*/
            }else {

                return name;

            }
        }else {
            throw new NoticeException("解析key失败");
        }


    }

}

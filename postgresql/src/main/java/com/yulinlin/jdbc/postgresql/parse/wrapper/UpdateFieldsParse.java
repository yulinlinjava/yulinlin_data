package com.yulinlin.jdbc.postgresql.parse.wrapper;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;
import com.yulinlin.jdbc.postgresql.parse.MysqlJsonUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.StringJoiner;

public class UpdateFieldsParse implements IParse<UpdateFieldsWrapper> {

    private HashMap<String,Object> parseUpdateSet(UpdateFieldsWrapper condition, IParamsContext params){
        String prefix =AliasUtil.parse(condition.getName(), params)+MysqlJsonUtil.symbol;

        HashMap<String, Object> map = new HashMap<>();
        Collection<AtomicValue> list = condition.getList();
        for (AtomicValue atomicValue : list) {
            Object value = params.putGetKey(atomicValue.getValue());


            String key = AliasUtil.parse(atomicValue,params) ;


            map.put(prefix+key,value);
        }


   /*     Collection<MysqlUpdateFieldsWrapper> children =  condition.getChildren();
        for (MysqlUpdateFieldsWrapper child : children) {
            Map<String,Object> data =  parseUpdateSet(child,params);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                map.put(prefix +entry.getKey(),entry.getValue());
            }

        }*/

        return map;
    }

    @Override
    public String parse(UpdateFieldsWrapper condition, IParamsContext params, IParseManager parseManager) {



        Collection<AtomicValue> list = condition.getList();

        StringJoiner sb = new StringJoiner(" , ");

        for (AtomicValue atomicValue : list) {
            Object value = params.putGetKey(atomicValue.getValue());
            String key = AliasUtil.parse(atomicValue,params) ;
            String sql;

            if(atomicValue.getOperate() == AtomicValue.OperateEnum.inc){
                sql = key+" = "+key+" + "+value;
            } else if(atomicValue.getOperate() == AtomicValue.OperateEnum.dec){
                sql = key+" = "+key+" - "+value;
            }
            else {
                sql = key+" = "+value;
            }

            sb.add(sql);

        }

    /*    Collection<MysqlUpdateFieldsWrapper> children =  condition.getChildren();

        for (MysqlUpdateFieldsWrapper child : children) {
            HashMap<String, Object> data = parseUpdateSet(child, params);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                List<String> paths = MysqlJsonUtil.json_path(entry.getKey());
                Object value =entry.getValue();
                String sql =paths.get(0)+" = JSON_SET("+ paths.get(0)+",'"+paths.get(1)+"',"+value+")";

                sb.add(sql);
            }


        }
*/

        return sb.toString();



    }
}

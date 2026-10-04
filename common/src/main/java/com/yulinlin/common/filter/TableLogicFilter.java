package com.yulinlin.common.filter;

import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.data.core.filter.IRequestFilter;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于类注解实现的逻辑删除
 */
public class TableLogicFilter implements IRequestFilter {



    private static class LogicNode{

        private Class table;

        private String logicField;

        private Object deleteValue;

        private Object notDeleteValue;

        public LogicNode(Class table, String logicField, Object deleteValue, Object notDeleteValue) {
            this.table = table;
            this.logicField = logicField;
            this.deleteValue = deleteValue;
            this.notDeleteValue = notDeleteValue;
        }
    }

    private Map<Class,LogicNode> data = new HashMap<>();


    public TableLogicFilter register(Class table,String logicField,Object deleteValue, Object notDeleteValue){
        data.put(table,new TableLogicFilter.LogicNode(table,logicField,deleteValue,notDeleteValue));
        return this;
    }

    @Override
    public ExecuteRequest updateBefore(String session, ExecuteRequest<?> request) {
        if(request.getRequestType() == RequestType.delete){
            LogicNode logicNode = data.get(request.getFromClass());
            if(logicNode != null){
                IDeleteWrapper deleteWrapper = (IDeleteWrapper) request.getWrappers().get(0);
                IConditionWrapper where = (IConditionWrapper)deleteWrapper.where();
                IConditionWrapper<?,?> struct = where.struct();
                ExecuteRequest req = ModelUpdateWrapper.newInstance(request.getFromClass())
                        .field(logicNode.logicField, logicNode.deleteValue)
                        .and(struct)
                     .getRequest();
                return req;
            }
        }
        return request;
    }

    @Override
    public QueryRequest selectBefore(String session, QueryRequest<?> request) {

        LogicNode logicNode = data.get(request.getFromClass());
        if(logicNode != null){
            IConditionWrapper where = null;
            INode wrapper = request.getWrapper();

           if(wrapper instanceof ISelectWrapper){
               ISelectWrapper selectWrapper =(ISelectWrapper) wrapper;
               where = (IConditionWrapper)selectWrapper.where();
           }else if(wrapper instanceof IGroupWrapper){
               IGroupWrapper selectWrapper =(IGroupWrapper)wrapper;
               where = (IConditionWrapper)selectWrapper.where();
           }

            where =  where.struct();
            where.and()
                    .eq(logicNode.logicField,logicNode.notDeleteValue);
        }
        return request;
    }
}

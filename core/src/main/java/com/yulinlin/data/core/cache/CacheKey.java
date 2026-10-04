package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.node.order.OrderNode;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.wrapper.*;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.List;

public class CacheKey {

    ParseType type;

    private int key;
    private boolean singleEquals;


    public static CacheKey of( ParseType type,INode node){
        return new CacheKey( type,node);
    }

    private CacheKey(ParseType type,INode node) {


        IPageWrapper page = null;
        ICondition where= null;
        ICondition having = null;
        Order sort = null;
        if(node instanceof IPageWrapper){
            page = (IPageWrapper)node;
        }
        if(node instanceof IWhereWrapper){
            IWhereWrapper ww = (IWhereWrapper)node;
            IConditionWrapper  wrapper =(IConditionWrapper) ww.where();
            where = wrapper.getCondition();
        }
        if(node instanceof IHavingWrapper){
            IHavingWrapper ww = (IHavingWrapper)node;
            IConditionWrapper  wrapper =(IConditionWrapper) ww.having();
            having = wrapper.getCondition();
        }
        if(node instanceof ISortWrapper){
            sort = (Order)ReflectionUtil.invokeGetter(node,"order");
        }


        if(where != null){
            init(type,where,having,page,sort);
        }


    }


    public void init(ParseType type, ICondition where, ICondition having, IPageWrapper page, Order order){

        String str="";
        if(where != null){
            str = JsonUtil.toJson(where);
        }
        if(having != null){
            str += JsonUtil.toJson(having);
        }

        if(order != null){
            List<OrderNode> list = order.getList();
            if(list.size() > 0){
                str += JsonUtil.toJson(list);
            }
        }
        if(page != null){
            str +=    ReflectionUtil.invokeGetter(page,"pageNumber");
            str +=    ReflectionUtil.invokeGetter(page,"pageSize");
        }


        if(type == ParseType.count){
            str =str+"count";
        }
        key =str.hashCode();

        if(where instanceof And){
            And and = (And)where;
            if(and.getList().size() == 1){
                singleEquals =  and.getList().get(0) instanceof Eq;
            }
        }else {
            singleEquals = false;

        }
    }

    public boolean isSingleEqualsCondition(){
        return singleEquals;
    }

    public int getKey() {
        return key;
    }
}

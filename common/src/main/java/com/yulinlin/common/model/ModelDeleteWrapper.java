package com.yulinlin.common.model;

import com.yulinlin.data.core.model.BaseModelDeleteWrapper;
import com.yulinlin.data.core.model.ModelConditionWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.Collection;

public class ModelDeleteWrapper<E,
        W extends IConditionWrapper<E,W>
        >
        extends BaseModelDeleteWrapper<
        E, W, ModelDeleteWrapper<E,W>
        > {

    public ModelDeleteWrapper(String session, Object obj) {
        super(session, obj);
    }





    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W> newInstance(E obj){
        return  new ModelDeleteWrapper(null,obj);
    }

    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W> newInstance(Class<E> clazz){
        return  new ModelDeleteWrapper(null,clazz);
    }

    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W> newInstance( Collection list){
        return  new ModelDeleteWrapper(null,list);
    }



    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W>  newInstance(String session, E obj){
        return  new ModelDeleteWrapper(session,obj);
    }

    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W>  newInstance(String session, Class<E> clazz){
        return  new ModelDeleteWrapper(session,clazz);
    }

    public static <E,W extends IConditionWrapper<E,W>>  ModelDeleteWrapper<E,W>  newInstance(String session, Collection<E> list){
        return  new ModelDeleteWrapper(session,list);
    }


}

package com.yulinlin.common.model;

import com.yulinlin.data.core.model.BaseModelGroupWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

public class ModelGroupWrapper<E,     W extends IConditionWrapper<E,W>>
        extends BaseModelGroupWrapper<E,W,ModelGroupWrapper<E,W>> {
    public ModelGroupWrapper(String session, Object model) {
        super(session, model);
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> newInstance(E query){
        return new ModelGroupWrapper(null,query);
    }

    public static<E, W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> newInstance(Class<E> clazz){
        return new ModelGroupWrapper(null,clazz);
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> newInstance(Class<E> clazz,Object query){
        ModelGroupWrapper<E,W> objectBaseModelGroupWrapper = newInstance(clazz);
        objectBaseModelGroupWrapper.parseWhereCondition(query);
        return objectBaseModelGroupWrapper;
    }






    public static<E, W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> newInstance(String session, E obj){
        ModelGroupWrapper wrapper = new ModelGroupWrapper(session,obj);
        return wrapper;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelGroupWrapper<E,W> newInstance(String session, Class<E> clazz){
        ModelGroupWrapper wrapper = new ModelGroupWrapper(session,clazz);
        return wrapper;
    }



}

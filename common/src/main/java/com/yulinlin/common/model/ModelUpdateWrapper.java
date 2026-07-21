package com.yulinlin.common.model;

import com.yulinlin.data.core.model.BaseModelUpdateWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.wrapper.IConditionWrapper;

import java.util.Collection;
import java.util.List;

public class ModelUpdateWrapper<E,   W extends IConditionWrapper<E,W>>
        extends BaseModelUpdateWrapper<E,W,ModelUpdateWrapper<E,W>> {

    public ModelUpdateWrapper(String session, Object model) {
        super(session, model);
    }

    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance(E obj){
        ModelUpdateWrapper wrapper = new ModelUpdateWrapper(null,obj);
        return wrapper;
    }


    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance( Class<E> clazz){

        return new ModelUpdateWrapper(null,clazz);
    }


    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance( Collection<E> list){


        return new ModelUpdateWrapper(null,list);
    }







    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance(String session, E obj){
        return new ModelUpdateWrapper(session,obj);
    }

    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance(String session,Collection<E> list){
        return new ModelUpdateWrapper(session,list);
    }



    public static <E,W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> newInstance(String session,Class<E> clazz){


        return new ModelUpdateWrapper(session,clazz);
    }

}

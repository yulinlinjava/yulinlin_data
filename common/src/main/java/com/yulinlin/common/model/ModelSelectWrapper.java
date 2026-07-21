package com.yulinlin.common.model;

import com.yulinlin.common.domain.ITreeNode;
import com.yulinlin.common.util.TreeUtil;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.List;
import java.util.function.Consumer;

public class ModelSelectWrapper<E,        W extends IConditionWrapper<E,W>
        >
        extends BaseModelSelectWrapper<E,
        W,

        ModelSelectWrapper<E,W>


        > {

    public ModelSelectWrapper(String session, Object model) {
        super(session, model);
    }

    public  List<E> selectTree() {
        if(ITreeNode.class.isAssignableFrom( getModelClass())){
            List<ITreeNode> list = (   List<ITreeNode> ) super.selectList();
            return ( List<E>)TreeUtil.buildTree(list);
        }
        throw new RuntimeException("必须实现树接口");

    }
    public  boolean exist() {
        page(1,1);
        return count() > 0;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W> newInstance(E obj){
        ModelSelectWrapper<E,W> wrapper = new ModelSelectWrapper(null,obj);
        return wrapper;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W>newInstance(Class<E> eClass){
        ModelSelectWrapper<E,W> wrapper = new ModelSelectWrapper(null,eClass);
        return wrapper;
    }
    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W>newInstance(Class<E> eClass,Object query){
        E e = ReflectionUtil.newInstance(eClass);
        ModelSelectWrapper wrapper =  newInstance(e);
        wrapper.parseWhereCondition(query);
        return wrapper;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W>newInstance(String session, Object obj){

        ModelSelectWrapper<E,W> wrapper = new ModelSelectWrapper(session,obj);
        return wrapper;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W>newInstance(String session,Class<E> clazz){

        ModelSelectWrapper<E,W> wrapper = new ModelSelectWrapper(session,clazz);
        return wrapper;
    }

    public static <E, W extends IConditionWrapper<E,W>> ModelSelectWrapper<E,W>newInstance(String session,Class<E> clazz,Object query){
        ModelSelectWrapper wrapper =    newInstance(session,clazz);
        wrapper.parseWhereCondition(query);
        return    wrapper;
    }



}

package com.yulinlin.common.model;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.model.PrimaryUtil;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;

/**
 * 基础crud模型
 * @param <E>
 */
public interface  AbstractModel<E extends AbstractModel<E>>  extends AbstractQueryModel<E> , BaseModel {

    //主键
    default   <E>  E primaryKeyValue(){
        return (E)PrimaryUtil.getPrimaryKeyValue(this);
    }
    default    E updateModel(){
        return (E)PrimaryUtil.copyByPrimaryKey(this);
    }


    //删除构造器
    default      <W extends IConditionWrapper<E,W>> ModelDeleteWrapper<E,W> createDeleteWrapper(){

        return ModelDeleteWrapper.newInstance((E)this);
    }

    //更新构造器
    default   <W extends IConditionWrapper<E,W>> ModelUpdateWrapper<E,W> createUpdateWrapper(){

        return ModelUpdateWrapper.newInstance((E)this);
    }

    //插入构造器
    default ModelInsertWrapper<E> createInsertWrapper(){

        return ModelInsertWrapper.newInstance((E)this);
    }

    /**
     * 创建同步代理
     * @return
     */
    default E createSyncProxy(){
        if(!SessionUtil.route().isOpenTransaction()){
            SessionUtil.route().startTransaction();
        }
        return (E) SessionUtil.route().getSyncProxy(this);
    }

    default void commitUpdate(){
        SessionUtil.route().commitTransaction();
    }


}


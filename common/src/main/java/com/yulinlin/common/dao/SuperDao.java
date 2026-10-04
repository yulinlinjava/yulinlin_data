/*
package com.yulinlin.common.dao;

import com.yulinlin.common.model.*;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.util.Arrays;
import java.util.List;

public abstract class SuperDao <E extends AbstractModel<E>> {




  public   List<E> findByIdIn(List<?> ids){
      return ModelSelectWrapper.newWrapper(this.getEntityClass())
              .in(ids)
              .selectList();
    }

    public  E findById(Object id){
        return ModelSelectWrapper.newWrapper(this.getEntityClass())
                .eq(id)
                .selectOne();
    }

    public   int insertOne(E obj){
        return insertList(Arrays.asList(obj));
    }

    public   int insertList(List<E> coll){

        return ModelInsertWrapper.newInstance(coll)
                .execute();
    }

    public int deleteById(String id){
        ModelDeleteWrapper wrapper =  this.newInstance().createDeleteWrapper();
        return wrapper.eq(id).execute();
    }


    public  int deleteByIdIn(List<String> coll){
        ModelDeleteWrapper wrapper =  this.newInstance().createDeleteWrapper();
        return wrapper.in(coll).execute();

    }

    public  int updateOne(E obj){
        return updateList(Arrays.asList(obj));
    }

    public int updateList(List<E> list){

        return ModelUpdateWrapper.newInstance(list)
                .execute();
    }

    public List<E> all(){
       return ModelSelectWrapper.newInstance(this.getEntityClass())
                .selectList();
    }

    public Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),SuperDao.class,0);
    }

    public E newInstance(){
        return (E)ReflectionUtil.newInstance( getEntityClass());
    }




    public <E> E getQueryProxy(E row){
        return SessionUtil.session().getLazyProxy(row);
    }

    public <E> E getSyncProxy(E row){
        return SessionUtil.session().getSyncProxy(row);
    }

}
*/

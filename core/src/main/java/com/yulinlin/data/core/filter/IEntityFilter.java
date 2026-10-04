package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.lang.reflection.GenericUtil;

//实体监听
public interface IEntityFilter<E> extends IRequestFilter {

    @Override
    default boolean test(String session, BaseRequest request) {
       Class c =   GenericUtil.getGeneric(this.getClass(),IEntityFilter.class,0);
       if(c != null && request.getEntityClass() != null){
           if(c.isAssignableFrom( request.getEntityClass())){
                return true;
           }
       }

       return  false;

    }


}

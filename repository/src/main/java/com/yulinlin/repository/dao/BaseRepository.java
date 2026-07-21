package com.yulinlin.repository.dao;

import com.yulinlin.data.lang.reflection.GenericUtil;

import java.util.Collection;
import java.util.List;

/**
 * 修改操作基类，必须继承他
 * 必须是单继承
 * @param <E>
 */
public interface BaseRepository<E> {

    int insert(E obj);

    int insertBatch(Collection<E> obj);

    int deleteByIdEq(Object id);

    int deleteByIdIn(List<?> ids);

    int update(E obj);

    int updateBatch(Collection<E> obj);

    E findByIdEq(Object id);

    List<E> findByIdIn(Collection<?> id);

    List<E> findAll();

    default Class getEntityClass(){
        Class clazz =  GenericUtil.getGeneric(this.getClass(),BaseRepository.class,0);
        return clazz;
    }

}

package com.yulinlin.common.service;


import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.util.Page;


import java.util.List;

public interface ISuperService<E extends AbstractModel<E>> {


    int save(E obj);
    int saveList(List<E> obj);

    public List<E> findByIdIn(List<?> ids);
    public  E findById(Object id);
    public   int insertOne(E obj);

    public   int insertList(List<E> coll);

    public int deleteById(String id);


    public  int deleteByIdIn(List<String> coll);

    public  int updateOne(E obj);

    public int updateList(List<E> list);

    public List<E> all();


    public Page<E> page(PagePo po);


    default Class<E> getEntityClass(){
        return (Class<E>)GenericUtil.getGeneric(this.getClass(),ISuperService.class,0);
    }
     E newInstance();
}

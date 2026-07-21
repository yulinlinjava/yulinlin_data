/*
package com.yulinlin.common.service.impl;


import com.yulinlin.common.dao.SuperDao;
import com.yulinlin.common.domain.po.OrderItem;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.domain.vo.PageVo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class ServiceImpl<E extends AbstractModel<E>>  implements ISuperService<E> {

    @Autowired
    private SuperDao<E> dao;

    public List<E> findByIdIn(List<?> ids){
        return dao.findByIdIn(ids);
    }

    public  E findById(Object id){
        return dao.findById(id);
    }

    public   int insertOne(E obj){
        return insertList(Arrays.asList(obj));
    }

    public   int insertList(List<E> coll){

        int total = 0;
        for (E e : coll) {

            total+=   dao.insertOne(e);
        }
        return  total;
    }

    public int deleteById(String id){
        return deleteByIdIn(Arrays.asList(id));
    }

    @Override
    public int saveList(List<E> coll) {
        int total = 0 ;
        for (E obj : coll) {

            if(ReflectionUtil.isEmpty(obj.primaryKey())) {

                total +=  this.insertOne(obj);
            }  else{
                E row =   findById(obj.primaryKey());
                if(row == null){
                    total +=  this.insertOne(obj);
                }else{
                    total +=  this.updateOne(obj);
                }

            }
        }

        return total;
    }

    @Override
    public int save(E obj) {
        return  saveList(Arrays.asList(obj));
    }

    public  int deleteByIdIn(List<String> coll){

        coll =  coll.stream().filter(row -> row != null).collect(Collectors.toList());

        return dao.deleteByIdIn(coll);

    }

    public  int updateOne(E obj){
        return updateList(Arrays.asList(obj));
    }

    public int updateList(List<E> list){

        int total = 0;
        for (E e : list) {
            total+=  dao.updateOne(e);
        }
        return  total;
    }

    public List<E> all(){
        return dao.all();
    }


    public PageVo<E> page(PagePo po){

        ModelSelectWrapper<E> wrapper =   ModelSelectWrapper.newInstance(this.getEntityClass(),po);

        if(po.getOrderList() != null){
            for (OrderItem orderItem : po.getOrderList()) {
                if(orderItem.isAsc()){
                    wrapper.orderByAsc(orderItem.getName());
                }else{
                    wrapper.orderByDesc(orderItem.getName());
                }
            }

        }


        wrapper.page(po.getPageNumber(),po.getPageSize());
        List<E> list =  wrapper.selectList();
        long total = wrapper.count();
        return PageVo.newInstance(list,total);
    }

    @Override
    public E newInstance() {
        return ReflectionUtil.newInstance(getEntityClass());
    }

}
*/

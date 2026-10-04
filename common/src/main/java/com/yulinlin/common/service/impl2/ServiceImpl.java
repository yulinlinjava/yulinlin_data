package com.yulinlin.common.service.impl2;


import com.yulinlin.common.domain.po.OrderItem;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.model.*;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public abstract class ServiceImpl<E extends AbstractModel<E>>  implements ISuperService<E> {



    public List<E> findByIdIn(List<?> ids){
        return ModelSelectWrapper.newInstance(getEntityClass())
                .in(ids)
                .selectList();


    }

    public  E findById(Object id)
    {
        return ModelSelectWrapper.newInstance(getEntityClass())
                .eq(id).selectOne();
    }

    public   int insertOne(E obj){
        return insertList(Arrays.asList(obj));
    }

    public   int insertList(List<E> coll){
        return ModelInsertWrapper.newInstance(coll)
                .execute();

    }

    public int deleteById(String id){
        return deleteByIdIn(Arrays.asList(id));
    }



    //数据分组
    protected      Map<Boolean,List<E>> dataGroup(List<E> coll){

        List<Object> ids = coll.stream()
                .filter(row -> row.primaryKeyValue() != null)
                .map(row -> row.primaryKeyValue()).collect(Collectors.toList());

        if(ids.isEmpty()){
            Map<Boolean,List<E>>  map =  new HashMap<>();
            map.put(false,    coll);
            return  map;
        }
        List<E> rows =   this.findByIdIn(ids);

        List<Object> primaryKeyValues =  rows.stream().map(row ->row.primaryKeyValue()).collect(Collectors.toList());

        Map<Boolean,List<E>> groupMaps=  coll.stream().collect(Collectors.groupingBy(row -> primaryKeyValues.contains(row.primaryKeyValue())));



        return groupMaps;
    }

    @Override
    public int saveList(List<E> coll) {
        int total = 0 ;


        List<Object> ids = coll.stream()
                .filter(row -> row.primaryKeyValue() != null)
                .map(row -> row.primaryKeyValue()).collect(Collectors.toList());

        if(ids.isEmpty()){
          return   this.insertList(coll);
        }
        for (Map.Entry<Boolean, List<E>> entry : dataGroup(coll).entrySet()) {
            if(entry.getKey().equals(true)){
                total+= this.updateList(entry.getValue());
            }else {
                total+=  this.insertList(entry.getValue());
            }
        }


        return total;
    }

    @Override
    public int save(E obj) {
        return  saveList(Arrays.asList(obj));
    }

    public  int deleteByIdIn(List<String> coll){

        if(coll.isEmpty()){
            return 0;
        }
        return ModelDeleteWrapper.newInstance(getEntityClass())
                .in(coll)
                .execute();


    }

    public  int updateOne(E obj){
        return updateList(Arrays.asList(obj));
    }

    public int updateList(List<E> list){

        return ModelUpdateWrapper.newInstance(list)
                .execute();




    }

    public List<E> all(){
        return ModelSelectWrapper.newInstance(getEntityClass()).selectList();
    }


    public Page<E> page(PagePo po){

              return  ModelSelectWrapper.newInstance(this.getEntityClass(),po)
                      .and(w -> {
                          if(po.getAnd() != null){
                              for (PagePo.Field field : po.getAnd()) {
                                  w.condition(field.getName(),field.getCondition(),field.getValue());
                              }
                          }

                      })
                      .or(w -> {
                          if(po.getOr() != null){
                              for (PagePo.Field field : po.getOr()) {
                                  w.condition(field.getName(),field.getCondition(),field.getValue());
                              }
                          }

                      })
                                .page(po.getPageNumber(),po.getPageSize())
                        .orderBy(f -> {
                            if(po.getOrderList() != null){
                                for (OrderItem orderItem : po.getOrderList()) {
                                    f.orderBy(orderItem.getName(),orderItem.isAsc());
                                }
                            }
                        }).selectPage();

    }


    @Override
    public E newInstance() {
        return ReflectionUtil.newInstance(getEntityClass());
    }
}

package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.anno.AggregationsEnum;

public interface IAggregationsWrapper<E> extends IWrapper {

    IAggregationsWrapper<E> field(String  name, String alias);


    default IAggregationsWrapper<E> field(AggregationsEnum aggregations,String  name, String alias){
        switch (aggregations){
            case field:{
                field(name,alias);
                break;
            }case minute:{
                minute(name,alias);
                break;
            }case day:{
                day(name,alias);
                break;
            }case hour:{
                hour(name,alias);
                break;
            }case month:{
                month(name,alias);
                break;
            }case quarter:{
                quarter(name,alias);
                break;
            }case year:{
                year(name,alias);
                break;
            }
        }
        return this;
    }


    IAggregationsWrapper<E> minute(String  name, String alias);

    IAggregationsWrapper<E> hour(String  name, String alias);

    IAggregationsWrapper<E> day(String  name, String alias);

    IAggregationsWrapper<E> month(String  name, String alias);

    IAggregationsWrapper<E> quarter(String  name, String alias);

    IAggregationsWrapper<E> interval(String  name,  String alias,int interval);

    IAggregationsWrapper<E> year(String  name, String alias);


    

}

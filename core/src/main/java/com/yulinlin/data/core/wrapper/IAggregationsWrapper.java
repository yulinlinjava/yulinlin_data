package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.anno.AggregationsEnum;

public interface IAggregationsWrapper<E,R> extends IWrapper {

    IAggregationsWrapper<E,R> field(String  name, String alias);


    default IAggregationsWrapper<E,R> field(AggregationsEnum aggregations,String  name, String alias){
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


    IAggregationsWrapper<E,R> minute(String  name, String alias);

    IAggregationsWrapper<E,R> hour(String  name, String alias);

    IAggregationsWrapper<E,R> day(String  name, String alias);

    IAggregationsWrapper<E,R> month(String  name, String alias);

    IAggregationsWrapper<E,R> quarter(String  name, String alias);

    IAggregationsWrapper<E,R> interval(String  name,  String alias,int interval);

    IAggregationsWrapper<E,R> year(String  name, String alias);


    

}

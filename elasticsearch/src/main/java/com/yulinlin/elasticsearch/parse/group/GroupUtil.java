package com.yulinlin.elasticsearch.parse.group;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;

public class GroupUtil {

    public static ThreadLocal<  Aggregation.Builder > builder = new ThreadLocal();


    public static void set( Aggregation.Builder val){
        builder.set(val);
    }



    public static Aggregation.Builder  get( ){
        return builder.get();

    }

}

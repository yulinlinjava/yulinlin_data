package com.yulinlin.data.core.util;

import com.yulinlin.data.lang.util.ListString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ListUtil {

    //集合变化处理
    public  static <E> List<E> encodeCollection(Collection<E> coll){
        if(coll instanceof ListString){
           return  (ListString)coll;
        }
        ArrayList list =   new ArrayList<>();

        LinkedList items =   new LinkedList<>();

        items.addAll(coll);

        while (!items.isEmpty()){
            Object val =  items.poll();
            if(val instanceof Collection){
                items.addAll((Collection)val);
            }else{
                list.add(val);
            }
        }
        return  list;
    }
}

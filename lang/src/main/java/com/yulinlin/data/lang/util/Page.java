package com.yulinlin.data.lang.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import lombok.Data;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public class Page<E>  implements Iterable<E>{


    private List<E> list;

    private int total;

    private Map<String,Object> extra;


    public Page() {
    }



    public Page<E> putValue(String key,Object value){
        if(extra == null){
            extra = new HashMap<>();
        }
        extra.put(key,value);
        return this;
    }

    public <V> V getValue(String key){
        if(extra != null){
            return (V)extra.get(key);
        }
        return null;
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    public Page(List<E> list) {
        this.list = list;
        this.total = list.size();
    }

    public Page(List<E> list, int total) {
        this.list = list;
        this.total = total;
    }

    public Page<E> filter(Function<E,Boolean> function){
        List<E> collect = list.stream().filter(row -> function.apply(row)).collect(Collectors.toList());
        return new Page<>(collect,total);
    }


    public <T> Page<T> map(Function<E,T> function){

        ArrayList<T> objects = new ArrayList<>();

        int t = total;

        for (E e : list) {
            T apply = function.apply(e);
            if(apply != null){
                objects.add(apply);
            }else {
                t--;
            }
        }

        return new Page<>(objects,t);
    }

    public   Page<E> page(int pageNumber,int pageSize){
        List<E> data =  page(list, pageNumber, pageSize);
        return new Page(data,total);
    }

    public   Page<E> shuffle(){
        ArrayList<E> es = new ArrayList<>(list);
        Collections.shuffle(es);
        return new Page(es,total);
    }

    public   Page<E> random(int len){
        if(len >= list.size()){
            return this;
        }
        int start = RandomUtil.randomInt(0, list.size() - len);
        List<E> es = list.subList(start, start + len);
        return new Page(new ArrayList(es),total);
    }

    public  E randomOne(){
        int i =  RandomUtil.randomInt(0,list.size());
        return list.get(i);
    }


    public static  <E> List<E> page(List<E> data, int pageNumber,int pageSize){

        if(data.isEmpty()){
            return data;
        }
        int start = (pageNumber - 1)*pageSize;
        int end = pageNumber *pageSize;
        if(end > data.size()){
            end = data.size();
        }
        if(start >= end){
            return new ArrayList();
        }

        if(start > data.size()){
            return new ArrayList();
        }


        return   new ArrayList( data.subList(start,end));
    }

    public static <E> Page<E> of(List<E> list, long total){
        return new Page<>(list,(int)total);
    }

    public static <E> Page<E> of(List<E> list, int total){
        return new Page<>(list,total);
    }

    public static <E> Page<E> of(List<E> list){
        return new Page<>(list);
    }

    public static void main(String[] args) {
        Page<Integer> of = Page.of(Arrays.asList(1, 2, 3, 4, 5, 6));

        for(int i = 0;i<100;i++){
            Page<Integer> random = of.random(3);
            System.out.println(random.list.toString());
        }

        int a = 0;

    }
}

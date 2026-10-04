package com.yulinlin.data.lang.tuple;

public class MutableTriple<K,M,V> extends Triple{


    public MutableTriple(Object... values) {
        super(values);
    }

    public K getLeft(){
        return get(0);
    }

    public M getMiddle(){
        return get(1);
    }

    public V getRight(){
        return get(2);
    }

    public static <K,M,V> MutableTriple<K,M,V> of(K k,M m,V v){
        return new MutableTriple(k,m,v);
    }

}

package com.yulinlin.data.lang.tuple;

public class Triple {

    private Object[] values;

    public Triple(Object... values) {
        this.values = values;
    }

    protected void put(int i,Object value){
        values[i] = value;
    }

    protected   <V> V get(int i ){
        return (V)values[i];
    }

}

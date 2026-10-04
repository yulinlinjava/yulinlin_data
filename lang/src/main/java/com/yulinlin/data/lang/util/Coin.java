package com.yulinlin.data.lang.util;

import java.util.function.Function;

public class Coin extends Currency<Double> {

    public Coin() {
    }

    public Coin(Double value) {
        super(value);
    }

    @Override
    public int decimal() {
        return 1;
    }


    @Override
    public Coin apply(Function<Double, Double> func) {
        return Coin.of(     func.apply(getValue()));
    }

    @Override
    public Long encode() {
        return (long)(getValue() * decimalSize());
    }

    @Override
    public void decode(Object str) {
      Double val =   Double.parseDouble(str.toString())/ decimalSize() ;
      setValue(val);
    }


    public Coin reverse(){
        double v = -getValue();
        return Coin.of(v);
    }

    @Override
    public int intValue() {
        return getValue().intValue();
    }

    @Override
    public long longValue() {
        return getValue().longValue();
    }

    @Override
    public float floatValue() {
        return getValue().floatValue();
    }

    @Override
    public double doubleValue() {
        return getValue().doubleValue();
    }

    public static Coin of(double d){
        return new Coin(d);
    }
}

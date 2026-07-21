package com.yulinlin.data.lang.util;

import java.util.function.Function;

public class Money extends Currency<Double> {

    public Money() {
    }

    public Money(Double value) {
        super(value);
    }

    @Override
    public int decimal() {
        return 2;
    }

    @Override
    public Money apply(Function<Double, Double> func) {
        return Money.of(     func.apply(getValue()));
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


    public Money reverse(){
        double v = -getValue();
        return Money.of(v);
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

    public static Money of(double d){
        return new Money(d);
    }
}

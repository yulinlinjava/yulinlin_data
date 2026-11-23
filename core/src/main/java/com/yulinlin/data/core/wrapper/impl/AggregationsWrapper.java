package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.anno.AggregationsEnum;
import java.util.Map;

public class AggregationsWrapper<E> extends AbstractAggregationsWrapper<E> {

    public AggregationsWrapper() {
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AggregationsWrapper<E> field(String  name, String alias) { super.field(name, alias); return this; }
    @Override public AggregationsWrapper<E> minute(String  name, String alias) { super.minute(name, alias); return this; }
    @Override public AggregationsWrapper<E> hour(String  name, String alias) { super.hour(name, alias); return this; }
    @Override public AggregationsWrapper<E> day(String  name, String alias) { super.day(name, alias); return this; }
    @Override public AggregationsWrapper<E> month(String  name, String alias) { super.month(name, alias); return this; }
    @Override public AggregationsWrapper<E> quarter(String  name, String alias) { super.quarter(name, alias); return this; }
    @Override public AggregationsWrapper<E> interval(String  name,  String alias,int interval) { super.interval(name, alias, interval); return this; }
    @Override public AggregationsWrapper<E> year(String  name, String alias) { super.year(name, alias); return this; }
    @Override public AggregationsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AggregationsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AggregationsWrapper<E> field(AggregationsEnum type, String name, String alias) { super.field(type, name, alias); return this; }
}

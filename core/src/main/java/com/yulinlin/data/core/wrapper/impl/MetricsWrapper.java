package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.anno.MetricsEnum;
import java.util.Map;

public class MetricsWrapper<E> extends AbstractMetricsWrapper<E> {

    public MetricsWrapper() {
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public MetricsWrapper<E> distinctCount(String name, String alias) { super.distinctCount(name, alias); return this; }
    @Override public MetricsWrapper<E> count(String name, String alias) { super.count(name, alias); return this; }
    @Override public MetricsWrapper<E> sum(String name, String alias) { super.sum(name, alias); return this; }
    @Override public MetricsWrapper<E> avg(String name, String alias) { super.avg(name, alias); return this; }
    @Override public MetricsWrapper<E> min(String name, String alias) { super.min(name, alias); return this; }
    @Override public MetricsWrapper<E> max(String name, String alias) { super.max(name, alias); return this; }
    @Override public MetricsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public MetricsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public MetricsWrapper<E> field(MetricsEnum type, String name, String alias) { super.field(type, name, alias); return this; }
}

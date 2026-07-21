package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IMetricsWrapper;

public class MetricsWrapper<E> extends AbstractMetricsWrapper<E,MetricsWrapper<E>> {

    public MetricsWrapper() {
    }

    public MetricsWrapper(String name) {
        super(name);
    }

    @Override
    public <N, NR extends IMetricsWrapper<N, NR>> NR object(String name) {
        return null;
    }

    @Override
    public <N, NR extends IMetricsWrapper<N, NR>> NR object(String name, Class<N> clazz) {
        return object(name);
    }
}

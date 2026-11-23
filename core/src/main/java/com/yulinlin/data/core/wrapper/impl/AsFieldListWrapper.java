package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;

public class AsFieldListWrapper<E> extends AbstractAsFieldListWrapper<E> {

    public AsFieldListWrapper() {
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AsFieldListWrapper<E> field(String name) { super.field(name); return this; }
    @Override public AsFieldListWrapper<E> field(String name, String alias) { super.field(name, alias); return this; }
    @Override public AsFieldListWrapper<E> field(LambdaPropertyFunction<E> name, String alias) { super.field(name, alias); return this; }
    @Override public AsFieldListWrapper<E> highlight(String name) { super.highlight(name); return this; }
    @Override public AsFieldListWrapper<E> highlight(LambdaPropertyFunction<E> name) { super.highlight(name); return this; }
    @Override public AsFieldListWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AsFieldListWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}

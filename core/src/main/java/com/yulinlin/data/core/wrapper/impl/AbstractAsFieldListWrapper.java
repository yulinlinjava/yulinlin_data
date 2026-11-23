package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAsFieldListWrapper<E> extends AbstractWrapper<IAsFieldListWrapper<E>> implements IAsFieldListWrapper<E>, IWrapper {

    private Map<String,AsField> map = new HashMap<>();

    public AbstractAsFieldListWrapper() {
    }

    @Override
    public AbstractAsFieldListWrapper<E> field(String name) {
        return field(name,name);
    }

    @Override
    public AbstractAsFieldListWrapper<E> field(String name, String alias) {

        AsField asField = new AsField(name, alias);
        AsField existing = map.get(name);
        if (existing != null) asField.setHighlight(existing.isHighlight());
        asField.put(getMetaAndReset());
        map.put(name,asField);
        return this;
    }

    @Override
    public AbstractAsFieldListWrapper<E> field(LambdaPropertyFunction<E> name, String alias) {
        AsField asField = new AsField(name, alias);
        AsField existing = map.get(asField.getKey());
        if (existing != null) asField.setHighlight(existing.isHighlight());
        asField.put(getMetaAndReset());
        map.put(asField.getKey(),asField);
        return this;
    }

    @Override
    public AbstractAsFieldListWrapper<E> highlight(String name) {
        AsField field = map.computeIfAbsent(name, key -> new AsField(key, key));
        field.setHighlight(true);
        return this;
    }

    @Override
    public AbstractAsFieldListWrapper<E> highlight(LambdaPropertyFunction<E> name) {
        AsField candidate = new AsField(name, "");
        AsField field = map.computeIfAbsent(candidate.getKey(), key -> new AsField(name, key));
        field.setHighlight(true);
        return this;
    }

    public Collection<AsField> getList() {
        return map.values();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractAsFieldListWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractAsFieldListWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}

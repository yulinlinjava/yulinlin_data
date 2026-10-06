package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IChildrenWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAsFieldListWrapper<E,R extends AbstractAsFieldListWrapper<E,R>> extends IChildrenWrapper<R> implements IAsFieldListWrapper<E,R> {



    private Map<String,AsField> map = new HashMap<>();


    public AbstractAsFieldListWrapper() {
    }

    public AbstractAsFieldListWrapper(String name) {
        super(name);
    }


    @Override
    public R field(String name) {
        return field(name,name);
    }

    @Override
    public R field(String name, String alias) {

        AsField asField = new AsField(name, alias);
        AsField existing = map.get(name);
        if (existing != null) asField.setHighlight(existing.isHighlight());
        asField.put(getMetaAndReset());
        map.put(name,asField);
        return (R)this;
    }




    @Override
    public R field(LambdaPropertyFunction<E> name, String alias) {
        AsField asField = new AsField(name, alias);
        AsField existing = map.get(asField.getKey());
        if (existing != null) asField.setHighlight(existing.isHighlight());
        asField.put(getMetaAndReset());
        map.put(asField.getKey(),asField);
        return (R)this;
    }

    @Override
    public R highlight(String name) {
        AsField field = map.computeIfAbsent(name, key -> new AsField(key, key));
        field.setHighlight(true);
        return (R)this;
    }

    @Override
    public R highlight(LambdaPropertyFunction<E> name) {
        AsField candidate = new AsField(name, "");
        AsField field = map.computeIfAbsent(candidate.getKey(), key -> new AsField(name, key));
        field.setHighlight(true);
        return (R)this;
    }



    public Collection<AsField> getList() {
        return map.values();
    }
}

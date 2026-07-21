package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IInsertFieldsWrapper;
import com.yulinlin.data.core.wrapper.IInsertWrapper;

import java.util.function.Consumer;

public abstract class AbstractInsertWrapper<
        E,R extends AbstractInsertWrapper<E,R,U>,
        U extends IInsertFieldsWrapper<E,U>

        > extends AbstractWrapper<R> implements IInsertWrapper<E,R,U> {

    private Store table;

    private U columns;


    public AbstractInsertWrapper() {
        this.columns = buildColumns();
    }

    protected abstract U buildColumns();



    public R table(String name){
        this.table = new Store(name);
        return (R)this;
    }

    @Override
    public U fields() {
        return columns;
    }

    @Override
    public R fields(Consumer<U> func) {
        func.accept(columns);
        return (R)this;
    }



    @Override
    public R table(String name, String alias) {
        return table(name);
    }


    public Store getFrom() {
        return table;
    }


    @Override
    public AbstractInsertWrapper clone()  {
        try {
            return (AbstractInsertWrapper)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

}


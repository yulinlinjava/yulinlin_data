package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;

import java.util.function.Consumer;

public abstract class AbstractDeleteWrapper<
        E,
        R extends AbstractDeleteWrapper<E,R,W>,
        W extends IConditionWrapper<E,W>>

        extends AbstractWrapper<R> implements IDeleteWrapper<E,R,W> {

    private Store from;

    private W where;

    public AbstractDeleteWrapper() {
        this.where = buildWhere();
    }

    public R table(String name){
        this.from = new Store(name);
        return (R)this;
    }

    protected abstract W buildWhere();


   @Override
    public R table(String name, String alias) {
        return table(name);
    }

    public W where(){
        return where;
    }


    @Override
    public R where(W wrapper) {
        where.and(wrapper);
        return (R)this;
    }

    @Override
    public R where(Consumer<W> func) {
        func.accept(where);
        return (R)this;
    }



    public Store getFrom() {
        return from;
    }




    @Override
    public R clone()  {
        try {
            return (R)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}

package com.yulinlin.common.model;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.model.BaseModelInsertWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import java.util.Collection;

public class ModelInsertWrapper<E> extends BaseModelInsertWrapper<E> {
    public ModelInsertWrapper(String session, Object model) {
        super(session, model);
    }

    public static <E> ModelInsertWrapper<E> newInstance(E obj){
        return new ModelInsertWrapper(null,obj);
    }
    public static <E> ModelInsertWrapper<E> newInstance(Class<E> obj){
        return new ModelInsertWrapper(null,obj);
    }

    public static <E> ModelInsertWrapper<E> newInstance( Collection<E> list){

        return new ModelInsertWrapper(null,list);
    }





    public static <E> ModelInsertWrapper<E> newInstance(String session, E obj){
        ModelInsertWrapper wrapper = new ModelInsertWrapper( session,obj);
        return wrapper;
    }


    public static <E> ModelInsertWrapper<E> newInstance(String session, Collection<E> list){
        return new ModelInsertWrapper(session,list);
    }


    public static <E> ModelInsertWrapper<E> newInstance(String session,Class<E> clazz){
        return new ModelInsertWrapper(session,clazz);
    }

    // Covariant returns keep fluent calls typed without a self-type generic.
    @Override public ModelInsertWrapper<E> batch(boolean batch) { super.batch(batch); return this; }
    @Override public ModelInsertWrapper<E> batch() { super.batch(); return this; }
    @Override public ModelInsertWrapper<E> queue() { super.queue(); return this; }
    @Override public ModelInsertWrapper<E> clearCache(boolean clearCache) { super.clearCache(clearCache); return this; }
    @Override public ModelInsertWrapper<E> root(Object root) { super.root(root); return this; }
    @Override public ModelInsertWrapper<E> field(String name, Object value) { super.field(name, value); return this; }
    @Override public ModelInsertWrapper<E> field(LambdaPropertyFunction<E> name, Object value) { super.field(name, value); return this; }
}

package com.yulinlin.data.core.model;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.exception.CodeException;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.factory.AbstractSelectWrapperFactory;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.core.annotation.AnnotationUtils;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public abstract class ModelConditionWrapper<E>
{


    public ModelConditionWrapper<E> parseWhereCondition(Object query) {
        if(query != null){
            AbstractSelectWrapperFactory.initSelectWhere(    where(),query);

        }


        return this;
    }

    public ModelConditionWrapper<E> not(Consumer<IConditionWrapper<E>> func){
        where().not((f) -> {
            func.accept(f);
        });
        return this;
    }
    public ModelConditionWrapper<E> struct(){
        where().struct();
        return this;
    }


    public  ModelConditionWrapper<E> and(IConditionWrapper<?> func){
        IConditionWrapper<E> where = where();
        where.and(func);
        return this;
    }



    public  ModelConditionWrapper<E> and(Consumer<IConditionWrapper<E>> func){
        IConditionWrapper<E> where = where();
        func.accept(where);
        return this;
    }

    //自定义条件
    public ModelConditionWrapper<E> condition(String name, ConditionEnum condition, Object value){
        where().condition(name,condition,value);
        return this;
    }
    public ModelConditionWrapper<E> expression(String name,Map<String,Object> data){
        where().expression(name,data);
        return this;
    }
    public ModelConditionWrapper<E> expression(String name){
        where().expression(name);
        return this;
    }
    //=
    public ModelConditionWrapper<E> eq(String name, Object value){
        where().eq(name,value);
        return this;
    }

    public ModelConditionWrapper<E> eq(LambdaPropertyFunction<E> name, Object value){
        where().eq(name,value);
        return this;
    }


    //!=
    //!=
    public ModelConditionWrapper<E> ne(String name, Object value){
        where().ne(name,value);
        return this;
    }

    public ModelConditionWrapper<E> ne(LambdaPropertyFunction<E> name, Object value){
        where().ne(name,value);
        return this;
    }


    //<
    public ModelConditionWrapper<E> gt(String name, Object value){


        where().gt(name,value);
        return this;
    }

    public ModelConditionWrapper<E> gt(LambdaPropertyFunction<E> name, Object value){
        where().gt(name,value);
        return this;
    }


    //<=
    public ModelConditionWrapper<E> gte(String name, Object value){
        where().gte(name,value);
        return this;
    }

    public ModelConditionWrapper<E> gte(LambdaPropertyFunction<E> name, Object value){
        where().gte(name,value);
        return this;
    }


    //>
    public ModelConditionWrapper<E> lt(String name, Object value){
        where().lt(name,value);
        return this;
    }

    public ModelConditionWrapper<E> lt(LambdaPropertyFunction<E> name, Object value){
        where().lt(name,value);
        return this;
    }


    //>=
    public ModelConditionWrapper<E> lte(String name, Object value){
        where().lte(name,value);
        return this;
    }

    public ModelConditionWrapper<E> lte(LambdaPropertyFunction<E> name, Object value){
        where().lte(name,value);
        return this;
    }


    //范围
    public ModelConditionWrapper<E> between(String name, Object x, Object y){
        where().between(name, Arrays.asList( x,y));
        return this;
    }

    public ModelConditionWrapper<E> between(LambdaPropertyFunction<E> name, List<?> list){
        where().between(name,list);
        return this;
    }

    //范围
    public ModelConditionWrapper<E> between(String name, List<?> list){
        where().between(name,list);
        return this;
    }


    //模糊
    public ModelConditionWrapper<E> like(String name, String value){

        where().like(name,value);
        return this;
    }

    public ModelConditionWrapper<E> like(LambdaPropertyFunction<E> name, String list){
        where().like(name,list);
        return this;
    }

    /** Full-text match where supported; otherwise uses the data source's fallback semantics. */
    public ModelConditionWrapper<E> match(String name, String value){
        where().match(name,value);
        return this;
    }

    public ModelConditionWrapper<E> match(LambdaPropertyFunction<E> name, String value){
        where().match(name,value);
        return this;
    }



    //右模糊
    public ModelConditionWrapper<E> likeRight(String name, String value){
        where().likeRight(name,value);
        return this;
    }

    public ModelConditionWrapper<E> likeRight(LambdaPropertyFunction<E> name, String list){
        where().likeRight(name,list);
        return this;
    }

    //是null
    public ModelConditionWrapper<E> isNull(String name){
        where().isNull(name);
        return this;
    }
    public ModelConditionWrapper<E> isNull(LambdaPropertyFunction<E> name){
        where().isNull(name);
        return this;
    }

    //属于集合
    public ModelConditionWrapper<E> in(String name, Collection<?> coll){
        where().in(name,coll);
        return this;
    }

    public ModelConditionWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll){
        where().in(name,coll);
        return this;
    }
    public ModelConditionWrapper<E> nin(String name, Collection<?> coll){
        where().not().in(name,coll);
        return this;
    }

    //下一个使用not
    public  ModelConditionWrapper<E> eq(Object value){
        where().eq(primaryKeyName(),value);
        return this;
    }

    //下一个使用not
    public  ModelConditionWrapper<E> nin(Collection<?> coll){
        return nin(primaryKeyName(),coll);
    }

    public  ModelConditionWrapper<E> in(Collection<?> coll){
        return in(primaryKeyName(),coll);
    }
    protected abstract Class getModelClass();

    protected String primaryKeyName(){
        return PrimaryUtil.getPrimaryKeyName(getModelClass());
    }
    
    public abstract  IConditionWrapper<E> where();

    public  ModelConditionWrapper<E> or(Consumer<IConditionWrapper<E>> func) {
        where().or(f -> {
            func.accept(f);
        });
        return this;
    }



    public  ModelConditionWrapper<E> where(Consumer<IConditionWrapper<E>> func) {
        func.accept(      where());
        return this;
    }


}

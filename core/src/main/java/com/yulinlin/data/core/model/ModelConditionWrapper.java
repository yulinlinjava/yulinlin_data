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

public abstract class ModelConditionWrapper<
        E,
        W extends IConditionWrapper<E,W>,
        R extends ModelConditionWrapper<E,W,R>
        >
{


    public R parseWhereCondition(Object query) {
        if(query != null){
            AbstractSelectWrapperFactory.initSelectWhere(    where(),query);

        }


        return (R)this;
    }

    public R not(Consumer<IConditionWrapper<E,W>> func){
        where().not((f) -> {
            func.accept(f);
        });
        return (R)this;
    }
    public R struct(){
        where().struct();
        return (R)this;
    }


    public  R and(IConditionWrapper func){
        IConditionWrapper<E, W> where = where();
        where.and((W)func);
        return (R)this;
    }



    public  R and(Consumer<IConditionWrapper<E,W>> func){
        IConditionWrapper<E, W> where = where();
        func.accept(where);
        return (R)this;
    }

    //自定义条件
    public R condition(String name, ConditionEnum condition, Object value){
        where().condition(name,condition,value);
        return (R)this;
    }
    public R expression(String name,Map<String,Object> data){
        where().expression(name,data);
        return (R)this;
    }
    public R expression(String name){
        where().expression(name);
        return (R)this;
    }
    //=
    public R eq(String name, Object value){
        where().eq(name,value);
        return (R)this;
    }

    public R eq(LambdaPropertyFunction<E> name, Object value){
        where().eq(name,value);
        return (R)this;
    }


    //!=
    //!=
    public R ne(String name, Object value){
        where().ne(name,value);
        return (R)this;
    }

    public R ne(LambdaPropertyFunction<E> name, Object value){
        where().ne(name,value);
        return (R)this;
    }


    //<
    public R gt(String name, Object value){


        where().gt(name,value);
        return (R)this;
    }

    public R gt(LambdaPropertyFunction<E> name, Object value){
        where().gt(name,value);
        return (R)this;
    }


    //<=
    public R gte(String name, Object value){
        where().gte(name,value);
        return (R)this;
    }

    public R gte(LambdaPropertyFunction<E> name, Object value){
        where().gte(name,value);
        return (R)this;
    }


    //>
    public R lt(String name, Object value){
        where().lt(name,value);
        return (R)this;
    }

    public R lt(LambdaPropertyFunction<E> name, Object value){
        where().lt(name,value);
        return (R)this;
    }


    //>=
    public R lte(String name, Object value){
        where().lte(name,value);
        return (R)this;
    }

    public R lte(LambdaPropertyFunction<E> name, Object value){
        where().lte(name,value);
        return (R)this;
    }


    //范围
    public R between(String name, Object x, Object y){
        where().between(name, Arrays.asList( x,y));
        return (R)this;
    }

    public R between(LambdaPropertyFunction<E> name, List<?> list){
        where().between(name,list);
        return (R)this;
    }

    //范围
    public R between(String name, List<?> list){
        where().between(name,list);
        return (R)this;
    }


    //模糊
    public R like(String name, String value){

        where().like(name,value);
        return (R)this;
    }

    public R like(LambdaPropertyFunction<E> name, String list){
        where().like(name,list);
        return (R)this;
    }



    //右模糊
    public R likeRight(String name, String value){
        where().likeRight(name,value);
        return (R)this;
    }

    public R likeRight(LambdaPropertyFunction<E> name, String list){
        where().likeRight(name,list);
        return (R)this;
    }

    //是null
    public R isNull(String name){
        where().isNull(name);
        return (R)this;
    }
    public R isNull(LambdaPropertyFunction<E> name){
        where().isNull(name);
        return (R)this;
    }

    //属于集合
    public R in(String name, Collection<?> coll){
        where().in(name,coll);
        return (R)this;
    }

    public R in(LambdaPropertyFunction<E> name, Collection<?> coll){
        where().in(name,coll);
        return (R)this;
    }
    public R nin(String name, Collection<?> coll){
        where().not().in(name,coll);
        return (R)this;
    }

    //下一个使用not
    public  R eq(Object value){
        where().eq(primaryKeyName(),value);
        return (R)this;
    }

    //下一个使用not
    public  R nin(Collection<?> coll){
        return nin(primaryKeyName(),coll);
    }

    public  R in(Collection<?> coll){
        return in(primaryKeyName(),coll);
    }
    protected abstract Class getModelClass();

    protected String primaryKeyName(){
        return PrimaryUtil.getPrimaryKeyName(getModelClass());
    }
    
    public abstract  IConditionWrapper<E,W> where();

    public  R or(Consumer<IConditionWrapper<E,W>> func) {
        where().or(f -> {
            func.accept(f);
        });
        return (R)this;
    }



    public  R where(Consumer<IConditionWrapper<E,W>> func) {
        func.accept(      where());
        return (R)this;
    }


}

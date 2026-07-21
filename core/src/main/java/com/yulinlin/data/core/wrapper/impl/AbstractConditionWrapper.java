package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.util.ListUtil;
import com.yulinlin.data.core.wrapper.IChildrenWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.node.*;
import com.yulinlin.data.core.node.base.*;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.lang.lambda.LambdaUtils;
import com.yulinlin.data.lang.util.ListString;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public abstract class AbstractConditionWrapper<E,R extends AbstractConditionWrapper<E,R>>

        extends IChildrenWrapper<R>
        implements IConditionWrapper<E,R>  {


    public static int and = 1;

    public static int or = 0;

    public static int not = -1;
    private int  model ;

    private int size;

    private ICondition where ;



    public AbstractConditionWrapper() {
        init(and);
    }

    public AbstractConditionWrapper(String name) {

        super(name);
        init(and);
    }

    private void init(int model){
        this.model = model;
        if(model == and){
            where = new And();
        }else if(model == or) {
            where = new Or();
        }else {
            where = new Not();
        }
    }

    protected R addNode(ICondition condition){
         size++;
         if(model == and){
             where.and(condition);
         }else if(model == or) {
             where.or(condition);
         }else {
             where.not(condition);
         }
         if(condition instanceof AbstractCondition){
             AbstractCondition c = (AbstractCondition) condition;
             c.put(getMetaAndReset());
         }
        return (R)this;
    }


    public int getSize() {
        return size;
    }


    public R eq(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.eq,value);
        return (R)this;
    }

    public R ne(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.ne,value);
        return (R)this;
    }


    public R gt(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.gt,value);
        return (R)this;
    }

    public R gte(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.gte,value);
        return (R)this;
    }


    public R lt(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.lt,value);
        return (R)this;
    }


    public R lte(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.lte,value);
        return (R)this;
    }




    public R between(LambdaPropertyFunction<E> name, Collection value) {

        condition(name,ConditionEnum.between,value);
        return (R)this;
    }


    public R like(LambdaPropertyFunction<E> name, String value) {
        condition(name,ConditionEnum.like,value);

        return (R)this;
    }





    public R likeRight(LambdaPropertyFunction<E> name, String value) {
        condition(name,ConditionEnum.likeRight,value);
        return (R)this;
    }


    public R isNull(LambdaPropertyFunction<E> name) {

        condition(name,ConditionEnum.isNull,1);

        return (R)this;
    }


    public R in(LambdaPropertyFunction<E> name, Collection<?> coll) {


        condition(name,ConditionEnum.in,coll);

        return (R)this;
    }


    //集合变化处理
    private static <E> Collection<E> encodeCollection(Collection<E> coll){


        return  ListUtil.encodeCollection(coll);
    }


    public R condition(Object key, ConditionEnum condition, Object arg) {

        if(condition != ConditionEnum.isNull && arg == null){

            return (R)this;
        }else if(arg instanceof String){
            if(arg.equals("")){

                return (R)this;
            }
        }else if(arg instanceof Collection){
            Collection collection = (Collection)arg;
            if(collection.isEmpty()){

                return (R)this;
            }

        }


        Object value;
        if(arg instanceof Collection){
            Collection coll =  encodeCollection((Collection) arg);
            value =coll;
        }else {
            value =arg;
        }



        Object name = key;
         ICondition node = null;

        switch (condition){
            case eq:{
                node = new Eq(name,value);


                break;
            }case ne:{
                node = new Ne(name,value);

                break;
            }case gte:{
                node = new Gte(name,value);

                break;
            }case gt:{
                node = new Gt(name,value);

                break;
            }case lte:{
                node = new Lte(name,value);

                break;
            }case lt:{
                node = new Lt(name,value);

                break;
            }case in:{

                node = new In(name,(Collection)value);

                break;
            }case like:{
                node = new Like(name,value);

                break;
            }case likeRight:{
                node = new LikeRight(name,value);
                break;
            }case between:{
                node = new Between(name,(Collection)value);

                break;
            }case isNull:{
                node = new IsNull(name);
                break;
            }
        }



        addNode(node);
        return (R)this;
    }



    //自定义条件表达式
    public R expression(String name , Map<String,Object> params){
        return addNode(new Expression(name,params));
    }

    public R eq(String name, Object value){
        return condition(name,ConditionEnum.eq,value);
    }
    public R ne(String name, Object value){
        return condition(name,ConditionEnum.ne,value);
    }
    public R gte(String name, Object value){

        return condition(name,ConditionEnum.gte,value);
    }
    public R gt(String name, Object value){
        return condition(name,ConditionEnum.gt,value);
    }
    public R lte(String name, Object value){
        return condition(name,ConditionEnum.lte,value);
    }
    public R lt(String name, Object value){

        return condition(name,ConditionEnum.lt,value);
    }
    public R like(String name, String value){

        return condition(name,ConditionEnum.like,value);
    }

    public R between(String name, Collection value){
        return condition(name,ConditionEnum.between,value);
    }


    public R likeRight(String name, String value){

        return condition(name,ConditionEnum.likeRight,value);
    }

    public R isNull(String name)
    {
        return condition(name,ConditionEnum.isNull,null);
    }

    public R in(String name, Collection<?> coll){
        return condition(name,ConditionEnum.in,coll);
    }

    @SneakyThrows
    protected R newInstance(){
        R r =  (R) this.getClass().getConstructor().newInstance();

        return r;
    }


    @SneakyThrows
    protected R newInstance(String name){
        R r =  (R) this.getClass().getConstructor(String.class).newInstance(name);
        return r;
    }

    @Override
    public R or(Consumer<R> consumer) {

        consumer.accept(or());
        return (R)this;
    }

    @Override
    public R or() {
        AbstractConditionWrapper r = newInstance();
        r.init(or);
        addNode(r.getCondition());
        return (R)r;
    }

    @Override
    public R and() {
        AbstractConditionWrapper r = newInstance();
        r.init(and);
        addNode(r.getCondition());
        return (R)r;
    }


    @Override
    public R and(Consumer<R> consumer) {

        consumer.accept(and());
        return (R)this;
    }

    @Override
    public R and(R conditionWrapper) {
        addNode(conditionWrapper.getCondition());
        return (R)this;
    }

    @Override
    public R not() {
        R r = newInstance();
        AbstractConditionWrapper c =r;
        c.init(not);
        addNode(r.getCondition());
        return r;
    }

    @Override
    public R not(Consumer<R> consumer) {
        R r =not();
        consumer.accept(r);
        return (R)this;
    }

    @Override
    public R struct() {
        ICondition w = where;
        init(and);
        addNode(w);
        return (R)this;
    }

/*
    @Override
    public R struct(Consumer<R> func) {
        R r = newInstance(true);
        func.accept(r);
        return (R)this;
    }
*/




    public ICondition getCondition(){
        return where;
    }



}

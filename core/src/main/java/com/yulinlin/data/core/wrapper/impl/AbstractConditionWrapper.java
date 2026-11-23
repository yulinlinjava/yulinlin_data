package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.util.ListUtil;
import com.yulinlin.data.core.wrapper.IWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.core.node.*;
import com.yulinlin.data.core.node.base.*;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import lombok.SneakyThrows;

import java.util.*;
import java.util.function.Consumer;

public abstract class AbstractConditionWrapper<E>

        extends AbstractWrapper<IConditionWrapper<E>>
        implements IConditionWrapper<E>, IWrapper {


    public static int and = 1;

    public static int or = 0;

    public static int not = -1;
    private int  model ;

    private int size;

    private ICondition where ;



    public AbstractConditionWrapper() {
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

    protected IConditionWrapper<E> addNode(ICondition condition){
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
        return this;
    }


    public int getSize() {
        return size;
    }


    public IConditionWrapper<E> eq(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.eq,value);
        return this;
    }

    public IConditionWrapper<E> ne(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.ne,value);
        return this;
    }


    public IConditionWrapper<E> gt(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.gt,value);
        return this;
    }

    public IConditionWrapper<E> gte(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.gte,value);
        return this;
    }


    public IConditionWrapper<E> lt(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.lt,value);
        return this;
    }


    public IConditionWrapper<E> lte(LambdaPropertyFunction<E> name, Object value) {
        condition(name,ConditionEnum.lte,value);
        return this;
    }




    public IConditionWrapper<E> between(LambdaPropertyFunction<E> name, Collection value) {

        condition(name,ConditionEnum.between,value);
        return this;
    }


    public IConditionWrapper<E> like(LambdaPropertyFunction<E> name, String value) {
        condition(name,ConditionEnum.like,value);

        return this;
    }

    public IConditionWrapper<E> match(LambdaPropertyFunction<E> name, String value) {
        condition(name,ConditionEnum.match,value);
        return this;
    }





    public IConditionWrapper<E> likeRight(LambdaPropertyFunction<E> name, String value) {
        condition(name,ConditionEnum.likeRight,value);
        return this;
    }


    public IConditionWrapper<E> isNull(LambdaPropertyFunction<E> name) {

        condition(name,ConditionEnum.isNull,1);

        return this;
    }


    public IConditionWrapper<E> in(LambdaPropertyFunction<E> name, Collection<?> coll) {


        condition(name,ConditionEnum.in,coll);

        return this;
    }


    //集合变化处理
    private static <E> Collection<E> encodeCollection(Collection<E> coll){


        return  ListUtil.encodeCollection(coll);
    }


    public IConditionWrapper<E> condition(Object key, ConditionEnum condition, Object arg) {

        if(condition != ConditionEnum.isNull && arg == null){

            return this;
        }else if(arg instanceof String){
            if(arg.equals("")){

                return this;
            }
        }else if(arg instanceof Collection){
            Collection collection = (Collection)arg;
            if(collection.isEmpty()){

                return this;
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
            }case match:{
                node = new Match(name, value.toString());

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
        return this;
    }



    //自定义条件表达式
    public IConditionWrapper<E> expression(String name , Map<String,Object> params){
        return addNode(new Expression(name,params));
    }

    public IConditionWrapper<E> eq(String name, Object value){
        return condition(name,ConditionEnum.eq,value);
    }
    public IConditionWrapper<E> ne(String name, Object value){
        return condition(name,ConditionEnum.ne,value);
    }
    public IConditionWrapper<E> gte(String name, Object value){

        return condition(name,ConditionEnum.gte,value);
    }
    public IConditionWrapper<E> gt(String name, Object value){
        return condition(name,ConditionEnum.gt,value);
    }
    public IConditionWrapper<E> lte(String name, Object value){
        return condition(name,ConditionEnum.lte,value);
    }
    public IConditionWrapper<E> lt(String name, Object value){

        return condition(name,ConditionEnum.lt,value);
    }
    public IConditionWrapper<E> like(String name, String value){

        return condition(name,ConditionEnum.like,value);
    }

    public IConditionWrapper<E> match(String name, String value){
        return condition(name,ConditionEnum.match,value);
    }

    public IConditionWrapper<E> between(String name, Collection value){
        return condition(name,ConditionEnum.between,value);
    }


    public IConditionWrapper<E> likeRight(String name, String value){

        return condition(name,ConditionEnum.likeRight,value);
    }

    public IConditionWrapper<E> isNull(String name)
    {
        return condition(name,ConditionEnum.isNull,null);
    }

    public IConditionWrapper<E> in(String name, Collection<?> coll){
        return condition(name,ConditionEnum.in,coll);
    }

    @SneakyThrows
    protected AbstractConditionWrapper<E> newInstance(){
        AbstractConditionWrapper<E> r = (AbstractConditionWrapper<E>) getClass().getConstructor().newInstance();

        return r;
    }


    @Override
    public IConditionWrapper<E> or(Consumer<IConditionWrapper<E>> consumer) {

        consumer.accept(or());
        return this;
    }

    @Override
    public IConditionWrapper<E> or() {
        AbstractConditionWrapper<E> r = newInstance();
        r.init(or);
        addNode(r.getCondition());
        return r;
    }

    @Override
    public IConditionWrapper<E> and() {
        AbstractConditionWrapper<E> r = newInstance();
        r.init(and);
        addNode(r.getCondition());
        return r;
    }


    @Override
    public IConditionWrapper<E> and(Consumer<IConditionWrapper<E>> consumer) {

        consumer.accept(and());
        return this;
    }

    @Override
    public IConditionWrapper<E> and(IConditionWrapper<?> conditionWrapper) {
        addNode(conditionWrapper.getCondition());
        return this;
    }

    @Override
    public IConditionWrapper<E> not() {
        AbstractConditionWrapper<E> r = newInstance();
        r.init(not);
        addNode(r.getCondition());
        return r;
    }

    @Override
    public IConditionWrapper<E> not(Consumer<IConditionWrapper<E>> consumer) {
        IConditionWrapper<E> r =not();
        consumer.accept(r);
        return this;
    }

    @Override
    public IConditionWrapper<E> struct() {
        ICondition w = where;
        init(and);
        addNode(w);
        return this;
    }






    public ICondition getCondition(){
        return where;
    }



}

package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.util.MetaUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.core.wrapper.IStoreWrapper;
import com.yulinlin.data.core.wrapper.ITableWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.reflect.Field;
import java.util.List;

public abstract class AbstractGroupWrapperFactory implements AbstractWrapperFactory<IGroupWrapper> {


    private void parseTable(IStoreWrapper wrapper, Object obj){


        AbstractSelectWrapperFactory.parseTable(wrapper,obj,true);
    }


    @Override
    public IGroupWrapper create(Object obj) {


        EventUtil.before(obj,EventUtil.group);
        IGroupWrapper wrapper = create();

        parseTable(wrapper,obj);

        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());


        for (Field f : fs){
            String columnName = f.getName();
            JoinField joinField =  AnnotationUtil.findAnnotation(f,JoinField.class);//  f.getAnnotation(JoinField.class);
            if(joinField != null && !joinField.exist()){
                continue;
            }


            JoinMetrics joinFunction =  AnnotationUtil.findAnnotation(f,JoinMetrics.class);//  f.getAnnotation(JoinField.class);
            JoinAggregations joinGroup =  AnnotationUtil.findAnnotation(f,JoinAggregations.class);


            if(joinGroup != null){

                AggregationsEnum function = joinGroup.value();
                int interval = joinGroup.interval();


                if(function == AggregationsEnum.interval){
                    wrapper.aggregations().interval(columnName,f.getName(),interval);
                }else {
                    wrapper.aggregations().field(joinGroup.value(),columnName,f.getName());

                }

            }else if(joinFunction != null){

                MetricsEnum function = joinFunction.value();
                wrapper.metrics().field(function,columnName,f.getName());


            }else {

            }


            JoinWhere joinWhere =
                    AnnotationUtil.findAnnotation(f,JoinWhere.class);
            IConditionWrapper and = (IConditionWrapper)wrapper.where();
            IConditionWrapper or = and.or();

            IConditionWrapper having = (IConditionWrapper) wrapper.having();

            if(joinWhere != null){
                Object value =  ReflectionUtil.invokeGetter(obj,f.getName());
                if(ReflectionUtil.isNotEmpty(value)){
                    IConditionWrapper whereWrapper ;
                    if(joinGroup != null){

                        if(joinWhere.and()){
                            whereWrapper = and;
                        }else{
                            whereWrapper = or;
                        }
                    }else {
                        whereWrapper = having;
                        columnName =f.getName();
                    }


                    JoinMeta joinMeta = AnnotationUtil.findAnnotation(f,JoinMeta.class);
                    if(joinMeta != null){
                        whereWrapper.meta(MetaUtil.toMap(joinMeta));
                    }
                    whereWrapper.condition(columnName,joinWhere.condition(),value);



                }
            }





            JoinOrder joinOrder =
                    AnnotationUtil.findAnnotation(f,JoinOrder.class);
            //f.getAnnotation(JoinOrder.class);
            if(joinOrder != null){
                if(joinOrder.asc()){
                    wrapper.orderByAsc(f.getName());
                }else{
                    wrapper.orderByDesc(f.getName());
                }

            }
        }

        return wrapper;
    }
}

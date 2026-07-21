package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.util.MetaUtil;
import com.yulinlin.data.core.wrapper.*;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractSelectWrapperFactory implements AbstractWrapperFactory<ISelectWrapper> {

    //删除，更新条件
    public static void initSelectWhere(IConditionWrapper where,Object obj){
        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());
        IConditionWrapper or =  where.or();
        IConditionWrapper and = or.and();
        for (Field f : fs) {
            JoinWhere joinWhere =
                    AnnotationUtil.findAnnotation(f,JoinWhere.class);

            if(joinWhere == null){
                continue;
            }
            JoinMeta joinMeta =
                    AnnotationUtil.findAnnotation(f,JoinMeta.class);


            String columnName =  f.getName();

            if(joinWhere != null && !joinWhere.name().isEmpty()){
                columnName = joinWhere.name();
            }
            Object value =  ReflectionUtil.invokeGetter(obj,f.getName());
            if(value == null){
                continue;
            }
            if(joinMeta != null){
                where.meta(MetaUtil.toMap(joinMeta));
            }
            if(joinWhere != null){
                if(joinWhere.and()){
                    and.condition(columnName,joinWhere.condition(),value);

                }else {

                    or.condition(columnName,joinWhere.condition(),value);
                }
            }else {
                where.eq(columnName,value);
            }
        }
    }



    protected static List<String> tables(String sql){
        sql = sql.trim();
        String[] sqls =  sql.split(" ");
        String sqlName=sqls[0];
        String sqlAlias = sqls[sqls.length - 1];

        return Arrays.asList(sqlName,sqlAlias);
    }



    protected static void parseTable(IStoreWrapper wrapper, Object obj,boolean select){

        Class clazz =obj.getClass();
        JoinTable table = AnnotationUtil.findAnnotation(clazz,JoinTable.class);
        JoinTableList tableList =AnnotationUtil.findAnnotation(clazz,JoinTableList.class);


        if(select && tableList != null){
            if( !IJoinWrapper.class.isAssignableFrom(wrapper.getClass())){
                throw new NoticeException("不支持连接查询");
            }
            IJoinWrapper joinWrapper =(IJoinWrapper) wrapper;

            for (JoinTable node: tableList.value()){
                if(node.value().length() > 0){
                    wrapper.table(node.value());
                }else{
                    String sql;

                    if(node.left().length() > 0 && node.right().length() > 0){
                        wrapper.table(node.left());
                        sql = node.right();
                    }else{
                        if(node.left().length() > 0){
                            sql = node.left();
                        } else{
                            sql = node.right();
                        }
                    }


                    List<String> sqls = tables(sql);
                    String sqlName=sqls.get(0);
                    String sqlAlias =sqls.get(1);

                    String on = node.on();
                    if(node.join() == JoinEnum.left){
                        joinWrapper.left(sqlName,sqlAlias).expression(on);
                    }else if(node.join() == JoinEnum.right){
                        joinWrapper.right(sqlName,sqlAlias).expression(on);
                    }else if(node.join() == JoinEnum.inner){
                        joinWrapper.inner(sqlName,sqlAlias).expression(on);
                    }

                }
            }

            return;
        }


            if(table.value().length() > 0){
                wrapper.table(table.value());
            }else{
                if( !IJoinWrapper.class.isAssignableFrom(wrapper.getClass())){
                    throw new NoticeException("不支持连接查询");
                }
                IJoinWrapper joinWrapper = wrapper;
                List<String> left =  tables(table.left());
                List<String> right =  tables(table.right());
                String on = StringUtil.javaToColumn( table.on());
                wrapper.table(left.get(0),left.get(1));

                if(table.join() == JoinEnum.left){
                    joinWrapper.left(right.get(0),right.get(1)).expression(on);
                }else if(table.join() == JoinEnum.right){
                    joinWrapper.right(right.get(0),right.get(1)).expression(on);
                }else if(table.join() == JoinEnum.inner){
                    joinWrapper.inner(right.get(0),right.get(1)).expression(on);
                }


            }

    }




    @Override
    public ISelectWrapper create(Object obj) {
        EventUtil.before(obj,EventUtil.select);
        ISelectWrapper wrapper = create();
        parseTable(wrapper,obj,true);
        initSelectWhere((IConditionWrapper)wrapper.where(),obj);

        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());


        for (Field f : fs){
            JoinField joinField =  AnnotationUtil.findAnnotation(f,JoinField.class);//  f.getAnnotation(JoinField.class);
            JoinMeta joinMeta =  AnnotationUtil.findAnnotation(f,JoinMeta.class);//  f.getAnnotation(JoinField.class);



            if(joinField != null && !joinField.exist()){
                continue;
            }
            String columnName = f.getName();

            if(joinMeta != null){
                wrapper.fields().meta(MetaUtil.toMap(joinMeta));
            }
            if(joinField == null){
                wrapper.fields()
                        .field(columnName,f.getName());
            }else if(joinField.exist()){
                wrapper.fields()
                        .field(columnName,f.getName());
            }

            JoinOrder joinOrder =
                    AnnotationUtil.findAnnotation(f,JoinOrder.class);

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

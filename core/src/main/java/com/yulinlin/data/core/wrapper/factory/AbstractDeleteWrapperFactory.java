package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.util.MetaUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.List;

public abstract class AbstractDeleteWrapperFactory implements AbstractWrapperFactory<IDeleteWrapper> {

    protected static void initUpdateWhere(IConditionWrapper where,Object obj){
        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());
        IConditionWrapper or =  where.or();
        IConditionWrapper and = where;
        for (Field f : fs) {

            JoinMeta joinMeta =
                    AnnotationUtil.findAnnotation(f,JoinMeta.class);
            if(joinMeta == null){
                continue;
            }
            String columnName =  f.getName();
            Object value =  ReflectionUtil.invokeGetter(obj,f.getName());
            if(value == null){
                continue;
            }
            if(joinMeta != null){
                where.meta(MetaUtil.toMap(joinMeta));
            }
            if(joinMeta.primaryKey()){
                JoinWhere joinWhere =
                        AnnotationUtil.findAnnotation(f,JoinWhere.class);
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
    }




    @Override
    public IDeleteWrapper create(Object obj) {
        EventUtil.before(obj,EventUtil.delete);
        IDeleteWrapper wrapper =  create();

        JoinTable table =AnnotationUtil.findAnnotation(obj.getClass(),JoinTable.class);
        wrapper.table(table.value());


        initUpdateWhere((IConditionWrapper)wrapper.where(),obj);



        return wrapper;

    }
}

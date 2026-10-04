package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.UpdateTypeEnum;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.util.MetaUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.reflect.Field;
import java.util.List;

public abstract class AbstractUpdateWrapperFactory implements AbstractWrapperFactory<IUpdateWrapper> {



    @Override
    public IUpdateWrapper create(Object obj) {
        EventUtil.before(obj,EventUtil.update);

        IUpdateWrapper wrapper = create();


        JoinTable table =AnnotationUtil.findAnnotation(obj.getClass(),JoinTable.class);


        if(table != null){
            wrapper.table(table.value());
        }else {
            throw new NoticeException("缺少JoinTable注解");
        }



        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());

        for (Field f : fs){
            JoinField joinField =   AnnotationUtil.findAnnotation(f,JoinField.class);// f.getAnnotation(JoinField.class);

            if(joinField == null){

            }else if( !joinField.exist() || !joinField.update()){
                continue;
            }

            String columnName =  f.getName();

            if(joinField != null && StringUtil.isNotNull(joinField.name())){
                columnName = joinField.name();
            }

            Object value =  ReflectionUtil.invokeGetter(obj,f.getName());

            if(value == null){
                continue;
            }

            JoinMeta joinMeta =
                    AnnotationUtil.findAnnotation(f,JoinMeta.class);

            if(joinMeta != null){
                wrapper.fields().meta(MetaUtil.toMap(joinMeta));
            }
            if(joinField == null){
                wrapper.fields().field(columnName,value);
            }else  if(joinField.version()){
                wrapper.fields().inc(columnName,1);
                ((IConditionWrapper) wrapper.where()).eq(columnName,value);
            }

            else {

                if(joinMeta != null && joinMeta.primaryKey()){
                    ((IConditionWrapper) wrapper.where()).eq(columnName,value);
                } else {
                    UpdateTypeEnum updateTypeEnum = joinField.updateType();
                    if(updateTypeEnum == UpdateTypeEnum.set){
                        wrapper.fields().field(columnName,value);
                    }else if(updateTypeEnum == UpdateTypeEnum.inc){
                        wrapper.fields().inc(columnName,(Number) value);
                    }else if(updateTypeEnum == UpdateTypeEnum.dec){
                        wrapper.fields().dec(columnName,(Number) value);
                    }

                }

            }



        }

        return wrapper;

    }
}

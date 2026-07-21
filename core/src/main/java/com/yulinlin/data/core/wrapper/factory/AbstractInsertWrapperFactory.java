package com.yulinlin.data.core.wrapper.factory;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.util.MetaUtil;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.core.wrapper.IWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.List;

public abstract class AbstractInsertWrapperFactory implements AbstractWrapperFactory<IInsertWrapper> {

    protected void before(Object obj){
        if(obj instanceof BaseModel){
            ((BaseModel) obj).insertBefore();
        }
    }


    @Override
    public IInsertWrapper create(Object obj) {


        EventUtil.before(obj,EventUtil.insert);

        IInsertWrapper wrapper = create();

        JoinTable table = AnnotationUtil.findAnnotation(obj.getClass(),JoinTable.class);

        if(table == null){
            throw new NoticeException("缺少注解："+JoinTable.class);
        }



        wrapper.table(table.value());


        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(obj.getClass());

        for (Field f : fs){
            JoinField joinField = AnnotationUtil.findAnnotation(f,JoinField.class);//   f.getAnnotation(JoinField.class);
            if(joinField != null && !joinField.exist()){
                continue;
            }
            JoinMeta joinMeta = AnnotationUtil.findAnnotation(f,JoinMeta.class);
            String columnName =  f.getName();
            Object value =  ReflectionUtil.invokeGetter(obj,f.getName());
            if(value == null){
                continue;
            }

            if(joinMeta != null){
                wrapper.fields().meta(MetaUtil.toMap(joinMeta));
            }

            wrapper.fields().field(columnName,value);

        }

        return wrapper;

    }
}

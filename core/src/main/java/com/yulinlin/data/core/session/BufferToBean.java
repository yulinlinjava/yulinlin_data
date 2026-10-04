/*
package com.yulinlin.data.core.session;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BufferToBean {

    private ICoderManager coderManager;

    public BufferToBean(ICoderManager coderManager) {
        this.coderManager = coderManager;
    }

    public ICoder getCoder(Class clazz){
        return coderManager.getCoder(clazz);
    }

    @SneakyThrows
    public Object createBean(IDataBuffer buffer, Class clazz){
        if (clazz == null) {
            Map map = new HashMap<>();
            for (String columnName : buffer.keys()) {
                Object value = buffer.getObject(columnName);
                map.put(columnName,value);
            }
            return map;
        }
        try {
            Object obj = ReflectionUtil.newInstance(clazz);
            for (Field field : ReflectionUtil.getAllDeclaredFields(clazz)) {
                String name =field.getName();
                Object value = buffer.getObject(field.getName());
                if(value != null){
                    ICoder coder =  coderManager.getCoder(field.getType());
                    value  = coder.decode(buffer, field);
                    ReflectionUtil.invokeSetter(obj, name, value);
                }

            }

            return obj;

        }catch (Exception e){
            throw new NoticeException("请检查字段类型,对象构造异常",e);
        }






    }
    @SneakyThrows
    public List createBean(List<IDataBuffer> buffers, Class clazz){
        return buffers.stream().map(row -> createBean(row,clazz)).collect(Collectors.toList());
    }
}
*/

package com.yulinlin.data.core.coder;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.lang.reflection.GenericUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.List;

public interface ICoder<K,V> {

    //value 映射为V 编码
    V encode(IDataBuffer buffer, String key,K value);

    /**
     * 解码
     * @return
     */
     K decode(IDataBuffer buffer , Field field, Object value) ;


    default K decode(IDataBuffer buffer , Class field, Object value){
        throw new NoticeException("方法需覆盖");
    }

    default    boolean check(Class clazz){
        return getTypeClass() == clazz;
    }

    default Class getTypeClass() {
        return GenericUtil.getGeneric(this.getClass(), ICoder.class, 0);
    }

    //优先级
    default int priority(){
        return 9;
    }

}

package com.yulinlin.common.util;

import com.yulinlin.common.model.ModelGroupWrapper;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import lombok.Data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Data
@JoinTable("${table}")
public class MaxNumberUtil {

    @JoinField(exist = false)
    private String table;

    @JoinField(exist = false)
    private String name="id";

    @JoinField(name = "${name}")
    @JoinMetrics(MetricsEnum.max)
    private AtomicLong value;


    static ConcurrentHashMap<String,AtomicLong> cache = new ConcurrentHashMap() ;

    public static AtomicLong of(Class  clazz){
        JoinTable annotation = AnnotationUtil.findAnnotation(clazz, JoinTable.class);
        return of(annotation.value(),"id");
    }

    public static AtomicLong of(String  table){
        return of(table,"id");
    }
    public static AtomicLong of(String  table,String column){
        return   cache.computeIfAbsent(table,(k) -> {

            MaxNumberUtil nIdUtil = new MaxNumberUtil();
            nIdUtil.setName(column);
            nIdUtil.setTable(table);

            MaxNumberUtil vv = ModelGroupWrapper.newInstance(nIdUtil).selectOne();

            AtomicLong value1 = vv.getValue();
            if(value1 == null){
                value1 = new AtomicLong();
            }

            return value1;
        });


    }
}

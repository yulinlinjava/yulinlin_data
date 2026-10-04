package com.yulinlin.admin;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.MetricsEnum;
import lombok.Data;

import java.util.concurrent.atomic.LongAdder;

@Data
@JoinTable("${table}")
public class IdUtil {

    @JoinField(exist = false)
    private String table;

    @JoinField(exist = false)
    private String name="id";


    @JoinMetrics(MetricsEnum.max)
    private LongAdder value;

}

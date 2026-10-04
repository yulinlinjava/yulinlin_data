package com.yulinlin.admin;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.MetricsEnum;
import lombok.Data;

@Data
@JoinTable( "sys_user a")
public class CountTest {

    @JoinMetrics(MetricsEnum.count)
    private Integer count;


}

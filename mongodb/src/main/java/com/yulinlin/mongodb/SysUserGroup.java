package com.yulinlin.mongodb;

import com.yulinlin.data.core.anno.*;
import lombok.Data;

import java.util.List;


@Data

@JoinTable("sys_user")
public class SysUserGroup {

        @JoinAggregations
        @JoinField
        private String username;

/*        @JoinAggregations(AggregationsEnum.minute)
        @JoinField
        private String crtTime;*/

        @JoinField(name = "money")
        @JoinMetrics(MetricsEnum.sum)
        private int total;



}

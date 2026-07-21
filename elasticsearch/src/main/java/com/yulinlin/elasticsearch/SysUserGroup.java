package com.yulinlin.elasticsearch;


import com.yulinlin.data.core.anno.*;
import lombok.Data;

@JoinTable("employee")
@Data
public class SysUserGroup {


    //聚合指标
    @JoinField(name = "age")
    @JoinMetrics(MetricsEnum.sum)
    @JoinWhere(condition = ConditionEnum.gte)
    private Integer age;

    //聚合维度
    @JoinField(name = "job.keyword")
    @JoinAggregations()
    private String job;
    //聚合维度
    @JoinField(name = "gender.keyword")
    @JoinAggregations()
    private String gender;


}

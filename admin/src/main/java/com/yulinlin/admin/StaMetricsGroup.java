package com.yulinlin.admin;

import com.yulinlin.data.core.anno.*;
import lombok.Data;

@Data
@JoinTable("sta_metrics")
public class StaMetricsGroup {

    @JoinField
    @JoinAggregations
    private String name;
    @JoinField
    @JoinAggregations(AggregationsEnum.day)
    private String crtTime;
    @JoinField
    @JoinMetrics(MetricsEnum.sum)
    private Integer metrics;

}

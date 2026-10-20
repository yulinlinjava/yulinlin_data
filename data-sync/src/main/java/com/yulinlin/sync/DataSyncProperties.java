package com.yulinlin.sync;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties("yulinlin.sync")
public class DataSyncProperties {
    private boolean enabled;
    /** 每条规则最多保留的写节点数，包含正在执行和失败的任务。 */
    private int capacity = 10000;
    /** 全量同步每批数据条数，不预先 COUNT 全表。 */
    private int fullBatchSize = 1000;
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        /** 可省略，默认 source->target；同一源/目标只配置一条规则。 */
        private String name;
        private String source;
        private String target;
        private List<String> entities = new ArrayList<>();
    }
}

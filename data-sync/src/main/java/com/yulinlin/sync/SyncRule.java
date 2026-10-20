package com.yulinlin.sync;

import java.util.List;

/** 注册时固定配置，运行中修改配置 Bean 不会改变已有规则。 */
public record SyncRule(String name, String source, String target, List<String> entities) {
    public SyncRule {
        if (source == null || source.isBlank() || target == null || target.isBlank())
            throw new IllegalArgumentException("同步规则必须指定 source 和 target");
        source = source.trim();
        target = target.trim();
        if (source.equals(target)) throw new IllegalArgumentException("同步源和目标不能相同：" + source);
        name = name == null || name.isBlank() ? source + "->" + target : name.trim();
        if (entities == null || entities.isEmpty()) throw new IllegalArgumentException("同步规则 entities 不能为空：" + name);
        entities = List.copyOf(entities);
    }
}

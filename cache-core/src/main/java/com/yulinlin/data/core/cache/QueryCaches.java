package com.yulinlin.data.core.cache;

import java.util.List;

/** 无显式默认配置的手动装配兼容入口；Spring 装配统一使用 CacheRegistry。 */
public final class QueryCaches {

    private QueryCaches() {
    }

    public static QueryCache single(List<QueryCache> providers) {
        return new CacheRegistry(providers, null).defaultCache();
    }
}

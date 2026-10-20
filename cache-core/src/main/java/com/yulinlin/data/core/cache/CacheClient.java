package com.yulinlin.data.core.cache;

import java.util.Map;
import java.util.Set;

/** 缓存管理入口：仅选择和查看缓存，业务数据操作由 Cache 提供。 */
public interface CacheClient {
    /** 单实现自动选择，多实现使用配置默认值；未引入实现时返回空缓存。 */
    Cache getCache();
    /** 获取指定缓存；名称不存在时抛出异常。 */
    Cache getCache(String name);
    /** 默认缓存名称；未注册实现时返回 null。 */
    String defaultName();
    boolean contains(String name);
    /** 已注册名称的只读视图，不包含兜底空缓存。 */
    Set<String> names();
    /** 已注册缓存的只读视图，不包含兜底空缓存。 */
    Map<String, Cache> caches();
}

package com.yulinlin.mcp;

/** 可选功能（如 SQL 工具）参与模块启动校验和定时清理，不需要 McpAutoConfiguration 直接依赖其实现类。 */
public interface McpLifecycle {
    /** 启动时在存储初始化之后调用；配置错误应直接抛异常使启动失败。 */
    default void initialize() { }

    /** 每小时由 McpMaintenance 调用一次。 */
    default void clean() { }
}

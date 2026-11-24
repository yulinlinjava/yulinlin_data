package com.yulinlin.mcp.data;

import java.util.*;

/**
 * 一次调用内的数据访问能力，供 mcp-script 的沙箱脚本使用。
 * command：JDBC 数据源是一条 SQL；Elasticsearch 是 JSON 命令（字符串）。
 * 写入时整个调用是一个框架事务：读 JDBC 数据源会登记到这个事务（能看到本次写入），Elasticsearch 没有事务，直接执行。
 */
public interface McpDataScope {
    /** 查询，返回行列表。超出本次调用的行数预算时抛异常，而不是静默截断。 */
    List<Map<String, Object>> query(String group, String command, Map<String, Object> params);

    /** 查询一行；没有结果返回 null，多于一行抛异常。 */
    Map<String, Object> queryOne(String group, String command, Map<String, Object> params);

    /** 写入，group 必须是本次调用声明的 writeGroup（JDBC）；返回影响行数。 */
    long execute(String group, String sql, Map<String, Object> params);
}

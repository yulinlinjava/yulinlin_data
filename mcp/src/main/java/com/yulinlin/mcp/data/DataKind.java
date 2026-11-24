package com.yulinlin.mcp.data;

import java.util.Locale;

/**
 * 数据源类型。按会话类的继承链上的类名识别，不直接引用 JDBC / Elasticsearch 的类，
 * 所以只引入其中一部分模块的应用也能加载本类。
 */
public enum DataKind {
    JDBC("com.yulinlin.jdbc.session.JdbcSession"),
    ELASTICSEARCH("com.yulinlin.elasticsearch.session.ElasticsearchSession");

    private final String sessionClass;

    DataKind(String sessionClass) { this.sessionClass = sessionClass; }

    /** 返回给 AI 的类型名：jdbc、elasticsearch。 */
    public String label() { return name().toLowerCase(Locale.ROOT); }

    /** 不支持的会话类型返回 null。 */
    public static DataKind of(Object session) {
        if (session == null) return null;
        for (Class<?> c = session.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (DataKind kind : values()) if (kind.sessionClass.equals(c.getName())) return kind;
        return null;
    }
}

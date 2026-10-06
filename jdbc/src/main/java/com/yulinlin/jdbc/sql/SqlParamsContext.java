package com.yulinlin.jdbc.sql;

import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.sql.parse.NameParse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Request-local parser and SELECT expressions, never shared between requests or threads. */
public final class SqlParamsContext implements IParamsContext {
    private final IParamsContext delegate;
    private final SqlParseManager parseManager;
    private final NameParse nameParse;
    private AliasContent aliases;
    private Map<String, String> expressions;
    private Map<String, Object> attributes;

    public SqlParamsContext(IParamsContext delegate, SqlParseManager parseManager) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.parseManager = java.util.Objects.requireNonNull(parseManager, "parseManager");
        this.nameParse = parseManager.nameParse();
    }
    public SqlParseManager parseManager() { return parseManager; }
    public NameParse nameParse() { return nameParse; }
    public static NameParse nameParse(IParamsContext params) {
        return params instanceof SqlParamsContext sql ? sql.nameParse : Defaults.MANAGER.nameParse();
    }
    public static SqlParseManager parseManager(IParamsContext params) {
        return params instanceof SqlParamsContext sql ? sql.parseManager : Defaults.MANAGER;
    }
    // Preserve standalone utility calls without constructing a session or touching connections.
    private static class Defaults {
        static final SqlParseManager MANAGER = new SqlParseManager();
    }
    public void selectExpression(String alias, String expression) {
        if (expressions == null) expressions = new HashMap<>();
        expressions.put(alias, expression);
    }
    public String selectExpression(String alias) { return expressions == null ? null : expressions.get(alias); }
    public Object attribute(String key) { return attributes == null ? null : attributes.get(key); }
    public void attribute(String key, Object value) {
        if (attributes == null) attributes = new HashMap<>();
        attributes.put(key, value);
    }
    @SuppressWarnings("unchecked")
    public <T> T computeAttribute(String key, Supplier<T> supplier) {
        Object current = attribute(key);
        if (current != null) return (T) current;
        T value = supplier.get();
        attribute(key, value);
        return value;
    }
    @Override public String putGetKey(Object value) { return delegate.putGetKey(value); }
    @Override public void put(String key, Object value) { delegate.put(key, value); }
    @Override public IDataBuffer getDataBuffer() { return delegate.getDataBuffer(); }
    @Override public AliasContent getAliasContent() {
        if (aliases == null) aliases = new AliasContent();
        return aliases;
    }
    @Override public String toColumn(String name) {
        String local = aliases == null ? name : aliases.toColumn(name);
        return !local.equals(name) ? local : delegate.toColumn(name);
    }
    @Override public String toKey(String name) {
        String local = aliases == null ? name : aliases.toKey(name);
        return !local.equals(name) ? local : delegate.toKey(name);
    }
    @Override public RequestType getRequestType() { return delegate.getRequestType(); }
    @Override public Object getRoot() { return delegate.getRoot(); }
    @Override public Class<?> getSourceClass() { return delegate.getSourceClass(); }
    @Override public Object parse(String path) { return delegate.parse(path); }
}

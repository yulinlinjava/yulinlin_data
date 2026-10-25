package com.yulinlin.data.core.request;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.from.From;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, query-local index candidates bound to one physical table reference. */
public record QueryIndexHint(String table, String alias, List<String> indexes) {
    private static final String IDENTIFIER = "[\\p{L}_][\\p{L}\\p{N}_$]*";
    private static final Pattern INDEX = Pattern.compile(IDENTIFIER);
    private static final Pattern TABLE = Pattern.compile(IDENTIFIER + "(?:\\." + IDENTIFIER + ")*");

    public QueryIndexHint {
        if (table == null || !TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("useIndex 仅支持明确的单表标识符，不支持模板表名或 SQL 片段");
        }
        if (indexes == null || indexes.isEmpty()) {
            throw new IllegalArgumentException("useIndex 至少需要一个索引名");
        }
        for (String index : indexes) {
            if (index == null || !INDEX.matcher(index).matches()) {
                throw new IllegalArgumentException("useIndex 索引名必须是单个标识符，不能包含空白、引号、注释或 SQL 片段");
            }
        }
        indexes = List.copyOf(new LinkedHashSet<>(indexes));
    }

    public static QueryIndexHint of(INode node, String... indexes) {
        Store store = singleTable(node);
        return new QueryIndexHint(store.getName(), store.getAlias(),
                indexes == null ? null : Arrays.asList(indexes));
    }

    /** Never transfer a physical-table hint onto a replacement query's other table/alias. */
    public Store target(INode node) {
        Store store = singleTable(node);
        if (!Objects.equals(table, store.getName()) || !Objects.equals(alias, store.getAlias())) {
            throw new IllegalArgumentException("useIndex 的目标表或别名已改变，请在新查询上明确指定索引提示");
        }
        return store;
    }

    private static Store singleTable(INode node) {
        while (node instanceof CountWrapper count) node = count.getWrapper();
        From from;
        if (node != null && node.getClass() == SelectWrapper.class) {
            from = ((SelectWrapper<?>) node).getFrom();
        } else if (node != null && node.getClass() == GroupWrapper.class) {
            from = ((GroupWrapper<?>) node).getFrom();
        } else {
            throw new UnsupportedOperationException("useIndex 仅支持普通单表 SELECT/GROUP 查询及其 count，不支持原生 SQL 或自定义 Wrapper");
        }
        if (!(from instanceof Store store)) {
            throw new UnsupportedOperationException("useIndex 需要明确的单表，不支持 JOIN、子查询或缺失表名");
        }
        return store;
    }
}

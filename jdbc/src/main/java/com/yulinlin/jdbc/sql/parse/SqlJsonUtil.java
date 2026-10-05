package com.yulinlin.jdbc.sql.parse;

import com.yulinlin.data.core.wrapper.IChildrenWrapper;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

public class SqlJsonUtil {
    private static final ThreadLocal<LinkedList<IChildrenWrapper>> threadLocal =
            ThreadLocal.withInitial(LinkedList::new);
    public static String symbol = "->";

    public static void push(IChildrenWrapper wrapper) { threadLocal.get().add(wrapper); }
    public static void pop() {
        LinkedList<IChildrenWrapper> stack = threadLocal.get();
        stack.removeLast();
        if (stack.isEmpty()) threadLocal.remove();
    }
    public static IChildrenWrapper get() { return threadLocal.get().getLast(); }
    public static boolean isEmpty() { return threadLocal.get().isEmpty(); }
    public static int size() { return threadLocal.get().size(); }

    private static String buildContext() {
        StringJoiner joiner = new StringJoiner(symbol);
        for (IChildrenWrapper wrapper : threadLocal.get()) {
            if (wrapper.getName() != null && !wrapper.getName().isEmpty()) joiner.add(wrapper.getName());
        }
        return joiner.toString();
    }

    public static List<String> json_path(String path) {
        String[] parts = path.split(symbol, -1);
        StringJoiner jsonPath = new StringJoiner(".");
        jsonPath.add("$");
        for (int i = 1; i < parts.length; i++) jsonPath.add(parts[i]);
        return Arrays.asList(parts[0], jsonPath.toString());
    }

    public static String resolve(String column, NameParse names, Object comparisonValue) {
        String prefix = buildContext();
        String path = prefix.isEmpty() ? column : prefix + symbol + column;
        String[] parts = path.split(symbol, -1);
        if (parts.length == 1) return names.reference(column);
        for (String part : parts) {
            if (part.isEmpty()) throw new IllegalArgumentException("JSON path must not contain an empty segment");
        }
        return names.jsonExtract(names.reference(parts[0]),
                Arrays.asList(parts).subList(1, parts.length), comparisonValue);
    }

    public static String json_where(String path) { return resolve(path, SqlParamsContext.nameParse(null), null); }
}

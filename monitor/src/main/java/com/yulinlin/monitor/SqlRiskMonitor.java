package com.yulinlin.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Separate bounded risk history: rejected SQL never inflates physical execution statistics. */
public final class SqlRiskMonitor {
    private final MonitorProperties properties;
    private final ArrayDeque<SqlRiskRecord> records = new ArrayDeque<>();

    public SqlRiskMonitor(MonitorProperties properties) { this.properties = properties; }

    synchronized void record(String group, String sql, boolean blocked, boolean inspectionFailed, List<String> reasons) {
        var safeReasons = reasons.stream().limit(20).map(reason -> truncate(reason, 1024)).toList();
        records.addLast(new SqlRiskRecord(System.currentTimeMillis(), group, truncate(sql, 4096),
                blocked, inspectionFailed, safeReasons));
        while (records.size() > properties.getMaxRecords()) records.removeFirst();
    }

    public synchronized List<SqlRiskRecord> recent(int limit) {
        int count = Math.max(1, Math.min(limit, properties.getMaxRecords()));
        var result = new ArrayList<SqlRiskRecord>();
        var iterator = records.descendingIterator();
        while (iterator.hasNext() && result.size() < count) result.add(iterator.next());
        return List.copyOf(result);
    }

    public synchronized void clear() { records.clear(); }

    private static String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }
}

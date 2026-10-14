package com.yulinlin.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Small, bounded in-memory SQL aggregation and slow-operation store. */
public final class DefaultDatabaseMonitor implements DatabaseMonitor {
    private final MonitorProperties properties;
    private final ArrayDeque<DatabaseOperation> records = new ArrayDeque<>();
    /** Access-order map: maxRecords also bounds distinct SQL templates without adding another setting. */
    private final LinkedHashMap<SummaryKey, SummaryAccumulator> summaries =
            new LinkedHashMap<>(16, 0.75f, true);
    private final ReentrantLock lock = new ReentrantLock();

    public DefaultDatabaseMonitor(MonitorProperties properties) {
        this.properties = properties;
    }

    @Override
    public void record(DatabaseOperation operation) {
        if (operation == null) return;
        long thresholdMs = properties.getThreshold().toMillis();

        lock.lock();
        try {
            SummaryKey key = SummaryKey.of(operation);
            SummaryAccumulator accumulator = summaries.get(key);
            if (accumulator == null) {
                while (summaries.size() >= properties.getMaxRecords()) {
                    var iterator = summaries.entrySet().iterator();
                    if (!iterator.hasNext()) break;
                    iterator.next();
                    iterator.remove();
                }
                accumulator = new SummaryAccumulator(operation);
                summaries.put(key, accumulator);
            }
            accumulator.add(operation, thresholdMs);

            if (operation.success() && operation.elapsedMs() < thresholdMs) return;
            while (records.size() >= properties.getMaxRecords()) records.removeFirst();
            records.addLast(operation);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<DatabaseOperation> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, properties.getMaxRecords()));
        lock.lock();
        try {
            ArrayList<DatabaseOperation> result = new ArrayList<>(Math.min(safeLimit, records.size()));
            var iterator = records.descendingIterator();
            while (iterator.hasNext() && result.size() < safeLimit) result.add(iterator.next());
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<DatabaseSummary> summary() {
        lock.lock();
        try {
            long thresholdMs = properties.getThreshold().toMillis();
            return summaries.values().stream()
                    .map(summary -> summary.finish(thresholdMs))
                    .sorted(Comparator.comparingInt((DatabaseSummary summary) -> priorityRank(summary.priority()))
                            .thenComparing(Comparator.comparingLong(DatabaseSummary::errorCount).reversed())
                            .thenComparing(Comparator.comparingLong(DatabaseSummary::slowCount).reversed())
                            .thenComparing(Comparator.comparingDouble(DatabaseSummary::slowRate).reversed())
                            .thenComparing(Comparator.comparingLong(DatabaseSummary::averageElapsedMs).reversed())
                            .thenComparing(Comparator.comparingLong(DatabaseSummary::maxElapsedMs).reversed()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DatabaseMonitorSnapshot snapshot(int limit) {
        List<DatabaseOperation> recent = recent(limit);
        return new DatabaseMonitorSnapshot(properties.getThreshold().toMillis(), properties.getMaxRecords(),
                size(), summary(), recent);
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            records.clear();
            summaries.clear();
        } finally {
            lock.unlock();
        }
    }

    private int size() {
        lock.lock();
        try {
            return records.size();
        } finally {
            lock.unlock();
        }
    }

    private static int priorityRank(String priority) {
        return switch (priority) {
            case "P0" -> 0;
            case "P1" -> 1;
            case "P2" -> 2;
            case "P3" -> 3;
            case "P4" -> 4;
            default -> 5;
        };
    }

    private record SummaryKey(String group, String session, String operation, String source, String sql) {
        private static SummaryKey of(DatabaseOperation operation) {
            return new SummaryKey(operation.group(), operation.session(), operation.operation(),
                    operation.source(), operation.sql());
        }
    }

    private static final class SummaryAccumulator {
        private final String group;
        private final String session;
        private final String operation;
        private final String entity;
        private final String source;
        private final String table;
        private final String sql;
        private long executions;
        private long slow;
        private long errors;
        private long totalElapsed;
        private long timed;
        private long maxElapsed;
        private long lastElapsed;
        private long lastExecutedAt;

        private SummaryAccumulator(DatabaseOperation operation) {
            this.group = operation.group();
            this.session = operation.session();
            this.operation = operation.operation();
            this.entity = operation.entity();
            this.source = operation.source();
            this.table = operation.table();
            this.sql = operation.sql();
        }

        private void add(DatabaseOperation operation, long thresholdMs) {
            executions++;
            if (operation.elapsedMs() >= thresholdMs) slow++;
            if (!operation.success()) errors++;
            if (operation.elapsedMs() >= 0) {
                totalElapsed += operation.elapsedMs();
                timed++;
                maxElapsed = Math.max(maxElapsed, operation.elapsedMs());
            }
            lastElapsed = operation.elapsedMs();
            lastExecutedAt = operation.timestamp();
        }

        private DatabaseSummary finish(long thresholdMs) {
            long average = timed == 0 ? 0 : totalElapsed / timed;
            double slowRate = executions == 0 ? 0 : slow * 100.0 / executions;
            return new DatabaseSummary(group, session, operation, entity, source, table, sql,
                    priority(errors, executions, average, maxElapsed, slowRate, thresholdMs),
                    executions, slow, errors,
                    slowRate, average, maxElapsed, lastElapsed, lastExecutedAt);
        }

        private static String priority(long errors, long executions, long average, long maximum,
                                       double slowRate, long thresholdMs) {
            if (errors > 0) return "P0";
            long threshold = Math.max(1, thresholdMs);
            boolean enoughRateSamples = executions >= 10;
            if (average >= threshold * 3 || maximum >= threshold * 10) return "P1";
            if (average >= threshold * 2 || maximum >= threshold * 5
                    || enoughRateSamples && slowRate >= 50) return "P2";
            if (average >= threshold || maximum >= threshold * 2
                    || enoughRateSamples && slowRate >= 10) return "P3";
            if (maximum >= threshold) return "P4";
            return "NORMAL";
        }
    }
}

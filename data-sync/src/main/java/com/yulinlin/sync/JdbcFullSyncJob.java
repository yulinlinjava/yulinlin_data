package com.yulinlin.sync;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.jdbc.session.JdbcSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/** 独立维护连接：业务行和检查点使用同一个目标连接/事务，不借用多连接批处理。 */
final class JdbcFullSyncJob {
    static final String PROGRESS = "yulinlin_sync_progress";
    private static final Logger log = LoggerFactory.getLogger(JdbcFullSyncJob.class);
    private record Progress(String taskId, String mapping, String cursor, long rows, String status) { }
    private final RouteSession route;
    private final JdbcSession source;
    private final JdbcSession target;
    private final Class<?> entity;
    private final int batchSize;
    private final SyncManager.ManualGuard guard;
    private final boolean restart;
    private final long started = System.nanoTime();
    private long copied;
    private long batches;

    JdbcFullSyncJob(RouteSession route, JdbcSession source, JdbcSession target, Class<?> entity,
                   int batchSize, SyncManager.ManualGuard guard, boolean restart) {
        this.route = route;
        this.source = source;
        this.target = target;
        this.entity = entity;
        this.batchSize = batchSize;
        this.guard = guard;
        this.restart = restart;
    }

    SyncResult run(Connection input, Connection output) throws Exception {
        input.setAutoCommit(true);
        output.setAutoCommit(true);
        FullSyncMapping from = new FullSyncMapping(input, source, entity);
        FullSyncMapping to = new FullSyncMapping(output, target, entity);
        if (identity(input, from).equals(identity(output, to)))
            throw new IllegalArgumentException("源和目标实际指向同一数据库表，不能进行全量同步");
        String key = hash(source.group() + "\0" + target.group() + "\0" + entity.getName());
        String signature = hash(identity(input, from) + "\0" + identity(output, to)
                + from.primarySignature + "/" + to.primarySignature + from.columns + to.columns + from.fields.stream()
                .map(field -> field.getName() + ":" + field.getGenericType().getTypeName()).toList());
        String progressTable = FullSyncMapping.quote(to.dialect, PROGRESS);
        String ddl = "CREATE TABLE IF NOT EXISTS " + progressTable + " ("
                + "task_key VARCHAR(64) PRIMARY KEY, task_id VARCHAR(36) NOT NULL,"
                + "source_group VARCHAR(255) NOT NULL, target_group VARCHAR(255) NOT NULL,"
                + "entity_name VARCHAR(1024) NOT NULL, mapping_hash VARCHAR(64) NOT NULL,"
                + "last_key TEXT, completed_rows BIGINT NOT NULL, status VARCHAR(16) NOT NULL,"
                + "last_error TEXT, updated_at BIGINT NOT NULL)"
                + (to.dialect == FullSyncMapping.Dialect.MYSQL ? " ENGINE=InnoDB" : "");
        log.info("全量同步检查进度表 目标={} 表={}", target.group(), PROGRESS);
        try (Statement statement = output.createStatement()) { statement.executeUpdate(ddl); }
        requireTransactionalTable(output, to.table.catalog(), to.table.name(), to.dialect);
        requireTransactionalTable(output, output.getCatalog(), PROGRESS, to.dialect);
        try (DatabaseLock ignored = new DatabaseLock(output, to.dialect, key)) {
            Progress progress = load(output, progressTable, key);
            if (progress != null && !restart && !progress.mapping.equals(signature))
                throw new IllegalStateException("同步映射或实际数据库已变化，不能复用旧主键进度；核对后调用 restart 重新全量");
            if (progress != null && !restart && progress.status.equals("RESTART_REQUIRED"))
                throw new IllegalStateException("上次全量同步期间发生源写入，不能续传；请停写并排空增量规则后调用 restart");
            // 包括已完成任务也再次失效，修复“提交成功、清缓存失败”后重试的场景。
            route.invalidateMaintenanceCache(target, entity);
            if (progress != null && !restart && progress.status.equals("COMPLETED")) {
                log.info("全量同步已完成，无需重复执行 源={} 目标={} 实体={} 累计完成={}",
                        source.group(), target.group(), entity.getName(), progress.rows);
                return result(progress);
            }
            if (progress == null || restart) {
                String taskId = UUID.randomUUID().toString();
                output.setAutoCommit(false);
                if (progress == null) {
                    execute(output, "INSERT INTO " + progressTable
                                    + " (task_key,task_id,source_group,target_group,entity_name,mapping_hash,"
                                    + "last_key,completed_rows,status,last_error,updated_at) VALUES (?,?,?,?,?,?,NULL,0,'RUNNING',NULL,?)",
                            key, taskId, source.group(), target.group(), entity.getName(), signature, System.currentTimeMillis());
                } else {
                    execute(output, "UPDATE " + progressTable
                                    + " SET task_id=?,mapping_hash=?,last_key=NULL,completed_rows=0,status='RUNNING',last_error=NULL,updated_at=? WHERE task_key=?",
                            taskId, signature, System.currentTimeMillis(), key);
                }
                output.commit();
                progress = new Progress(taskId, signature, null, 0, "RUNNING");
            }
            log.info("全量同步开始 源={} 目标={} 实体={} 任务={} 每批={} 恢复主键={} 累计完成={}",
                    source.group(), target.group(), entity.getName(), progress.taskId, batchSize, progress.cursor, progress.rows);
            try {
                output.setAutoCommit(false);
                execute(output, "UPDATE " + progressTable + " SET status='RUNNING',last_error=NULL,updated_at=GREATEST(updated_at+1,?) WHERE task_key=?",
                        System.currentTimeMillis(), key);
                output.commit();
                while (true) {
                    guard.check();
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("全量同步被中断");
                    long batchStarted = System.nanoTime();
                    List<Object[]> rows = read(input, from, progress.cursor);
                    guard.check();
                    if (rows.isEmpty()) {
                        execute(output, "UPDATE " + progressTable + " SET status='COMPLETED',last_error=NULL,updated_at=? WHERE task_key=?",
                                System.currentTimeMillis(), key);
                        output.commit();
                        progress = new Progress(progress.taskId, signature, progress.cursor, progress.rows, "COMPLETED");
                        log.info("全量同步完成 源={} 目标={} 实体={} 本次完成={} 累计完成={} 批次={} 总耗时={}ms",
                                source.group(), target.group(), entity.getName(), copied, progress.rows, batches, elapsed(started));
                        return result(progress);
                    }
                    write(output, to, rows);
                    guard.check();
                    String cursor = rows.getLast()[0].toString();
                    if (Objects.equals(cursor, progress.cursor))
                        throw new IllegalStateException("主键游标没有推进，请核对主键类型和排序规则");
                    long total = Math.addExact(progress.rows, rows.size());
                    execute(output, "UPDATE " + progressTable
                                    + " SET last_key=?,completed_rows=?,status='RUNNING',last_error=NULL,updated_at=? WHERE task_key=?",
                            cursor, total, System.currentTimeMillis(), key);
                    output.commit(); // 业务数据 + 进度唯一提交点。
                    progress = new Progress(progress.taskId, signature, cursor, total, "RUNNING");
                    copied += rows.size();
                    batches++;
                    route.invalidateMaintenanceCache(target, entity);
                    log.info("全量同步进度 源={} 目标={} 实体={} 本批={} 本次完成={} 累计完成={} 最后成功主键={} 本批耗时={}ms",
                            source.group(), target.group(), entity.getName(), rows.size(), copied, total, cursor, elapsed(batchStarted));
                }
            } catch (Exception error) {
                try {
                    output.rollback();
                    // 不覆盖 last_key / completed_rows，提交确认异常时以数据库中实际已提交的检查点为准。
                    execute(output, "UPDATE " + progressTable + " SET status=?,last_error=?,updated_at=? WHERE task_key=?",
                            guard.conflicted() ? "RESTART_REQUIRED" : "FAILED",
                            abbreviate(error.toString()), System.currentTimeMillis(), key);
                    output.commit();
                } catch (Exception recordError) { error.addSuppressed(recordError); }
                log.error("全量同步失败 源={} 目标={} 实体={} 本次已确认={}；进度以目标进度表为准，排查后调用 sync 续传",
                        source.group(), target.group(), entity.getName(), copied, error);
                throw error;
            }
        }
    }

    private List<Object[]> read(Connection input, FullSyncMapping mapping, String cursor) throws SQLException {
        String selected = java.util.stream.IntStream.range(0, mapping.columns.size())
                .mapToObj(i -> mapping.quote(mapping.columns.get(i)) + " AS c" + i).collect(Collectors.joining(","));
        String sql = "SELECT " + selected + " FROM " + mapping.table.sql()
                + (cursor == null ? "" : " WHERE " + mapping.primary() + " > ?")
                + " ORDER BY " + mapping.primary() + " ASC LIMIT " + batchSize;
        try (PreparedStatement statement = input.prepareStatement(sql)) {
            if (cursor != null) source.bindMaintenanceValue(statement, 1,
                    primaryBinding(primary(cursor, mapping.fields.getFirst().getType()),
                            source.getCoderManager().createEncoderBuffer()));
            try (ResultSet result = statement.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    IDataBuffer decoder = source.getCoderManager().createDecoderBuffer();
                    Object[] row = new Object[mapping.fields.size()];
                    for (int i = 0; i < row.length; i++) {
                        Object raw = source.readMaintenanceValue(result, "c" + i, metadata.getColumnType(i + 1));
                        row[i] = raw == null ? null : i == 0
                                ? primary(raw.toString(), mapping.fields.getFirst().getType())
                                : decoder.decode(raw, mapping.fields.get(i));
                    }
                    if (row[0] == null) throw new IllegalStateException("源主键不能为空");
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void write(Connection output, FullSyncMapping mapping, List<Object[]> rows) throws SQLException {
        List<Object[]> encoded = new ArrayList<>(rows.size());
        Set<String> incoming = new HashSet<>();
        for (Object[] row : rows) {
            if (!incoming.add(row[0].toString())) throw new IllegalStateException("源主键解码后重复，请核对实体主键类型");
            IDataBuffer encoder = target.getCoderManager().createEncoderBuffer();
            Object[] values = new Object[row.length];
            for (int i = 1; i < row.length; i++) values[i] = row[i] == null ? null : encoder.encode(row[i]);
            values[0] = primaryBinding(row[0], encoder);
            encoded.add(values);
        }
        Set<String> existing = new HashSet<>();
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        try (PreparedStatement statement = output.prepareStatement("SELECT " + mapping.primary()
                + " AS c0 FROM " + mapping.table.sql() + " WHERE " + mapping.primary() + " IN (" + placeholders + ")")) {
            for (int i = 0; i < encoded.size(); i++) target.bindMaintenanceValue(statement, i + 1, encoded.get(i)[0]);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String key = primary(result.getString(1), mapping.fields.getFirst().getType()).toString();
                    if (!incoming.contains(key))
                        throw new IllegalStateException("目标主键比较规则与源值不一致，拒绝覆盖其他主键：" + key);
                    existing.add(key);
                }
            }
        }
        String insertSql = "INSERT INTO " + mapping.table.sql() + " (" + mapping.quotedColumns() + ") VALUES ("
                + String.join(",", Collections.nCopies(mapping.columns.size(), "?")) + ")";
        // 先按主键查询再分 INSERT / UPDATE。不会因 MySQL 的其他 UNIQUE 冲突覆盖另一条记录。
        String assignments = mapping.columns.stream().skip(1).map(name -> mapping.quote(name) + "=?").collect(Collectors.joining(","));
        String updateSql = "UPDATE " + mapping.table.sql() + " SET "
                + (assignments.isEmpty() ? mapping.primary() + "=" + mapping.primary() : assignments)
                + " WHERE " + mapping.primary() + "=?";
        try (PreparedStatement inserts = output.prepareStatement(insertSql);
             PreparedStatement updates = output.prepareStatement(updateSql)) {
            int insertCount = 0, updateCount = 0;
            for (int r = 0; r < rows.size(); r++) {
                Object[] values = encoded.get(r);
                if (existing.contains(rows.get(r)[0].toString())) {
                    int index = 1;
                    for (int i = 1; i < values.length; i++) target.bindMaintenanceValue(updates, index++, values[i]);
                    target.bindMaintenanceValue(updates, index, values[0]);
                    updates.addBatch();
                    updateCount++;
                } else {
                    for (int i = 0; i < values.length; i++) target.bindMaintenanceValue(inserts, i + 1, values[i]);
                    inserts.addBatch();
                    insertCount++;
                }
            }
            if (updateCount > 0) batch(updates, updateCount, true);
            if (insertCount > 0) batch(inserts, insertCount, false);
        }
    }

    private static void batch(PreparedStatement statement, int expected, boolean update) throws SQLException {
        int[] counts = statement.executeBatch();
        if (counts.length != expected) throw new SQLException("全量批次返回数量不完整");
        for (int count : counts) {
            if (count == Statement.EXECUTE_FAILED || (!update && count == 0))
                throw new SQLException("全量批次包含失败或未插入的记录");
        }
    }

    static Object primary(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == long.class || type == Long.class) return Long.valueOf(value);
        if (type == int.class || type == Integer.class) return Integer.valueOf(value);
        if (type == short.class || type == Short.class) return Short.valueOf(value);
        if (type == byte.class || type == Byte.class) return Byte.valueOf(value);
        if (type == java.math.BigInteger.class) return new java.math.BigInteger(value);
        throw new IllegalArgumentException("不支持的主键游标类型：" + type.getName());
    }

    private static Object primaryBinding(Object value, IDataBuffer encoder) {
        // 公共 BigInteger 编码器用于 TEXT 字段；已校验为 NUMERIC 的主键必须按数字绑定。
        return value instanceof java.math.BigInteger integer ? new java.math.BigDecimal(integer) : encoder.encode(value);
    }

    private Progress load(Connection connection, String table, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id,mapping_hash,last_key,completed_rows,status FROM " + table + " WHERE task_key=?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new Progress(rows.getString(1), rows.getString(2), rows.getString(3),
                        rows.getLong(4), rows.getString(5)) : null;
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            if (statement.executeUpdate() != 1) throw new SQLException("同步检查点写入失败，未影响唯一任务记录");
        }
    }

    private static void requireTransactionalTable(Connection connection, String catalog, String table,
                                                   FullSyncMapping.Dialect dialect) throws SQLException {
        if (dialect != FullSyncMapping.Dialect.MYSQL) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
            statement.setString(1, catalog);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)))
                    throw new IllegalStateException("全量同步目标业务表和进度表必须使用 InnoDB：" + catalog + "." + table);
            }
        }
    }

    private static String identity(Connection connection, FullSyncMapping mapping) throws SQLException {
        // 只进入 SHA-256 指纹，不落库/打印可能含敏感连接参数的 JDBC URL。
        return connection.getMetaData().getURL() + "\0" + mapping.table.catalog() + "\0"
                + mapping.table.schema() + "\0" + mapping.table.name();
    }
    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String abbreviate(String value) { return value.substring(0, Math.min(value.length(), 2000)); }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    private SyncResult result(Progress progress) {
        return new SyncResult(progress.taskId, source.group(), target.group(), entity.getName(), progress.status,
                progress.cursor, progress.rows, copied, batches, elapsed(started));
    }

    /** 会话锁跨批次 commit 保留；连接异常关闭后数据库释放，进程崩溃不留下永久 RUNNING 锁。 */
    private static final class DatabaseLock implements AutoCloseable {
        private final Connection connection;
        private final FullSyncMapping.Dialect dialect;
        private final String key;
        DatabaseLock(Connection connection, FullSyncMapping.Dialect dialect, String key) throws SQLException {
            this.connection = connection;
            this.dialect = dialect;
            this.key = key;
            String sql = dialect == FullSyncMapping.Dialect.MYSQL ? "SELECT GET_LOCK(?,0)" : "SELECT pg_try_advisory_lock(?)";
            boolean acquired;
            try { acquired = call(sql); }
            catch (SQLException error) {
                // 取得锁后读取结果失败时，也不能把可能持锁的连接归还连接池。
                try { connection.abort(Runnable::run); }
                catch (SQLException abort) { error.addSuppressed(abort); }
                throw error;
            }
            if (!acquired) throw new IllegalStateException("同一全量同步任务正在执行，请勿重复启动");
        }
        private boolean call(String sql) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (dialect == FullSyncMapping.Dialect.MYSQL) statement.setString(1, key);
                else statement.setLong(1, Long.parseUnsignedLong(key.substring(0, 16), 16));
                try (ResultSet rows = statement.executeQuery()) { return rows.next() && rows.getBoolean(1); }
            }
        }
        @Override public void close() throws SQLException {
            try {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(true);
                if (!call(dialect == FullSyncMapping.Dialect.MYSQL ? "SELECT RELEASE_LOCK(?)" : "SELECT pg_advisory_unlock(?)"))
                    throw new SQLException("释放全量同步会话锁失败");
            } catch (SQLException error) {
                // 不能把仍持有会话锁的物理连接归还连接池。
                try { connection.abort(Runnable::run); } catch (SQLException abort) { error.addSuppressed(abort); }
                throw error;
            }
        }
    }
}

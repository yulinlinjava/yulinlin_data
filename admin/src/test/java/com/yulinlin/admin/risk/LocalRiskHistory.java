package com.yulinlin.admin.risk;

import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import com.yulinlin.jdbc.mysql.MysqlSession;
import com.yulinlin.jdbc.mysql.coder.MysqlCoderManager;
import com.yulinlin.jdbc.postgresql.PostgresqlSession;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.security.risk.RiskModels.ReplayEvent;
import com.yulinlin.security.risk.RiskProperties;
import lombok.Data;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Local manual test support: paged ORM SELECTs, no schema changes or live risk state. */
final class LocalRiskHistory {
    record Config(DataSourceProperties datasource, History history, RiskProperties risk) {}
    record Range(LocalDateTime start, LocalDateTime end, ZoneId zone) {}
    record Loaded(Range range, List<ReplayEvent> events) {}

    static Config load(Resource resource) throws IOException {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("risk-local", resource))
            environment.getPropertySources().addLast(source);
        var binder = Binder.get(environment);
        return new Config(binder.bind("spring.datasource", Bindable.of(DataSourceProperties.class)).orElseGet(DataSourceProperties::new),
                binder.bind("risk-replay", Bindable.of(History.class)).orElseGet(History::new),
                binder.bind("yulinlin.security.risk", Bindable.of(RiskProperties.class)).orElseGet(RiskProperties::new));
    }

    static List<ReplayEvent> read(Config config) {
        return read(config, Clock.systemUTC()).events();
    }

    static Loaded read(Config config, Clock clock) {
        var h = config.history();
        h.validate();
        var range = h.range(clock);
        config.risk().validate();
        if (!config.risk().getBehaviors().containsKey(h.behaviorType))
            throw new IllegalArgumentException("风控配置中没有行为：" + h.behaviorType);
        String url = config.datasource().getUrl();
        if (url == null || url.isBlank()) throw new IllegalArgumentException("请填写 spring.datasource.url");
        var source = new DriverManagerDataSource(url, config.datasource().getUsername(), config.datasource().getPassword()) {
            @Override protected Connection getConnectionFromDriver(Properties properties) throws SQLException {
                var connection = super.getConnectionFromDriver(properties);
                try {
                    connection.setReadOnly(true);
                    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    return connection;
                } catch (SQLException error) {
                    try { connection.close(); } catch (SQLException close) { error.addSuppressed(close); }
                    throw error;
                }
            }
        };
        var session = session(url, source);
        try {
            var proxies = EntityProxyService.newInstance();
            session.setProxyService(proxies);
            session.setGroup("risk-history");
            var route = RouteSession.builder().proxyService(proxies).build();
            route.setLoadBalance(new RandomLoadBalance());
            route.registerSession(session);
            var query = new SelectWrapper<HistoryRow>().table(h.table).orderByAsc(h.timeColumn);
            query.fields().field(h.userColumn, "userId").field(h.resourceColumn, "resourceId")
                    .field(h.timeColumn, "occurredAt");
            if (!h.idColumn.isBlank()) {
                query.fields().field(h.idColumn, "eventId");
                // Time-range index scans stay ordered; the unique ID makes equal timestamps stable across pages.
                query.orderByAsc(h.idColumn);
            } else {
                query.orderByAsc(h.userColumn);
            }
            if (!h.userId.isBlank()) query.where().eq(h.userColumn, h.userId);
            query.where().gte(h.timeColumn, range.start()).lt(h.timeColumn, range.end());
            // A single read-only repeatable-read transaction keeps pages on the same database snapshot.
            var events = route.transaction(() -> {
                int expected = route.count(QueryRequest.newInstance(HistoryRow.class, query).autoUpdate(false));
                if (h.maxRecords > 0 && expected > h.maxRecords)
                    throw new IllegalArgumentException("历史记录超过 max-records=" + h.maxRecords + "，请缩小时间范围或调整上限；本次未回放");
                var loaded = new ArrayList<ReplayEvent>();
                int pageSize = h.maxRecords == 0 ? h.pageSize : Math.min(h.pageSize, h.maxRecords + 1);
                for (int page = 1; ; page++) {
                    query.page(page, pageSize);
                    var rows = route.select(QueryRequest.newInstance(HistoryRow.class, query).autoUpdate(false));
                    if (page > 1 && !rows.isEmpty() && h.idColumn.isBlank())
                        throw new IllegalArgumentException("分页读取历史请配置唯一的 id-column，以保证同一时间记录的稳定顺序");
                    if (h.maxRecords > 0 && loaded.size() + (long) rows.size() > h.maxRecords)
                        throw new IllegalArgumentException("历史记录超过 max-records=" + h.maxRecords + "，请缩小时间范围或调整上限；本次未回放");
                    for (var row : rows) {
                        if (row.occurredAt == null) throw new IllegalArgumentException("历史记录时间不能为空");
                        loaded.add(new ReplayEvent(row.userId, h.behaviorType, row.resourceId, row.eventId,
                                row.occurredAt.atZone(range.zone()).toInstant().toEpochMilli()));
                    }
                    if (expected > pageSize)
                        System.out.printf("历史读取进度：%d / %d 条%n", loaded.size(), expected);
                    if (rows.size() < pageSize) break;
                }
                if (loaded.size() != expected)
                    throw new IllegalStateException("历史快照校验失败：应读取 " + expected + " 条，实际 " + loaded.size() + " 条；本次未回放");
                System.out.printf("历史快照校验通过：共 %d 条，实际读取 %d 条%n", expected, loaded.size());
                return loaded;
            });
            return new Loaded(range, events);
        } finally {
            session.getThreadPoolExecutor().shutdownNow();
        }
    }

    private static JdbcSession session(String url, DataSource source) {
        if (url.startsWith("jdbc:mysql:")) {
            var session = new MysqlSession(source);
            session.setCoderManager(new MysqlCoderManager());
            return session;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            var session = new PostgresqlSession(source);
            session.setCoderManager(new PostgresqlCoderManager());
            return session;
        }
        if (url.startsWith("jdbc:h2:")) {
            var session = new H2Session(source);
            session.setCoderManager(new H2CoderManager());
            return session;
        }
        throw new IllegalArgumentException("本地历史回放支持 MySQL、PostgreSQL、H2 JDBC URL");
    }

    private static LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    @Data
    public static class History {
        private String table = "video_history";
        private String userColumn = "user_id";
        private String resourceColumn = "video_id";
        private String timeColumn = "crt_time";
        private String idColumn = "";
        private String userId = "";
        private String start = "";
        private String end = "";
        private String zone = "Asia/Shanghai";
        private int maxRecords;
        private int pageSize = 5_000;
        private String reportDirectory = "target/risk-replay";
        /** Optional output filter, not a detector threshold: only hits with records strictly above this value. */
        private Integer reportRequestsGreaterThan;
        private String behaviorType = "video-play-url";

        void validate() {
            identifier(table, true);
            for (String column : List.of(userColumn, resourceColumn, timeColumn)) identifier(column, false);
            if (!idColumn.isBlank()) identifier(idColumn, false);
            if (start.isBlank() != end.isBlank()) throw new IllegalArgumentException("start 和 end 应同时填写或同时留空");
            if (!start.isBlank() && !parseTime(start).isBefore(parseTime(end)))
                throw new IllegalArgumentException("start 必须早于 end");
            ZoneId.of(zone);
            if (maxRecords < 0 || maxRecords == Integer.MAX_VALUE)
                throw new IllegalArgumentException("max-records 必须为 0（不限）或小于 2147483647 的正数");
            if (pageSize < 1 || pageSize > 50_000) throw new IllegalArgumentException("page-size 必须在 1 到 50000 之间");
            if (reportRequestsGreaterThan != null && reportRequestsGreaterThan < 0)
                throw new IllegalArgumentException("report-requests-greater-than 必须为非负数");
        }

        Range range(Clock clock) {
            ZoneId sourceZone = ZoneId.of(zone);
            if (!start.isBlank()) return new Range(parseTime(start), parseTime(end), sourceZone);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), sourceZone);
            return new Range(now.toLocalDate().atStartOfDay(), now, sourceZone);
        }

        private static void identifier(String value, boolean table) {
            String part = "[A-Za-z_][A-Za-z0-9_]*";
            if (value == null || !value.matches(table ? part + "(?:\\." + part + ")?" : part))
                throw new IllegalArgumentException("表或列名只能填写普通标识符，不能填写 SQL 表达式：" + value);
        }
    }

    @Data
    public static class HistoryRow {
        private String userId;
        private String resourceId;
        private String eventId;
        private LocalDateTime occurredAt;
    }
}

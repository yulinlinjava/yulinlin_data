package com.yulinlin.admin.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.http.HttpRequestClient;
import com.yulinlin.data.core.request.ExecuteRequest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Models external business concurrency: a fixed number of application threads repeatedly submit
 * independent ORM requests, with at most {@code requestSize} rows in each request. Invocation
 * setup/clear and teardown/count verification are outside the measured interval.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
@Threads(1)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class LocalDatabaseWriteBenchmark {
    private static final String SQLITE_GROUP = "sqlite-benchmark";
    private static final String H2_GROUP = "h2-benchmark";
    private static final String TABLE = "jmh_local_write";

    @Param({"100000"})
    public int size;

    @Param({"4"})
    public int businessThreads;

    @Param({"128"})
    public int requestSize;

    private ConfigurableApplicationContext context;
    private Path databaseDirectory;
    private List<List<List<WriteRow>>> workerRequests;
    private ExecutorService businessExecutor;

    @Setup(Level.Trial)
    public void start() throws IOException {
        databaseDirectory = Files.createTempDirectory("yulinlin-local-db-jmh-");
        String sqliteFile = databaseDirectory.resolve("sqlite.db").toString().replace('\\', '/');
        String h2File = databaseDirectory.resolve("h2").toString().replace('\\', '/');

        context = new SpringApplicationBuilder(BenchmarkConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.main.log-startup-info=false",
                        "spring.main.register-shutdown-hook=false",
                        "spring.jmx.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration",
                        "logging.level.root=ERROR",
                        "log=false",
                        "yulinlin.sqlite.file=" + sqliteFile,
                        "yulinlin.sqlite.group=" + SQLITE_GROUP,
                        "yulinlin.sqlite.busy-timeout=5000",
                        "yulinlin.sqlite.synchronous=NORMAL",
                        "yulinlin.sqlite.schema-packages=com.yulinlin.admin.benchmark",
                        "yulinlin.h2.file=" + h2File,
                        "yulinlin.h2.group=" + H2_GROUP,
                        "yulinlin.h2.parallel-connections=" + businessThreads,
                        "yulinlin.h2.execute-batch-size=256",
                        "yulinlin.h2.schema-mode=CREATE",
                        "yulinlin.h2.schema-packages=com.yulinlin.admin.benchmark",
                        "yulinlin.h2.auto-server=false",
                        "yulinlin.sqlite.execute-batch-size=256")
                .run();

        List<WriteRow> rows = createRows(size);
        workerRequests = partition(rows, businessThreads, requestSize);
        businessExecutor = Executors.newFixedThreadPool(businessThreads);
        // Force table and declared-index creation before any measured invocation.
        ModelSelectWrapper.newInstance(SQLITE_GROUP, WriteRow.class).count();
        ModelSelectWrapper.newInstance(H2_GROUP, WriteRow.class).count();
    }

    @Setup(Level.Invocation)
    public void clearTable(BenchmarkParams params) {
        String group = group(params);
        String verb = H2_GROUP.equals(group) ? "TRUNCATE TABLE " : "DELETE FROM ";
        ExecuteRequest<Object> request = ExecuteRequest.newInstance(
                verb + "\"" + TABLE + "\"", Map.of());
        request.setSession(group);
        request.execute();
    }

    /** Concurrent callers share SQLite's single physical writer connection. */
    @Benchmark
    public int sqliteConcurrentRequests() throws Exception {
        return insertConcurrent(SQLITE_GROUP);
    }

    /** Concurrent callers can use up to businessThreads H2 connections. */
    @Benchmark
    public int h2ConcurrentRequests() throws Exception {
        return insertConcurrent(H2_GROUP);
    }

    @TearDown(Level.Invocation)
    public void verifyRowCount(BenchmarkParams params) {
        int actual = ModelSelectWrapper.newInstance(group(params), WriteRow.class).count();
        if (actual != size) {
            throw new IllegalStateException("Expected " + size + " inserted rows, found " + actual);
        }
    }

    @TearDown(Level.Trial)
    public void stop() throws IOException {
        if (businessExecutor != null) businessExecutor.shutdownNow();
        if (context != null) context.close();
        if (databaseDirectory != null && Files.exists(databaseDirectory)) {
            try (var paths = Files.walk(databaseDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private int insertConcurrent(String group) throws Exception {
        List<Callable<Integer>> jobs = new ArrayList<>(workerRequests.size());
        for (List<List<WriteRow>> requests : workerRequests) {
            jobs.add(() -> insertWorker(group, requests));
        }

        int inserted = 0;
        List<Future<Integer>> results = businessExecutor.invokeAll(jobs);
        for (Future<Integer> result : results) inserted += result.get();
        if (inserted != size) {
            throw new IllegalStateException("Driver reported " + inserted + " rows; expected " + size);
        }
        return inserted;
    }

    private static int insertWorker(String group, List<List<WriteRow>> requests) {
        int inserted = 0;
        for (List<WriteRow> request : requests) {
            inserted += ModelInsertWrapper.newInstance(group, request).execute();
        }
        return inserted;
    }

    private static String group(BenchmarkParams params) {
        return params.getBenchmark().contains("sqlite") ? SQLITE_GROUP : H2_GROUP;
    }

    private static List<WriteRow> createRows(int size) {
        List<WriteRow> values = new ArrayList<>(size);
        long baseTime = 1_735_689_600_000L;
        for (int index = 0; index < size; index++) {
            WriteRow row = new WriteRow();
            row.setId("row-" + index);
            row.setTenantId(index % 100);
            row.setSequenceNo((long) index);
            row.setAmount(BigDecimal.valueOf(index, 2));
            row.setPayload("payload-" + index + "-abcdefghijklmnopqrstuvwxyz");
            row.setCreatedAt(new Date(baseTime + index * 1_000L));
            values.add(row);
        }
        return List.copyOf(values);
    }

    private static List<List<List<WriteRow>>> partition(
            List<WriteRow> rows, int businessThreads, int requestSize) {
        if (businessThreads < 1) throw new IllegalArgumentException("businessThreads must be positive");
        if (requestSize < 1) throw new IllegalArgumentException("requestSize must be positive");

        List<List<List<WriteRow>>> workers = new ArrayList<>(businessThreads);
        for (int index = 0; index < businessThreads; index++) workers.add(new ArrayList<>());

        int requestIndex = 0;
        for (int start = 0; start < rows.size(); start += requestSize) {
            int end = Math.min(rows.size(), start + requestSize);
            workers.get(requestIndex++ % businessThreads).add(List.copyOf(rows.subList(start, end)));
        }
        return workers.stream().map(List::copyOf).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class BenchmarkConfiguration {
        /** Keeps unrelated HTTP-client initialization and networking out of the database benchmark. */
        @Bean
        HttpRequestClient benchmarkHttpRequestClient() {
            RestClient restClient = RestClient.builder()
                    .requestFactory(new SimpleClientHttpRequestFactory())
                    .build();
            return new HttpRequestClient(restClient, new ObjectMapper());
        }
    }

    @JoinTable(value = TABLE, autoSchema = true)
    @JoinIndex(fields = {"tenantId", "createdAt"})
    public static class WriteRow {
        @JoinField(name = "id")
        @JoinMeta(primaryKey = true)
        private String id;

        @JoinField(name = "tenant_id")
        private Integer tenantId;

        @JoinField(name = "sequence_no")
        private Long sequenceNo;

        @JoinField(name = "amount")
        private BigDecimal amount;

        @JoinField(name = "payload")
        private String payload;

        @JoinField(name = "created_at")
        private Date createdAt;

        public WriteRow() { }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
        public Long getSequenceNo() { return sequenceNo; }
        public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    }
}

package com.yulinlin.jdbc.session;


import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.JdbcSessionProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractJdbcSession extends AbstractSession implements EntitySession {


    private JdbcSessionProperties properties = new JdbcSessionProperties();
    private volatile int parallelConnections = properties.getParallelConnections();
    private volatile int executeBatchSize = properties.getExecuteBatchSize();
    private final ThreadLocal<JdbcTransactionResources> transactionResources = new ThreadLocal<>();

    protected DataSource dataSource;

    public AbstractJdbcSession(DataSource dataSource) {
        this.dataSource = JdbcTransactionResources.targetDataSource(java.util.Objects.requireNonNull(dataSource, "dataSource"));

    }




    protected Object readColumn(ResultSet rows, String label, int jdbcType) throws java.sql.SQLException {
        return rows.getString(label);
    }

    /** Parse a node using this session, without opening a connection. */
    public Object parseSql(INode node, com.yulinlin.data.core.parse.IParamsContext context) {
        return getParseManager().parse(node, context);
    }

    @Override
    protected ParseResult parseNode(
            RequestType requestType,
            Object root, INode node, Class clazz) {
        return (ParseResult) parseSql(node, createParamsContext(requestType, root, clazz));
    }

    @Override
    protected SimpParamsContext createParamsContext(RequestType requestType, Object root, Class<?> clazz) {
        if(root == null){
            root = new HashMap<>();
        }
        SimpParamsContext context = new SimpParamsContext(requestType,root,getCoderManager().createEncoderBuffer(),clazz,isMapUnderscoreToCamelCase());

        if(root instanceof Map){
            context.put((Map)root);
        }else {
            for (Field field : ReflectionUtil.getAllDeclaredFields(root.getClass())) {
                Object o = ReflectionUtil.invokeGetter(root, field);
                if(o == null){
                    continue;
                }
                context.put(field.getName(),o);
            }

        }




        return context;
    }

    @Override
    protected boolean isMapUnderscoreToCamelCase() {
        return properties.isMapUnderscoreToCamelCase();
    }


    protected List<IDataBuffer> resultSetToBuffer(ResultSet resultSet ) throws Exception {



        ArrayList<IDataBuffer> list =  new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnTotal = metaData.getColumnCount();
        String[] labels = new String[columnTotal];
        int[] types = new int[columnTotal];
        for (int i = 0; i < columnTotal; i++) {
            labels[i] = metaData.getColumnLabel(i + 1);
            types[i] = metaData.getColumnType(i + 1);
        }
        while (resultSet.next()) {
            IDataBuffer buffer = getCoderManager().createDecoderBuffer();

            list.add(buffer);


            for (int i = 1; i <= columnTotal; i++) {

                String columnName = labels[i - 1];
                Object value = null;

                //列类型
            //    int type =  metaData.getColumnType(i);
            //    JDBCType jdbcType = JDBCType.valueOf(type);

                value = readColumn(resultSet, columnName, types[i - 1]);


                if (value == null) {
                    continue;
                }
                buffer.put(columnName,value);
            }

        }

        return list;
    }


    @Override
    protected CompletableFuture<Integer> executeUpdateAsync(List<ParseResult> results, RequestType requestType) {
        if (isOpenTransaction()) return CompletableFuture.completedFuture(executeUpdate(results, requestType));
        return CompletableFuture.supplyAsync(() -> executeIndependentUpdate(results), getThreadPoolExecutor());
    }

    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        return executeSelect(request).getFirst();
    }

    @Override
    protected Integer executeUpdate(List<ParseResult> results, RequestType requestType) {
        if (!isOpenTransaction()) return executeIndependentUpdate(results);
        JdbcTransactionResources resources = transactionConnections();
        Connection connection = resources.getConnection();
        try {
            return executeUpdateNode(connection, results);
        } catch (Throwable error) {
            throw executionFailure(resources, connection, error);
        } finally {
            resources.releaseConnection(connection);
        }
    }

    private Integer executeIndependentUpdate(List<ParseResult> results) {
        try (Connection connection = dataSource.getConnection()) {
            // Parallel batches are independent auto-commit operations.
            if (!connection.getAutoCommit()) connection.setAutoCommit(true);
            return executeUpdateNode(connection, results);
        } catch (Throwable error) {
            throw JdbcErrors.translate(error);
        }
    }

    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {
        return executeSelect(request);
    }

    @lombok.SneakyThrows
    @Override
    protected List<IDataBuffer> executeSelect(ParseResult request) {
        SqlNode sqlNode = (SqlNode)request.getRequest();
        return withQueryConnection(connection -> executeSelectNode(connection, sqlNode));
    }

    @FunctionalInterface
    protected interface QueryConnectionWork<T> {
        T apply(Connection connection) throws Exception;
    }

    /** Queries and diagnostics share the same transaction resource and connection lifetime. */
    @lombok.SneakyThrows
    protected final <T> T withQueryConnection(QueryConnectionWork<T> work) {
        JdbcTransactionResources resources = isOpenTransaction() ? transactionConnections() : null;
        if (resources == null) {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            } catch (java.sql.SQLException error) {
                throw executionFailure(null, null, error);
            }
        }
        Connection connection = resources.getConnection();
        try {
            return work.apply(connection);
        } catch (Throwable error) {
            throw executionFailure(resources, connection, error);
        } finally {
            resources.releaseConnection(connection);
        }

    }

    @Override
    protected void onExecutionFailure(Throwable error) {
        // SQL 错误已在持有连接的执行入口判断；普通错误交给事务边界决定回滚。
        if (error instanceof Error) setRollbackOnly();
    }

    private RuntimeException executionFailure(JdbcTransactionResources resources, Connection connection, Throwable error) {
        if (resources != null) resources.executionFailed(connection, error);
        return JdbcErrors.translate(error);
    }

    protected  abstract Integer executeUpdateNode(Connection connection, List<ParseResult> list);


    protected  abstract List<IDataBuffer> executeSelectNode(Connection connection,SqlNode node);






    @Override
    public void startTransaction() {
        if (transactionResources.get() == null)
            transactionResources.set(new JdbcTransactionResources(dataSource, group(), String.valueOf(cluster())));
    }

    @Override
    public void commitTransaction() {
        finishTransactionResource(false);
    }

    @Override
    public void rollbackTransaction() {
        finishTransactionResource(true);
    }

    private void finishTransactionResource(boolean rollback) {
        JdbcTransactionResources resources = transactionResources.get();
        transactionResources.remove();
        if (resources != null) resources.finish(rollback);
    }

    @Override public boolean isOpenTransaction() { return transactionResources.get() != null; }

    @Override public void setRollbackOnly() {
        JdbcTransactionResources resources = transactionResources.get();
        if (resources != null) resources.setRollbackOnly();
    }

    @Override public boolean isRollbackOnly() {
        JdbcTransactionResources resources = transactionResources.get();
        return resources != null && resources.isRollbackOnly();
    }

    protected final JdbcTransactionResources transactionConnections() {
        JdbcTransactionResources resources = transactionResources.get();
        if (resources == null) throw new IllegalStateException("JDBC 事务资源尚未开启，请通过 RouteSession 执行写操作");
        // 同一业务线程内延迟取得唯一物理连接，保存点也使用该连接。
        return resources.initialize();
    }

    @Override public boolean supportsTransactions() { return true; }

    @Override public Object createSavepoint() { return transactionConnections().createSavepoint(); }

    @Override public void releaseSavepoint(Object savepoint) { transactionConnections().releaseSavepoint(savepoint); }

    @Override public void rollbackToSavepoint(Object savepoint) {
        JdbcTransactionResources resources = transactionResources.get();
        if (resources == null) throw new IllegalStateException("JDBC 事务资源尚未开启");
        resources.rollbackToSavepoint(savepoint);
    }

    @Override public boolean supportsParallelWrites() {
        return !isOpenTransaction() && parallelWriteGroupCount() > 1;
    }

    @Override protected int parallelWriteGroupCount() {
        return isOpenTransaction() ? 1 : JdbcTransactionResources.connectionLimit(dataSource, parallelConnections);
    }

    public int getParallelConnections() { return parallelConnections; }

    public void setParallelConnections(int parallelConnections) {
        if (parallelConnections < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        if (isOpenTransaction()) throw new IllegalStateException("Cannot reconfigure an active JDBC transaction");
        this.parallelConnections = parallelConnections;
    }

    public int getExecuteBatchSize() { return executeBatchSize; }

    public void setExecuteBatchSize(int executeBatchSize) {
        if (executeBatchSize < 1) throw new IllegalArgumentException("executeBatchSize must be positive");
        if (isOpenTransaction()) throw new IllegalStateException("Cannot reconfigure an active JDBC transaction");
        this.executeBatchSize = executeBatchSize;
    }

    public void setProperties(JdbcSessionProperties properties) {
        setParallelConnections(properties.getParallelConnections());
        setExecuteBatchSize(properties.getExecuteBatchSize());
        this.properties = properties;
        setSessionProperties(properties);
    }

    /** @deprecated Use {@link #setProperties(JdbcSessionProperties)}. */
    @Deprecated
    public void setProperties(JdbcProperties properties) {
        setProperties((JdbcSessionProperties) properties);
    }

    public JdbcSessionProperties getProperties() {
        return properties;
    }
}

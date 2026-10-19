package com.yulinlin.jdbc.session;


import com.zaxxer.hikari.HikariDataSource;
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
import java.util.concurrent.Future;

@Slf4j
public abstract class AbstractJdbcSession extends AbstractSession implements EntitySession {


    private JdbcSessionProperties properties = new JdbcSessionProperties();
    private volatile int parallelConnections = ConnectionUtil.DEFAULT_PARALLEL_CONNECTIONS;
    private volatile int executeBatchSize = 256;
    private final ThreadLocal<ConnectionPool> transactionPools = new ThreadLocal<>();

    protected DataSource dataSource;

    public AbstractJdbcSession(DataSource dataSource) {
        this.dataSource = ConnectionPool.targetDataSource(java.util.Objects.requireNonNull(dataSource, "dataSource"));

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




        ParseResult result = (ParseResult) parseSql(node, context);
        return result;
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
        // Capture the owning session's context, not a worker-thread ThreadLocal or global route.
        ConnectionPool pool = transactionConnections();

        CompletableFuture<Integer> future =   CompletableFuture.supplyAsync(() -> {
            Connection connection  =  pool.getConnection();
            try {
                return executeUpdateNode(connection,results);
            } catch (Throwable error) {
                pool.setRollbackOnly();
                throw error;
            }finally {
                pool.releaseConnection(connection);
            }
        },getThreadPoolExecutor());


        return future;


    }

    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        List<IDataBuffer> buffers = executeSelect(request);
        return buffers.get(0);
    }

    @Override
    protected Integer executeUpdate(List<ParseResult> list,RequestType requestType) {


            ConnectionPool pool = transactionConnections();
            Connection connection = pool.getConnection();

            try {
                Integer value =  executeUpdateNode(connection,list);
                return value;
            }finally {
                pool.releaseConnection(connection);
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


        ConnectionPool pool = isOpenTransaction() ? transactionConnections() : null;
        if (pool == null) {
            try (Connection connection = dataSource.getConnection()) {
                return executeSelectNode(connection, sqlNode);
            }
        }
        Connection connection = pool.getConnection();
        try {
            return executeSelectNode(connection, sqlNode);
        } finally {
            pool.releaseConnection(connection);
        }

    }





    protected  abstract Integer executeUpdateNode(Connection connection, List<ParseResult> list);


    protected  abstract List<IDataBuffer> executeSelectNode(Connection connection,SqlNode node);






    @Override
    public void startTransaction() {
        if (transactionPools.get() == null)
            transactionPools.set(new ConnectionPool(dataSource, parallelConnections, group(), String.valueOf(cluster())));
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
        ConnectionPool pool = transactionPools.get();
        transactionPools.remove();
        if (pool != null) pool.finish(rollback);
    }

    @Override public boolean isOpenTransaction() { return transactionPools.get() != null; }

    @Override public void setRollbackOnly() {
        ConnectionPool pool = transactionPools.get();
        if (pool != null) pool.setRollbackOnly();
    }

    @Override public boolean isRollbackOnly() {
        ConnectionPool pool = transactionPools.get();
        return pool != null && pool.isRollbackOnly();
    }

    protected final ConnectionPool transactionConnections() {
        ConnectionPool pool = transactionPools.get();
        if (pool == null) throw new IllegalStateException("JDBC 事务资源尚未开启，请通过 RouteSession 执行写操作");
        // 只在真正执行 SQL 时借连接，并在交给批处理工作线程前由原线程初始化。
        return pool.initialize();
    }

    @Override protected boolean isOpenAsync(ExecuteRequest request, List<ParseResult> results) {
        if (!super.isOpenAsync(request, results)) return false;
        ConnectionPool pool = transactionConnections();
        return pool.getLimit() > 1;
    }

    @Override public boolean supportsParallelWrites() {
        return parallelWriteGroupCount() > 1;
    }

    @Override protected int parallelWriteGroupCount() {
        ConnectionPool pool = transactionPools.get();
        if (pool != null) return pool.getLimit();
        return dataSource instanceof HikariDataSource hikari
                ? Math.min(parallelConnections, Math.max(1, hikari.getMaximumPoolSize())) : parallelConnections;
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

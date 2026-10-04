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
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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


    private JdbcProperties properties = new JdbcProperties();
    private volatile int parallelConnections = ConnectionUtil.DEFAULT_PARALLEL_CONNECTIONS;
    private volatile int executeBatchSize = 256;
    private final ThreadLocal<ConnectionPool> transactionPools = new ThreadLocal<>();

    protected DataSource dataSource;

    public AbstractJdbcSession(DataSource dataSource) {
        this.dataSource = dataSource;

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




        ParseResult result = (ParseResult) getParseManager().parse(node,context);
        return result;
    }

    @Override
    protected boolean isMapUnderscoreToCamelCase() {
        return properties.isMapUnderscoreToCamelCase();
    }


    protected List<IDataBuffer> resultSetToBuffer(ResultSet resultSet ) throws Exception {



        ArrayList<IDataBuffer> list =  new ArrayList<>();
        while (resultSet.next()) {
            IDataBuffer buffer = getCoderManager().createDecoderBuffer();

            list.add(buffer);

            ResultSetMetaData metaData = resultSet.getMetaData();

            int columnTotal = metaData.getColumnCount();
            for (int i = 1; i <= columnTotal; i++) {

                String columnName = metaData.getColumnLabel(i);
                Object value = null;

                //列类型
            //    int type =  metaData.getColumnType(i);
            //    JDBCType jdbcType = JDBCType.valueOf(type);

                value = resultSet.getString(columnName);


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


            ConnectionPool pool = isOpenTransaction() ? transactionConnections() : null;
            Connection connection = pool == null ? DataSourceUtils.getConnection(dataSource) : pool.getConnection();

            try {
                Integer value =  executeUpdateNode(connection,list);
                return value;
            }finally {
                if (pool == null) DataSourceUtils.releaseConnection(connection, dataSource);
                else pool.releaseConnection(connection);
            }





    }


    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {
        return executeSelect(request);
    }

    @Override
    protected List<IDataBuffer> executeSelect(ParseResult request) {
        SqlNode sqlNode = (SqlNode)request.getRequest();


        ConnectionPool pool = isOpenTransaction() ? transactionConnections() : null;
        Connection connection = pool == null ? DataSourceUtils.getConnection(dataSource) : pool.getConnection();

        try {
            List<IDataBuffer> value =  executeSelectNode(connection,sqlNode);

            return value;
        }finally {
            if (pool == null) DataSourceUtils.releaseConnection(connection, dataSource);
            else pool.releaseConnection(connection);
        }

    }





    protected  abstract Integer executeUpdateNode(Connection connection, List<ParseResult> list);


    protected  abstract List<IDataBuffer> executeSelectNode(Connection connection,SqlNode node);






    @Override
    public void startTransaction() {
        super.startTransaction();

    }

    @Override
    public void commitTransaction() {
        finishTransaction(false);
    }

    @Override
    public void rollbackTransaction() {
        finishTransaction(true);
    }

    private void finishTransaction(boolean rollback) {
        if (!isOpenTransaction()) return;
        if (rollback) setRollbackOnly();
        boolean mustRollback = isRollbackOnly();
        if (rollback) super.rollbackTransaction(); else super.commitTransaction();
        if (isOpenTransaction()) return;
        ConnectionPool pool = transactionPools.get();
        transactionPools.remove();
        if (pool != null) {
            if (mustRollback) pool.setRollbackOnly();
            pool.finish(rollback);
        } else if (!rollback && mustRollback) {
            throw new IllegalStateException("JDBC transaction was marked rollback-only");
        }
    }

    @Override public void setRollbackOnly() {
        super.setRollbackOnly();
        ConnectionPool pool = transactionPools.get();
        if (pool != null) pool.setRollbackOnly();
    }

    @Override public boolean isRollbackOnly() {
        ConnectionPool pool = transactionPools.get();
        return super.isRollbackOnly() || (pool != null && pool.isRollbackOnly());
    }

    protected final ConnectionPool transactionConnections() {
        if (!isOpenTransaction()) throw new IllegalStateException("No JDBC session transaction is active");
        ConnectionPool pool = transactionPools.get();
        if (pool == null) {
            pool = ConnectionUtil.createPool(dataSource, parallelConnections);
            transactionPools.set(pool);
        }
        return pool;
    }

    @Override protected boolean isOpenAsync(ExecuteRequest request, List<ParseResult> results) {
        if (!super.isOpenAsync(request, results)) return false;
        // Never send a Spring thread-bound Connection to workers or fork its transaction.
        ConnectionPool pool = transactionConnections();
        return !pool.isSpringManaged() && pool.getLimit() > 1;
    }

    @Override public boolean supportsParallelWrites() {
        if (parallelWriteGroupCount() <= 1) return false;
        // A Spring-bound connection must never be shared with workers or forked into another transaction.
        if (TransactionSynchronizationManager.hasResource(dataSource)) return false;
        ConnectionPool pool = transactionPools.get();
        return pool == null || !pool.isSpringManaged();
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

    public void setProperties(JdbcProperties properties) {
        setParallelConnections(properties.getParallelConnections());
        setExecuteBatchSize(properties.getExecuteBatchSize());
        this.properties = properties;
    }
}

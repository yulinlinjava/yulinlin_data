package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.TransactionSession;
import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.jdbc.log.JdbcExecutionLog;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 循环依赖解决
 */
@Slf4j
public class JdbcSession extends AbstractJdbcSession implements TransactionSession  {

    @Override
    protected void printExecution(SessionExecution execution) {
        JdbcExecutionLog.print(execution);
    }

    public JdbcSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new com.yulinlin.jdbc.sql.SqlParseManager());
    }

    /** Keeps schema logging and execution in the owning Session. */
    protected int executeSchemaSql(Statement statement, String sql) throws SQLException {
        log.info("[{}][表结构]\n{}", group(), sql);
        try {
            return statement.executeUpdate(sql);
        } catch (SQLException error) {
            log.error("[{}][表结构]执行失败\n{}", group(), sql, error);
            throw error;
        }
    }




    @SneakyThrows

    protected Integer executeUpdateNode(Connection connection, List<ParseResult> list) {

        SqlNodeList nodeList = SqlNodeList.newUpdate();

        try {
            for (ParseResult result : list) nodeList.add((SqlNode) result.getRequest());
            return syncExecuteUpdateNode(connection,nodeList);
        }finally {
            nodeList.close();
        }




    }




    private int syncExecuteUpdateNode (Connection connection, SqlNodeList nodeList) throws SQLException {

        int total = 0;

        for (Map.Entry<String, List<SqlNode>> entry : nodeList.getBatch().entrySet()) {
            total+=syncExecuteUpdateNode(connection,entry.getKey(),entry.getValue());
        }
        return total;

        }
        //同步
    private int syncExecuteUpdateNode (Connection connection, String sql , List<SqlNode> nodeList) throws SQLException {

        int total = 0;
        int pending = 0;
        int batchSize = getExecuteBatchSize();
        try (  PreparedStatement preparedStatement =  connection.prepareStatement(sql)){


            for (SqlNode node : nodeList) {
                int index = 1;
                preparedStatement.clearParameters();
                if (node.getList() != null) for (Object row : node.getList()) {
                    bindValue(preparedStatement, index, row);
                    index++;
                }
                preparedStatement.addBatch();
                if (++pending == batchSize) {
                    total += executeBatch(preparedStatement);
                    pending = 0;
                }
            }
            if (pending > 0) total += executeBatch(preparedStatement);

        }


            return total;

    }

    private int executeBatch(PreparedStatement statement) throws SQLException {
        int[] counts = statement.executeBatch();
        statement.clearBatch();
        int total = 0;
        for (int count : counts) {
            if (count == Statement.EXECUTE_FAILED) {
                throw new BatchUpdateException("JDBC batch reported a failed command", counts);
            }
            // Some drivers cannot report affected rows: count one successful command, not a negative row count.
            total += count == Statement.SUCCESS_NO_INFO ? 1 : count;
        }
        return total;
    }

    /** Database modules may preserve a different physical representation for encoded values. */
    protected void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
        statement.setObject(index, value);
    }

    @FunctionalInterface
    public interface MaintenanceWork<T> {
        T apply(Connection connection) throws Exception;
    }

    /**
     * 数据迁移等基础设施专用独立连接，不参加业务事务。回调自行确定批次提交边界；
     * 退出时回滚未提交内容并恢复连接状态，不向业务暴露 DataSource。
     */
    public <T> T withMaintenanceConnection(MaintenanceWork<T> work) throws Exception {
        if (isOpenTransaction()
                || com.yulinlin.data.core.transaction.TransactionListenerManager.currentContext() != null
                || org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("数据维护操作不能在业务事务中执行");
        try (Connection connection = dataSource.getConnection()) {
            boolean auto = connection.getAutoCommit();
            Throwable failure = null;
            try {
                return work.apply(connection);
            } catch (Exception | Error error) {
                failure = error;
                throw error;
            } finally {
                try {
                    if (!connection.isClosed()) {
                        if (!connection.getAutoCommit()) connection.rollback();
                        if (connection.getAutoCommit() != auto) connection.setAutoCommit(auto);
                    }
                } catch (SQLException cleanup) {
                    // 回滚失败后禁止切换自动提交，避免意外提交未完成批次。
                    try { connection.abort(Runnable::run); }
                    catch (SQLException abort) { cleanup.addSuppressed(abort); }
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    /** 维护工具复用数据库专属读取/绑定策略，避免绕过时间和布尔值适配。 */
    public Object readMaintenanceValue(ResultSet rows, String label, int type) throws SQLException {
        return readColumn(rows, label, type);
    }

    public void bindMaintenanceValue(PreparedStatement statement, int index, Object value) throws SQLException {
        bindValue(statement, index, value);
    }

    public String maintenanceColumnName(java.lang.reflect.Field field) {
        var mapping = com.yulinlin.data.lang.reflection.AnnotationUtil.findAnnotation(
                field, com.yulinlin.data.core.anno.JoinField.class);
        String name = mapping != null && !mapping.name().isEmpty() ? mapping.name() : field.getName();
        return isMapUnderscoreToCamelCase()
                ? com.yulinlin.data.lang.util.StringUtil.javaToColumn(name) : name;
    }





    @SneakyThrows
    public List<IDataBuffer> executeSelectNode(Connection connection , SqlNode node){




      try (PreparedStatement preparedStatement =  connection.prepareStatement(node.getSql())){




          if(node.getList() != null){
              int index = 1;
              for (Object row : node.getList()) {

                  bindValue(preparedStatement, index, row);
                  index++;

              }

          }

            try (   ResultSet resultSet =  preparedStatement.executeQuery()){
              return      resultSetToBuffer(resultSet);
            }

        }


    }





    @Override
    public boolean ping() {

        return true;

    }



    @Override
    public void shutdown() {
        log.info("会话关闭 group={}", group());


    }



}

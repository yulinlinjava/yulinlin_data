package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.TransactionSession;
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


    public JdbcSession(DataSource dataSource) {
        super(dataSource);
        setParseManager(new com.yulinlin.jdbc.sql.SqlParseManager());
    }

    /** Keeps schema logging and execution in the owning Session. */
    protected int executeSchemaSql(Statement statement, String sql) throws SQLException {
        log.info("[{}][schema]\n{}", group(), sql);
        try {
            return statement.executeUpdate(sql);
        } catch (SQLException error) {
            log.error("[{}][schema] execution failed\n{}", group(), sql, error);
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
                    preparedStatement.setObject(index, row);
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





    @SneakyThrows
    public List<IDataBuffer> executeSelectNode(Connection connection , SqlNode node){




      try (PreparedStatement preparedStatement =  connection.prepareStatement(node.getSql())){




          if(node.getList() != null){
              int index = 1;
              for (Object row : node.getList()) {

                  preparedStatement.setObject(index, row);
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
        log.info("会话关闭："+group());


    }



}

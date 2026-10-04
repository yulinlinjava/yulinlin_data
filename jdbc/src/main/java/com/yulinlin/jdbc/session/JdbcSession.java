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
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 循环依赖解决
 */
@Slf4j
public class JdbcSession extends AbstractJdbcSession implements TransactionSession  {


    public JdbcSession(DataSource dataSource) {
        super(dataSource);
    }



    @SneakyThrows

    protected Integer executeUpdateNode(Connection connection, List<ParseResult> list) {

        SqlNodeList nodeList = SqlNodeList.newUpdate();

        try {
            List<SqlNode> collect = (List)list.stream().map(row -> row.getRequest()).collect(Collectors.toList());
            nodeList.addAll(collect);
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
        try (  PreparedStatement preparedStatement =  connection.prepareStatement(sql)){


            //for (List<SqlNode> group : dataGroup(entry.getValue())) {
            for (SqlNode node : nodeList) {
                int index = 1;


                for (Object row : node.getList()) {
                    if(row instanceof InputStream){
                        preparedStatement.setBlob(index,(InputStream)row );
                    }else {
                        preparedStatement.setObject(index, row);
                    }
                    index++;

                }
                preparedStatement.addBatch();
            }
            int[] vals = preparedStatement.executeBatch();

            for (int val : vals) {
                total += val;
            }

        }


            return total;

    }





    @SneakyThrows
    public List<IDataBuffer> executeSelectNode(Connection connection , SqlNode node){




      try (PreparedStatement preparedStatement =  connection.prepareStatement(node.getSql())){




          if(node.getList() != null){
              int index = 1;
              for (Object row : node.getList()) {

                  preparedStatement.setObject(index++,row);
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

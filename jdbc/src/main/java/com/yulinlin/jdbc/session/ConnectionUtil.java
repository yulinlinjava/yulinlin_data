package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConnectionUtil  {



    private static boolean debug = false;


    public static void setDebug(boolean debug) {
        ConnectionUtil.debug = debug;
    }




    @SneakyThrows
    public static Connection getSpringConnection(DataSource dataSource){
        Connection connection ;

        if(SessionUtil.route().loadBalanceList().size() > 1){
            return pool(dataSource).getConnection();
        }else {
            connection = DataSourceUtils.getConnection(dataSource);

        }



        return connection;

    }

   static ThreadLocal<Map<DataSource,ConnectionPool>> threadLocal =
           ThreadLocal.withInitial(() -> new HashMap<>());

   static int core = (Runtime.getRuntime().availableProcessors() / 2) + 1 ;

    public static ConnectionPool pool(DataSource dataSource){
        Map<DataSource, ConnectionPool> poolMap = threadLocal.get();
        return poolMap.computeIfAbsent(dataSource,(data) -> {
            return new ConnectionPool(data,core);
        });
    }




    public static void startTransaction() {
        if(debug){
            log.info("开始事务");
        }

    }

    @SneakyThrows
    public static void releaseSpringConnection(DataSource dataSource,Connection connection){

        if(SessionUtil.route().loadBalanceList().size() > 1){
            pool(dataSource).releaseConnection(connection);
        }else {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

    }



    public static void commitTransaction() {
        int size =0 ;
        for (Map.Entry<DataSource, ConnectionPool> entry : threadLocal.get().entrySet()) {
            ConnectionPool pool = entry.getValue();
            for (Connection connection : pool.getConnections()) {
                try {
                    connection.commit();

                }catch (Exception e){
                    log.error("提交事务异常",e);

                }
            }
            size+=pool.getConnections().size();
            pool.clear();
        }


        if(debug){
            log.info("事务提交,使用链接：{}",size);
        }
        threadLocal.get().clear();
    }

    public static void rollbackTransaction() {

        int size =0 ;
        for (Map.Entry<DataSource, ConnectionPool> entry : threadLocal.get().entrySet()) {
            ConnectionPool pool = entry.getValue();
            for (Connection connection : pool.getConnections()) {
                try {
                    connection.rollback();

                }catch (Exception e){
                    log.error("事务回滚异常",e);

                }
            }
            size+=pool.getConnections().size();
            pool.clear();
        }


        if(debug){
            log.info("事务回滚,使用链接：{}",size);

        }
        threadLocal.get().clear();
    }




    public static void setCore(int core) {
        ConnectionUtil.core = core;
    }
}

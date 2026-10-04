package com.yulinlin.jdbc.session;

import com.yulinlin.data.core.exception.NoticeException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Slf4j
public class ConnectionPool {

    private DataSource dataSource;

    private  LongAdder size = new LongAdder();

    private Node[] deque  ;

    private int length;

    public ConnectionPool(DataSource dataSource, int length) {
        this.dataSource = dataSource;
        this.length = length;
        this.deque = new Node[length];
    }
    @SneakyThrows
    private Node getAvailableNode(){

        for (int i = 0; i < deque.length; i++) {
            Node node = deque[i];
            if(node != null && node.ok){
                if(node.connection.isClosed()){
                    deque[i] = null;
                    size.decrement();
                }else {
                    return node;
                }

            }
        }
        return null;
    }

    private  boolean isFull(){
        return size.intValue() == deque.length;
    }

    @SneakyThrows
    public  synchronized   Connection getConnection(){
        Connection jdbcConnection = getJdbcConnection();
        if(jdbcConnection.isClosed()){
            throw new NoticeException("链接已关闭");
        }

        return jdbcConnection;
    }
    @SneakyThrows
    public  synchronized   Connection getJdbcConnection(){
        Node node = getAvailableNode();
        if(node != null){
            node.ok = false;
            return node.connection;
        }
        if(isFull()){
            this.wait();
            return getJdbcConnection();
        }
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        node = new Node(connection,false);
        deque[size.intValue()] =node;
        size.increment();
        return node.connection;
    }
     @SneakyThrows
    public  synchronized void releaseConnection(Connection connection){
         for (Node node : deque) {
             if(node != null && node.connection == connection){
                 node.ok = true;

             }
         }
         this.notifyAll();
    }

    public Collection<Connection> getConnections(){
        return Arrays.asList(deque).stream()
                .filter(row -> row != null)
                .map(row -> row.connection).collect(Collectors.toList());
    }
    public  void clear(){
        for (Node node : deque) {
            if(node != null){
                try {
                    node.connection.close();
                }catch (Exception e){
                    log.error("关闭链接异常",e);
                }

            }
        }
        for (int i = 0; i < deque.length; i++) {
            deque[i] = null;
        }

        this.size.reset();
    }


    static class Node{
        Connection connection;
        boolean ok;

        public Node(Connection connection) {
            this.connection = connection;
        }

        public Node(Connection connection, boolean ok) {
            this.connection = connection;
            this.ok = ok;
        }
    }

}

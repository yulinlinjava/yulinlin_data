package com.yulinlin.jdbc.session;

import java.util.*;

public class SqlNodeList {


    private boolean update;

   private static ThreadLocal<SqlNodeList> updateLocal = ThreadLocal.withInitial(() -> {
        return  new SqlNodeList(true);
    });

    private static  ThreadLocal<SqlNodeList> queryLocal = ThreadLocal.withInitial(() -> {
        return  new SqlNodeList(false);
    });

    private SqlNodeList(boolean update) {
        this.update = update;
    }

    public boolean isUpdate() {
        return update;
    }

    private Map<String,List<SqlNode>> map = new HashMap<>();

    public SqlNodeList addAll(List<SqlNode> nodes){

        for (SqlNode node : nodes) {
            add(node);
        }
        return this;
    }
    public SqlNodeList add(SqlNode node){
        List<SqlNode> nodes =   map.get(node.getSql());
        if(nodes == null){
            nodes = new LinkedList<>();
            map.put(node.getSql(),nodes);
        }
        nodes.add(node);
        return this;
    }


    public Map<String, List<SqlNode>> getBatch() {
        return map;
    }

    //是否批量
    public boolean isBatch(){

        return map.size() == 1;
    }

    public SqlNode getOne(){
        for (Map.Entry<String, List<SqlNode>> entry : map.entrySet()) {
            return entry.getValue().get(0);
        }

        return null;
    }

    public void close(){
        map.clear();
    }

    public static SqlNodeList newUpdate(){
        return SqlNodeList.updateLocal.get();

    }

    public static SqlNodeList newQuery(){
        return SqlNodeList.queryLocal.get();
    }

}

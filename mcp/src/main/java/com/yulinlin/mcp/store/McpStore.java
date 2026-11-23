package com.yulinlin.mcp.store;
import java.util.*;
/** Persistent, application-scoped storage. create must atomically reject existing keys across instances. */
public interface McpStore {
 default void initialize() { }
 <T> T get(String key,Class<T> type);
 <T> List<T> list(String kind,int page,int size,Class<T> type);
 void create(String key,String kind,Object value);
 void save(String key,String kind,Object value);
 void delete(String key);
 long count(String kind);
 static String id(){return UUID.randomUUID().toString().replace("-","");}
}

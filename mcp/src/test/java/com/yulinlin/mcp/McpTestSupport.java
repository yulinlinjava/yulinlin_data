package com.yulinlin.mcp;
import com.fasterxml.jackson.databind.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.security.auth.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.mock.web.MockHttpServletRequest;
class McpTestSupport {
 static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
 static final IUserDetails ADMIN=new User("admin"), READER=new User("reader");
 record User(String getUserId) implements IUserDetails {
  public String getUserId(){return getUserId;} public String getUsername(){return getUserId;} public Set<String> getRoles(){return Set.of();}public Set<String> getPermissions(){return Set.of();}public Object getAttribute(String name){return null;}
 }
 static class Policy implements McpAccessPolicy {volatile boolean use=true,manage=true,invoke=true;
  public boolean canUse(IUserDetails u){return use;}public boolean canManage(IUserDetails u){return manage;}public boolean canInvoke(IUserDetails u,String t){return invoke;}}
 static class MemoryStore implements McpStore {
  record Row(String kind,String payload,long time){}final ConcurrentMap<String,Row> rows=new ConcurrentHashMap<>();
  public <T>T get(String key,Class<T> type){var row=rows.get(key);try{return row==null?null:JSON.readValue(row.payload(),type);}catch(Exception e){throw new IllegalStateException(e);}}
  private Row row(String kind,Object value){try{return new Row(kind,JSON.writeValueAsString(value),System.nanoTime());}catch(Exception e){throw new IllegalStateException(e);}}
  public void create(String key,String kind,Object value){if(rows.putIfAbsent(key,row(kind,value))!=null)throw new IllegalStateException("duplicate");}
  public void save(String key,String kind,Object value){rows.put(key,row(kind,value));}public void delete(String key){rows.remove(key);}public long count(String kind){return rows.values().stream().filter(r->r.kind().equals(kind)).count();}
  public <T>List<T> list(String kind,int page,int size,Class<T> type){return rows.entrySet().stream().filter(e->e.getValue().kind().equals(kind)).sorted(Comparator.comparingLong((Map.Entry<String,Row> e)->e.getValue().time()).reversed()).skip((long)(page-1)*size).limit(size).map(e->get(e.getKey(),type)).toList();}
 }
 static McpProperties properties(){var p=new McpProperties();p.setApplicationId("test");return p;}
 static MockHttpServletRequest request(String token){var r=new MockHttpServletRequest();r.addHeader("Authorization","Bearer "+token);return r;}
}

package com.yulinlin.mcp;
import com.fasterxml.jackson.databind.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.security.auth.*;
import java.time.*;
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
 static class Time extends Clock {
  volatile long now=1000;
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return this;}
  public Instant instant(){return Instant.ofEpochMilli(now);}
  public long millis(){return now;}
 }
 static class MemoryStore implements McpStore {
  record Row(String kind,String payload,long time){}final ConcurrentMap<String,Row> rows=new ConcurrentHashMap<>();
  public <T>T get(String key,Class<T> type){var row=rows.get(key);try{return row==null?null:JSON.readValue(row.payload(),type);}catch(Exception e){throw new IllegalStateException(e);}}
  private Row row(String kind,Object value){try{return new Row(kind,JSON.writeValueAsString(value),System.nanoTime());}catch(Exception e){throw new IllegalStateException(e);}}
  public void create(String key,String kind,Object value){if(rows.putIfAbsent(key,row(kind,value))!=null)throw new IllegalStateException("duplicate");}
  public void save(String key,String kind,Object value){rows.put(key,row(kind,value));}public void delete(String key){rows.remove(key);}public long count(String kind){return rows.values().stream().filter(r->r.kind().equals(kind)).count();}
  public <T>List<T> list(String kind,int page,int size,Class<T> type){return rows.entrySet().stream().filter(e->e.getValue().kind().equals(kind)).sorted(Comparator.comparingLong((Map.Entry<String,Row> e)->e.getValue().time()).reversed()).skip((long)(page-1)*size).limit(size).map(e->get(e.getKey(),type)).toList();}
 }
 /** 内存令牌存储，语义与 OrmMcpTokenStore 一致（version 乐观锁、markUsed 单调且每分钟一次）。 */
 static class MemoryTokenStore implements McpTokenStore {
  final ConcurrentMap<String,McpToken> rows=new ConcurrentHashMap<>();
  public McpToken findById(String id){return id==null?null:rows.get(id);}
  public McpToken findByKeyId(String keyId){return rows.values().stream().filter(t->t.keyId().equals(keyId)).findFirst().orElse(null);}
  private java.util.stream.Stream<McpToken> where(McpTokenQuery q){
   return rows.values().stream().filter(t->q.ownerId()==null||q.ownerId().equals(t.ownerId()))
     .filter(t->q.keyword()==null||t.name().contains(q.keyword())||t.keyId().equals(q.keyword()))
     .filter(t->q.status()==null||t.effectiveStatus(q.now())==q.status());
  }
  public List<McpToken> list(McpTokenQuery q){return where(q).sorted(Comparator.comparingLong(McpToken::createdAt).reversed().thenComparing(McpToken::id,Comparator.reverseOrder())).skip((long)(q.page()-1)*q.size()).limit(q.size()).toList();}
  public long count(McpTokenQuery q){return where(q).count();}
  public long countUnrevoked(String ownerId){return rows.values().stream().filter(t->t.ownerId().equals(ownerId)&&t.status()!=McpTokenStatus.REVOKED).count();}
  public synchronized void insert(McpToken t){if(findByKeyId(t.keyId())!=null||rows.putIfAbsent(t.id(),t)!=null)throw new IllegalStateException("duplicate");}
  public synchronized boolean update(McpToken t,long expected){var old=rows.get(t.id());if(old==null||old.version()!=expected)return false;rows.put(t.id(),t.toBuilder().lastUsedAt(old.lastUsedAt()).lastUsedIp(old.lastUsedIp()).build());return true;}
  public synchronized void markUsed(String id,long at,String ip){var old=rows.get(id);if(old!=null&&old.lastUsedAt()<=at-60_000L)rows.put(id,old.toBuilder().lastUsedAt(at).lastUsedIp(ip).build());}
  public void delete(String id){rows.remove(id);}
 }
 /** 令牌服务 + 认证的完整组装，缓存关闭以便测试立即看到变化。 */
 static class TokenEnv {
  final MemoryStore store=new MemoryStore();
  final MemoryTokenStore tokens=new MemoryTokenStore();
  final List<McpTokenEvent> events=new CopyOnWriteArrayList<>();
  final Time time=new Time();
  final Policy policy;
  final McpProperties properties=properties();
  final McpTokenService service;
  final TokenAuthenticator authenticator;
  final McpAuthentication auth;
  TokenEnv(){this(new Policy(),id->new User(id),null);}
  TokenEnv(Policy policy,McpIdentityProvider identities,McpToolRegistry registry){
   this.policy=policy;properties.getToken().setCacheSeconds(0);
   service=new McpTokenService(tokens,identities,policy,()->registry,properties.getToken(),events::add,time);
   authenticator=new TokenAuthenticator(service,time);
   auth=new McpAuthentication(List.of(authenticator),store,identities,policy);
   auth.enable(true);
  }
  McpTokenService.Actor actor(IUserDetails user){return McpTokenService.Actor.of(user);}
  McpTokenView.Issued issue(IUserDetails user,String name,List<String> scope){
   return service.issue(actor(user),new McpTokenService.IssueRequest(name,null,null,null,scope,null,null,null));
  }
  McpPrincipal login(String token){return auth.authenticate(request(token));}
 }
 static McpProperties properties(){var p=new McpProperties();p.setApplicationId("test");return p;}
 static MockHttpServletRequest request(String token){var r=new MockHttpServletRequest();r.setRemoteAddr("127.0.0.1");r.addHeader("Authorization","Bearer "+token);return r;}
 static McpPrincipal principal(String credentialId){return new McpPrincipal(TokenAuthenticator.TYPE,credentialId,"test",ADMIN.getUserId(),null,null);}
 static McpCallContext ctx(String credentialId){return new McpCallContext("test",principal(credentialId),ADMIN,"request","");}
}

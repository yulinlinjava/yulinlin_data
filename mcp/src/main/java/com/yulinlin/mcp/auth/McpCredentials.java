package com.yulinlin.mcp.auth;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.security.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
public class McpCredentials {
 private final McpStore store; private final McpIdentityProvider identities; private final McpAccessPolicy policy;
 private final SecureRandom random=new SecureRandom();
 private final Clock clock;
 public McpCredentials(McpStore store,McpIdentityProvider identities,McpAccessPolicy policy){this(store,identities,policy,Clock.systemUTC());}
 public McpCredentials(McpStore store,McpIdentityProvider identities,McpAccessPolicy policy,Clock clock){this.store=store;this.identities=identities;this.policy=policy;this.clock=Objects.requireNonNull(clock);}
 public Credential byId(String id){return store.list("credential",1,2000,Credential.class).stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->unauthorized());}
 public boolean enabled(){var s=store.get("settings",Settings.class);return s!=null&&s.enabled();}
 public void enable(boolean enabled){store.save("settings","settings",new Settings(enabled));}
 public Created create(IUserDetails owner,String name){
  return create(owner,name,null);
 }
 /** null/0 means unlimited; a positive value is an absolute epoch-millisecond deadline. */
 public Created create(IUserDetails owner,String name,Long expiresAt){
  if(!policy.canUse(owner))throw new McpException(403,"所属账号无 MCP 使用权限");
  if(name==null||name.isBlank()||name.length()>60||name.chars().anyMatch(c->c<32))throw new IllegalArgumentException("名称限 1–60 字符");
  long now=clock.millis(),deadline=expiresAt==null?0:expiresAt;
  if(deadline<0||(deadline>0&&deadline<=now))throw new IllegalArgumentException("expiresAt 必须为 0、null 或未来的毫秒时间戳");
  if(store.count("credential")>=2000)throw new McpException(429,"凭证记录已满");
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token="ylmcp_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var c=new Credential(McpStore.id(),name.strip(),hash(token),identities.accountId(owner),null,now,deadline,false);
  store.create("credential-"+c.digest(),"credential",c);return new Created(info(c),token);
 }
 public List<Info> list(){return store.list("credential",1,2000,Credential.class).stream().map(this::info).toList();}
 public void revoke(String id){var c=store.list("credential",1,2000,Credential.class).stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("凭证不存在"));store.save("credential-"+c.digest(),"credential",new Credential(c.id(),c.name(),c.digest(),c.userId(),c.sessionId(),c.createdAt(),c.expiresAt(),true));}
 public Credential authenticate(HttpServletRequest request){
  if(!enabled())throw new McpException(403,"MCP 已暂停");
  var hs=Collections.list(request.getHeaders("Authorization"));
  if(hs.size()!=1||!hs.getFirst().regionMatches(true,0,"Bearer ",0,7))throw unauthorized();
  String token=hs.getFirst().substring(7);if(token.isBlank()||token.length()>512||token.chars().anyMatch(Character::isWhitespace))throw unauthorized();
  var c=store.get("credential-"+hash(token),Credential.class);if(c==null)throw unauthorized();requireValid(c);return c;
 }
 public IUserDetails requireValid(Credential c){
  var current=store.get("credential-"+c.digest(),Credential.class);
  if(!enabled()||current==null||current.revoked()||!withinValidity(current))throw unauthorized();
  var user=identities.load(current.userId());if(user==null||!policy.canUse(user))throw unauthorized();return user;
 }
 private boolean withinValidity(Credential c){return c.expiresAt()==0||c.expiresAt()>clock.millis();}
 private Info info(Credential c){var user=identities.load(c.userId());return new Info(c.id(),c.name(),c.createdAt(),c.expiresAt(),c.revoked(),!c.revoked()&&withinValidity(c)&&user!=null&&policy.canUse(user));}
 private static McpException unauthorized(){return new McpException(401,"MCP 凭证或当前账号授权无效");}
 public static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public record Settings(boolean enabled){}
 /** Missing/null deadlines are normalized to 0; positive deadlines are always enforced, including legacy records. */
 public record Credential(String id,String name,String digest,String userId,String sessionId,long createdAt,Long expiresAt,boolean revoked){
  public Credential { expiresAt=expiresAt==null?0L:expiresAt; }
  public Credential(String id,String name,String digest,String userId,String sessionId,long createdAt,long expiresAt,boolean revoked){this(id,name,digest,userId,sessionId,createdAt,Long.valueOf(expiresAt),revoked);}
  @Override public String toString(){return "McpCredential[redacted]";}
 }
 public record Info(String id,String name,long createdAt,long expiresAt,boolean revoked,boolean active){}
 public record Created(Info credential,String token){@Override public String toString(){return "McpCreated[redacted]";}}
}

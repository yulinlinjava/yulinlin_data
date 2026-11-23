package com.yulinlin.mcp;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.security.auth.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class McpSecurityTest extends McpTestSupport {
 @AfterEach void clear(){SecurityContext.clear();}
 @Test void persistentCredentialsRecheckCurrentAccountPolicyAndRevocation(){
  var store=new MemoryStore();var policy=new Policy();var active=new AtomicBoolean(true);McpIdentityProvider identity=id->active.get()?ADMIN:null;
  var first=new McpCredentials(store,identity,policy);first.enable(true);var created=first.create(ADMIN,"local");
  assertFalse(store.rows.values().toString().contains(created.token()));assertFalse(created.toString().contains(created.token()));
  var restarted=new McpCredentials(store,identity,policy);assertEquals(created.credential().id(),restarted.authenticate(request(created.token())).id());
  active.set(false);assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));active.set(true);
  policy.use=false;assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));policy.use=true;
  restarted.enable(false);assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));restarted.enable(true);
  restarted.revoke(created.credential().id());assertThrows(McpException.class,()->first.authenticate(request(created.token())));
 }
 @Test void legacyDigestSurvivesWithoutReissuingToken(){var store=new MemoryStore();var c=new McpCredentials(store,id->ADMIN,new Policy());c.enable(true);String token="ndmcp_test-only-old-token";var row=new McpCredentials.Credential("id","legacy",McpCredentials.hash(token),"admin","old-session",1,0,false);store.create("credential-"+row.digest(),"credential",row);assertEquals("id",c.authenticate(request(token)).id());}
 @Test void trustedCallScopesNestRestoreAndDoNotInheritAcrossThreads()throws Exception{
  var browser=new LoginSession("browser",null,READER,"test");SecurityContext.set(browser);
  assertThrows(IllegalStateException.class,()->SecurityContext.callAs(new LoginSession(null,null,ADMIN,"mcp"),()->{
   assertSame(ADMIN,SecurityContext.requireUser());
   SecurityContext.callAs(browser,()->{assertSame(READER,SecurityContext.requireUser());return null;});assertSame(ADMIN,SecurityContext.requireUser());
   try(var pool=Executors.newVirtualThreadPerTaskExecutor()){assertNull(pool.submit(SecurityContext::getUser).get());}
   throw new IllegalStateException("business failure");
  }));assertSame(READER,SecurityContext.requireUser());
 }
 @Test void trustedScopeRestoresExternalResolverAfterFailure()throws Exception{
  class Manager implements ISecurityManager,SecurityContextResolver {
   int resolutions;
   public LoginSession resolveContext(LoginSession old){resolutions++;return new LoginSession(null,null,READER,"external");}
   public void logout(){SecurityContext.clear();}public IUserDetails getUserDetails(String id){return READER;}public IUserDetails refresh(String id){return READER;}
  }
  var manager=new Manager();var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();beans.registerSingleton("manager",manager);
  var util=new SecurityUtil(beans.getBeanProvider(ISecurityManager.class));util.afterSingletonsInstantiated();
  try{
   assertSame(READER,SecurityContext.requireUser());int before=manager.resolutions;
   assertThrows(IllegalArgumentException.class,()->SecurityContext.callAs(new LoginSession(null,null,ADMIN,"mcp"),()->{
    assertSame(ADMIN,SecurityUtil.requireUser());assertEquals(before,manager.resolutions);throw new IllegalArgumentException("failure");
   }));
   assertSame(READER,SecurityUtil.requireUser());assertEquals(before+1,manager.resolutions);
  }finally{util.destroy();}
 }
}

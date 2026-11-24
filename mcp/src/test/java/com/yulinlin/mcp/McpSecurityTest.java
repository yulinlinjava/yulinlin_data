package com.yulinlin.mcp;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.security.auth.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class McpSecurityTest extends McpTestSupport {
 @AfterEach void clear(){SecurityContext.clear();}
 @Test void persistentTokensRecheckCurrentAccountPolicyAndRevocation(){
  var active=new AtomicBoolean(true);McpIdentityProvider identity=id->active.get()?ADMIN:null;
  var first=new TokenEnv(new Policy(),identity,null);var created=first.issue(ADMIN,"local",null);
  var secret=com.yulinlin.mcp.token.McpTokenCodec.parse(created.token()).secret();
  assertTrue(first.tokens.rows.values().stream().noneMatch(t->t.tokenHash().contains(secret)||t.toString().contains(secret)));
  assertFalse(created.toString().contains(created.token()));
  var restartedService=new com.yulinlin.mcp.token.McpTokenService(first.tokens,identity,first.policy,()->null,first.properties.getToken(),e->{},first.time);
  var restarted=new McpAuthentication(List.of(new com.yulinlin.mcp.token.TokenAuthenticator(restartedService,first.time)),first.store,identity,first.policy);
  assertEquals(created.credential().id(),restarted.authenticate(request(created.token())).credentialId());
  active.set(false);assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));active.set(true);
  first.policy.use=false;assertThrows(McpException.class,()->restarted.authenticate(request(created.token())));first.policy.use=true;
  restarted.enable(false);assertThrows(McpException.class,()->first.login(created.token()));restarted.enable(true);
  restartedService.revoke(com.yulinlin.mcp.token.McpTokenService.Actor.of(ADMIN),created.credential().id());assertThrows(McpException.class,()->first.login(created.token()));
 }
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

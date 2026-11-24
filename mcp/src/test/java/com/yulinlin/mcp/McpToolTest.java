package com.yulinlin.mcp;
import com.yulinlin.mcp.tool.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class McpToolTest extends McpTestSupport {
 @Test void schemaValidationAndPermissionAreEnforcedBeforeHandler()throws Exception{
  var calls=new AtomicInteger();var policy=new Policy();var schema=JSON.readTree("{\"type\":\"object\",\"required\":[\"n\"],\"properties\":{\"n\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":3}},\"additionalProperties\":false}");
  var tool=new McpToolDefinition("test","test",schema,true,(c,a)->calls.incrementAndGet());var r=new McpToolRegistry(List.of(()->List.of(tool)),policy);var c=ctx("credential");
  assertEquals(1,r.list(c).size());assertEquals(1,r.call(c,"test",JSON.readTree("{\"n\":2}")));
  for(String invalid:List.of("{}","{\"n\":4}","{\"n\":\"2\"}","{\"n\":2,\"extra\":1}"))assertThrows(IllegalArgumentException.class,()->r.call(c,"test",JSON.readTree(invalid)));
  assertEquals(1,calls.get());policy.invoke=false;assertTrue(r.list(c).isEmpty());assertThrows(McpException.class,()->r.call(c,"test",JSON.readTree("{\"n\":2}")));assertEquals(1,calls.get());
 }
 @Test void duplicateNamesFailAndExplicitAdditionalPropertiesRemainSupported()throws Exception{
  var t=new McpToolDefinition("map","map",JSON.readTree("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}"),true,(c,a)->a);
  assertThrows(IllegalArgumentException.class,()->new McpToolRegistry(List.of(()->List.of(t,t)),new Policy()));
  var r=new McpToolRegistry(List.of(()->List.of(t)),new Policy());var c=ctx("c");assertNotNull(r.call(c,"map",JSON.readTree("{\"custom\":2}")));
 }
}

package com.yulinlin.mcp;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.tool.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class McpAnnotatedToolTest extends McpTestSupport {
    ApplicationContextRunner runner(Class<?> tools,java.util.function.Supplier<?> supplier) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
            .withPropertyValues("yulinlin.mcp.enabled=true","yulinlin.mcp.application-id=test")
            .withBean(ObjectMapper.class,()->JSON).withBean(McpStore.class,MemoryStore::new).withBean(com.yulinlin.mcp.token.McpTokenStore.class,MemoryTokenStore::new)
            .withBean(McpAccessPolicy.class,Policy::new).withBean(McpIdentityProvider.class,()->id->ADMIN)
            .withBean("businessTools",Object.class,supplier::get);
    }
    static McpCallContext context(){return ctx("credential");}
    public record Item(@NotBlank @Schema(description="商品编号",example="item-1") String id){}
    public record Request(
        @NotBlank @JsonProperty("order_id") @Schema(description="业务订单编号",example="order-1") String id,
        @Valid @NotEmpty @Size(max=3) @Schema(description="订单商品") List<Item> items,
        @Min(1) @Max(10) @Schema(description="最多返回条数",defaultValue="2") Integer limit){}
    public record Result(@Schema(description="订单编号") String id,@Schema(description="实际条数") int count){}
    public record Page<T>(@Schema(description="结果列表") List<T> items){}
    public static class Tools {
        final AtomicInteger calls=new AtomicInteger();
        @McpTool(name="orders",description="查询订单",readOnly=true)
        public Page<Result> query(Request request){calls.incrementAndGet();return new Page<>(List.of(new Result(request.id(),request.limit()==null?2:request.limit())));}
        @McpTool(name="ping",description="读取服务状态",readOnly=true)
        public String ping(){return "ok";}
        @McpTool(name="nothing",description="无返回值示例") public void nothing(){}
    }
    @Test void automaticallyRegistersDtosGenericsAnnotationsAndNoArgumentMethods() {
        var service=new Tools();runner(Tools.class,()->service).run(c->{
            assertThat(c).hasNotFailed();var registry=c.getBean(McpToolRegistry.class);
            var descriptor=registry.list(context()).stream().filter(t->t.get("name").equals("orders")).findFirst().orElseThrow();
            JsonNode input=(JsonNode)descriptor.get("inputSchema");
            assertEquals("业务订单编号",input.path("properties").path("order_id").path("description").asText());
            assertTrue(input.path("required").toString().contains("order_id"));
            assertEquals("order-1",input.path("properties").path("order_id").path("examples").get(0).asText());
            assertTrue(input.toString().contains("商品编号"));assertFalse(input.path("properties").has("id"));
            assertEquals(2,input.path("properties").path("limit").path("default").asInt());
            var value=registry.call(context(),"orders",JSON.readTree("{\"order_id\":\"o\",\"items\":[{\"id\":\"i\"}]}"));
            assertEquals(2,((Page<Result>)value).items().getFirst().count());
            var output=JSON.createObjectNode();output.set("data",JSON.valueToTree(value));registry.validateOutput("orders",output);
            assertTrue(descriptor.get("outputSchema").toString().contains("订单编号"));
            assertEquals("ok",registry.call(context(),"ping",JSON.createObjectNode()));
            assertNull(registry.call(context(),"nothing",JSON.createObjectNode()));
            assertThrows(IllegalArgumentException.class,()->registry.call(context(),"ping",JSON.readTree("{\"extra\":1}")));
            for(String bad:List.of("{\"order_id\":\"o\",\"items\":[]}","{\"order_id\":\" \",\"items\":[{\"id\":\"i\"}]}","{\"order_id\":\"o\",\"items\":[{\"id\":\" \"}]}","{\"order_id\":\"o\",\"items\":[{\"id\":\"i\"}],\"limit\":11}","{\"order_id\":\"o\",\"items\":[{\"id\":\"i\"}],\"extra\":1}"))
                assertThrows(IllegalArgumentException.class,()->registry.call(context(),"orders",JSON.readTree(bad)));
            assertEquals(1,service.calls.get());
        });
    }
    public interface Contract { String execute(Item input); }
    public static class Proxied implements Contract {
        @McpTool(name="proxied",description="代理方法",readOnly=true)
        public String execute(Item input){return input.id();}
    }
    @Test void invokesJdkAndClassProxiesInsteadOfBypassingAdvice() {
        for(boolean classProxy:new boolean[]{false,true}) {
            var advice=new AtomicInteger();var factory=new ProxyFactory(new Proxied());factory.setProxyTargetClass(classProxy);
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->{advice.incrementAndGet();return invocation.proceed();});
            runner(Contract.class,factory::getProxy).run(c->{
                assertThat(c).hasNotFailed();int before=advice.get();
                assertEquals("id",c.getBean(McpToolRegistry.class).call(context(),"proxied",JSON.readTree("{\"id\":\"id\"}")));
                assertEquals(before+1,advice.get());
            });
        }
    }
    public static class ContextTools {
        @McpTool(name="whoami",description="读取调用方与令牌属性",readOnly=true)
        public String whoami(McpCallContext context,Item item){return context.credentialId()+":"+item.id()+":"+(McpCallContext.current()==context);}
        @McpTool(name="context_only",description="只要上下文",readOnly=true)
        public String contextOnly(McpCallContext context){return context.user().getUserId();}
    }
    public static class TwoContexts {
        @McpTool(name="two",description="两个上下文参数")public String result(McpCallContext a,McpCallContext b){return "";}
    }
    @Test void callContextCanBeInjectedNextToTheRequestDto() {
        runner(ContextTools.class,ContextTools::new).run(c->{
            assertThat(c).hasNotFailed();var r=c.getBean(McpToolRegistry.class);
            assertEquals("credential:i:true",r.call(context(),"whoami",JSON.readTree("{\"id\":\"i\"}")));
            assertEquals("admin",r.call(context(),"context_only",JSON.createObjectNode()));
            assertFalse(r.list(context()).toString().contains("McpCallContext"));
            assertNull(McpCallContext.current());
        });
        runner(TwoContexts.class,TwoContexts::new).run(c->assertThat(c).hasFailed());
    }
    public static class Duplicate {
        @McpTool(name="same",description="a")public String first(){return "a";}
        @McpTool(name="same",description="b")public String second(){return "b";}
    }
    public static class Dynamic {
        @McpTool(name="dynamic",description="不能推断")public Object result(){return null;}
    }
    public static class ManyArgs {
        @McpTool(name="many",description="参数过多")public String result(Item one,Item two){return "";}
    }
    @Test void invalidContractsAndDuplicateNamesFailStartup() {
        runner(Duplicate.class,Duplicate::new).run(c->assertThat(c).hasFailed());
        runner(Dynamic.class,Dynamic::new).run(c->assertThat(c).hasFailed());
        runner(ManyArgs.class,ManyArgs::new).run(c->assertThat(c).hasFailed());
        runner(Dynamic.class,Dynamic::new).withPropertyValues("yulinlin.mcp.enabled=false")
            .run(c->assertThat(c).hasNotFailed().doesNotHaveBean(McpToolRegistry.class));
    }
    public static class Base<T> {
        @McpTool(name="inherited",description="继承的泛型方法",readOnly=true)public T echo(T request){return request;}
    }
    public static class Inherited extends Base<Item>{}
    public static class Mutable {
        private String value;
        @NotBlank @Schema(description="通过 setter 绑定的属性") public String getValue(){return value;}
        public void setValue(String value){this.value=value;}
    }
    public static class MutableTools {
        @McpTool(name="mutable",description="普通 JavaBean",readOnly=true)public Mutable echo(Mutable request){return request;}
    }
    @Test void inheritedGenericMethodsAndMutableBeansUseResolvedPropertyTypes() {
        runner(Inherited.class,Inherited::new).run(c->{
            assertThat(c).hasNotFailed();var r=c.getBean(McpToolRegistry.class);
            assertEquals(new Item("i"),r.call(context(),"inherited",JSON.readTree("{\"id\":\"i\"}")));
        });
        runner(MutableTools.class,MutableTools::new).run(c->{
            assertThat(c).hasNotFailed();var r=c.getBean(McpToolRegistry.class);
            assertEquals("v",((Mutable)r.call(context(),"mutable",JSON.readTree("{\"value\":\"v\"}"))).getValue());
            assertThrows(IllegalArgumentException.class,()->r.call(context(),"mutable",JSON.readTree("{\"value\":\" \"}")));
        });
    }
    public static class Node {
        @Schema(description="节点名称")public String name;
        @Valid @Schema(description="子节点")public List<Node> children;
        @JsonIgnore public String secret;
    }
    public static class Recursive {
        @McpTool(name="tree",description="树结构",readOnly=true)public Node tree(Node node){return node;}
    }
    public record Dictionary(@Valid @NotEmpty Map<String,Item> entries){}
    public static class DictionaryTools {
        @McpTool(name="dictionary",description="明确值类型的映射",readOnly=true)
        public Map<String,Item> query(Dictionary request){return request.entries();}
    }
    @Test void typedMapsDescribeAndValidateDynamicValues() {
        runner(DictionaryTools.class,DictionaryTools::new).run(c->{
            assertThat(c).hasNotFailed();var r=c.getBean(McpToolRegistry.class);
            var data=r.call(context(),"dictionary",JSON.readTree("{\"entries\":{\"key\":{\"id\":\"i\"}}}"));
            assertEquals(Map.of("key",new Item("i")),data);
            assertThrows(IllegalArgumentException.class,()->r.call(context(),"dictionary",JSON.readTree("{\"entries\":{}}")));
            assertThrows(IllegalArgumentException.class,()->r.call(context(),"dictionary",JSON.readTree("{\"entries\":{\"key\":{\"id\":\" \"}}}")));
            var payload=JSON.createObjectNode();payload.set("data",JSON.valueToTree(data));r.validateOutput("dictionary",payload);
        });
    }
    @Test void recursiveDtosUseLocalReferencesAndIgnoredFieldsStayHidden() {
        runner(Recursive.class,Recursive::new).run(c->{
            assertThat(c).hasNotFailed();var r=c.getBean(McpToolRegistry.class);
            var data=r.call(context(),"tree",JSON.readTree("{\"name\":\"root\",\"children\":[{\"name\":\"child\"}]}"));
            assertFalse(r.list(context()).toString().contains("secret"));
            var payload=JSON.createObjectNode();payload.set("data",JSON.valueToTree(data));r.validateOutput("tree",payload);
        });
    }
}

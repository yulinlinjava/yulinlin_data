package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RiskJsonBodyTest {
    record PlayRequest(String videoId, String userId) {}

    @RestController static class Videos {
        final AtomicInteger calls = new AtomicInteger();
        @PostMapping("/play") @ApiRisk(value="video", resourceBody="videoId")
        String play(@RequestBody PlayRequest body) { calls.incrementAndGet(); return body.videoId(); }
        @PostMapping("/nested") @ApiRisk(value="video", resourceBody="video.id")
        Object nested(@RequestBody Map<String,Object> body) { calls.incrementAndGet(); return body; }
        @PostMapping("/string") @ApiRisk(value="video", resourceBody="videoId")
        String text(@RequestBody String body) { calls.incrementAndGet(); return body; }
        @PostMapping("/entity") @ApiRisk(value="video", resourceBody="videoId")
        Object entity(HttpEntity<PlayRequest> body) { calls.incrementAndGet(); return body.getBody(); }
        @PostMapping("/optional") @ApiRisk(value="video", resourceBody="videoId")
        String optional(@RequestBody(required=false) PlayRequest body) { calls.incrementAndGet(); return "ok"; }
        @PostMapping("/public-json")
        String publicJson(@RequestBody PlayRequest body) { return body.videoId(); }
        @GetMapping("/invalid") @ApiRisk(value="video", resourceBody="videoId")
        String invalid() { calls.incrementAndGet(); return "unexpected"; }
    }

    final RiskService service = mock(RiskService.class);
    final ISecurityManager manager = mock(ISecurityManager.class);
    final IUserDetails user = mock(IUserDetails.class);
    final Videos controller = new Videos();
    final RiskWebMvcConfigurer checks = new RiskWebMvcConfigurer(service, () -> manager);
    final RiskRequestBodyAdvice advice = new RiskRequestBodyAdvice(checks, new ObjectMapper());
    final RiskWebTest.Registry registry = new RiskWebTest.Registry();

    RiskJsonBodyTest() {
        when(user.getUserId()).thenReturn("logged-in-user");
        when(manager.getUserDetails()).thenReturn(user);
        when(service.checkAndRecord(any())).thenReturn(new Decision(true, Status.ALLOWED, 0, List.of()));
        checks.addInterceptors(registry);
    }

    MockMvc mvc(Object... additionalAdvice) {
        var all = new java.util.ArrayList<>(List.of(additionalAdvice)); all.add(advice);
        return MockMvcBuilders.standaloneSetup(controller).addInterceptors(registry.interceptor())
                .setControllerAdvice(all.toArray()).build();
    }

    @Test void dtoBodyReachesControllerAndIdentityCannotBeSpoofedOrCountedTwice() throws Exception {
        var result = mvc().perform(post("/play").contentType(MediaType.APPLICATION_JSON)
                .param("videoId", "wrong-query-value").content("{\"videoId\":\"v123\",\"userId\":\"victim\"}"))
                .andExpect(status().isOk()).andExpect(content().string("v123")).andReturn();
        assertEquals(1, controller.calls.get());
        registry.interceptor().preHandle(result.getRequest(), result.getResponse(),
                new HandlerMethod(controller, Videos.class.getDeclaredMethod("play", PlayRequest.class)));
        verify(service, times(1)).checkAndRecord(new BehaviorEvent("logged-in-user", "video", "v123"));
    }

    @Test void nestedNumericIdAndRawJsonStringAndHttpEntityAreSupported() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/nested").contentType("application/problem+json").content("{\"video\":{\"id\":42}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.video.id").value(42));
        mvc.perform(post("/string").contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"raw\"}"))
                .andExpect(status().isOk()).andExpect(content().string("{\"videoId\":\"raw\"}"));
        mvc.perform(post("/entity").contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"entity\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videoId").value("entity"));
        for (String resource : List.of("42", "raw", "entity"))
            verify(service).checkAndRecord(new BehaviorEvent("logged-in-user", "video", resource));
        assertEquals(3, controller.calls.get());
    }

    @Test void blockingStopsControllerAndAnonymousRequestIsRejectedBeforeJsonParsing() throws Exception {
        when(service.checkAndRecord(any())).thenReturn(new Decision(false, Status.BLOCKED, 1000, List.of()));
        var mvc = mvc();
        mvc.perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"v\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        assertEquals(0, controller.calls.get());
        when(manager.getUserDetails()).thenReturn(null);
        mvc.perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("broken"))
                .andExpect(status().isUnauthorized());
        verify(service, times(1)).checkAndRecord(any());
    }

    @Test void emptyMalformedNonJsonAndNonScalarResourcesCannotInvokeBusiness() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/optional").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/string").contentType(MediaType.TEXT_PLAIN).content("{\"videoId\":\"v\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String value : List.of("{}", "[]", "true", "\"\""))
            mvc.perform(post("/nested").contentType(MediaType.APPLICATION_JSON).content("{\"video\":{\"id\":" + value + "}}"))
                    .andExpect(status().isBadRequest());
        assertEquals(0, controller.calls.get());
        verifyNoInteractions(service);
    }

    @Test void absentResourceStillCountsAndUnannotatedJsonDoesNot() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/public-json").contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"public\"}"))
                .andExpect(status().isOk()).andExpect(content().string("public"));
        verify(service).checkAndRecord(new BehaviorEvent("logged-in-user", "video", null));
        verifyNoMoreInteractions(service);
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(get("/invalid")));
    }

    @Test void encryptedJsonIsCheckedAfterExistingCryptoAdviceDecryptsIt() throws Exception {
        var codec = mock(CryptoCodec.class);
        when(codec.decrypt(any(), eq("ciphertext"))).thenReturn("{\"videoId\":\"decrypted-video\"}");
        var crypto = new CryptoRequestBodyAdvice(codec, headers -> null, new SecurityCryptoProperties());
        mvc(crypto).perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("ciphertext"))
                .andExpect(status().isOk()).andExpect(content().string("decrypted-video"));
        verify(service).checkAndRecord(new BehaviorEvent("logged-in-user", "video", "decrypted-video"));
        assertEquals(1, controller.calls.get());
    }

    @Test void autoConfigurationRegistersAdviceInRealMvcContext() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        WebMvcAutoConfiguration.class, RiskAutoConfiguration.class))
                .withBean(RiskService.class, () -> service).withBean(ISecurityManager.class, () -> manager)
                .withBean(Videos.class, () -> controller)
                .withPropertyValues("yulinlin.security.risk.enabled=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(RiskRequestBodyAdvice.class));
                    MockMvcBuilders.webAppContextSetup(context).build()
                            .perform(post("/play").contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"auto\"}"))
                            .andExpect(status().isOk()).andExpect(content().string("auto"));
                    verify(service).checkAndRecord(new BehaviorEvent("logged-in-user", "video", "auto"));
                });
    }
}

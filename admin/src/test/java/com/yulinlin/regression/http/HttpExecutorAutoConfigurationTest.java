package com.yulinlin.regression.http;

import com.yulinlin.data.core.http.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** 只验证 Bean 替换/选择和静态生命周期，不配置 YAML 属性、不发送网络请求。 */
@ResourceLock("HttpUtil")
class HttpExecutorAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpAutoConfiguration.class));
    private final Map<String, Object> previous = new HashMap<>();

    @BeforeEach
    void isolateStaticBinding() {
        for (String name : new String[]{"defaultClient", "springClient", "springOwner", "springStopped"}) {
            previous.put(name, ReflectionTestUtils.getField(HttpUtil.class, name));
            ReflectionTestUtils.setField(HttpUtil.class, name, name.equals("springStopped") ? false : null);
        }
    }

    @AfterEach
    void restoreStaticBinding() {
        previous.forEach((name, value) -> ReflectionTestUtils.setField(HttpUtil.class, name, value));
    }

    @Test
    void defaultUsesCurrentImplementationAndBindsTheSameClient() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HttpExecutor.class).hasSingleBean(HttpRequestClient.class);
            assertInstanceOf(RestClientHttpExecutor.class, context.getBean(HttpExecutor.class));
            assertSame(context.getBean(HttpRequestClient.class), HttpUtil.getClient());
            assertSame(context.getBean(HttpExecutor.class), HttpUtil.getClient().getExecutor());
        });
        assertThrows(IllegalStateException.class, HttpUtil::getClient);
    }

    @Test
    void customBeanReplacesDefaultForInjectedStaticAndTimeoutCallsAndClosesOnce() {
        var executor = new HttpExecutorTest.RecordingExecutor();
        runner.withBean("customExecutor", HttpExecutor.class, () -> executor).run(context -> {
            assertThat(context).hasSingleBean(HttpExecutor.class);
            assertThat(context).doesNotHaveBean("httpExecutor");
            HttpRequestClient injected = context.getBean(HttpRequestClient.class);
            assertSame(injected, HttpUtil.getClient());
            injected.get("https://example.test").execute();
            HttpUtil.get("https://example.test").execute();
            try (HttpRequestClient view = HttpUtil.withTimeout(Duration.ofSeconds(4))) {
                assertSame(executor, view.getExecutor());
                view.get("https://example.test").execute();
            }
            assertEquals(3, executor.executeCount);
            assertEquals(Duration.ofSeconds(4), executor.requests.getLast().getTimeout());
            injected.close();
            assertEquals(0, executor.closeCount);
            assertThrows(IllegalStateException.class,
                    () -> HttpUtil.setClient(new HttpRequestClient(new HttpExecutorTest.RecordingExecutor())));
        });
        assertEquals(1, executor.closeCount);
    }

    @Test
    void multipleExecutorsRequirePrimary() {
        runner.withBean("one", HttpExecutor.class, HttpExecutorTest.RecordingExecutor::new)
                .withBean("two", HttpExecutor.class, HttpExecutorTest.RecordingExecutor::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    boolean explained = false;
                    for (Throwable error = failure; error != null; error = error.getCause()) {
                        if (error.getMessage() != null && error.getMessage().contains("多个 HttpExecutor")) explained = true;
                    }
                    assertTrue(explained);
                });
    }

    @Test
    void primaryExecutorDrivesBothEntrypoints() {
        var selected = new HttpExecutorTest.RecordingExecutor();
        runner.withBean("other", HttpExecutor.class, HttpExecutorTest.RecordingExecutor::new)
                .withBean("selected", HttpExecutor.class, () -> selected, definition -> definition.setPrimary(true))
                .run(context -> assertSame(selected, HttpUtil.getClient().getExecutor()));
    }

    @Test
    void customClientBeanIsAlsoBoundToStaticEntrypoint() {
        HttpRequestClient custom = new HttpRequestClient(new HttpExecutorTest.RecordingExecutor());
        runner.withBean("customClient", HttpRequestClient.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpRequestClient.class);
                    assertThat(context).doesNotHaveBean(HttpExecutor.class);
                    assertSame(custom, HttpUtil.getClient());
                });
    }

    @Test
    void anotherContextCannotStealStaticBinding() {
        runner.withBean("one", HttpExecutor.class, HttpExecutorTest.RecordingExecutor::new).run(first -> {
            HttpRequestClient selected = HttpUtil.getClient();
            runner.withBean("two", HttpExecutor.class, HttpExecutorTest.RecordingExecutor::new)
                    .run(second -> assertThat(second).hasFailed());
            assertSame(selected, HttpUtil.getClient());
        });
    }

    @Test
    void autoConfigurationIsListedInBootImports() throws Exception {
        // classpath 有多个模块的 imports，不能只检查第一个资源。
        var resources = HttpAutoConfiguration.class.getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        boolean found = false;
        while (resources.hasMoreElements()) {
            try (var stream = resources.nextElement().openStream()) {
                String text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (text.lines().anyMatch(HttpAutoConfiguration.class.getName()::equals)) found = true;
            }
        }
        assertTrue(found);
    }
}

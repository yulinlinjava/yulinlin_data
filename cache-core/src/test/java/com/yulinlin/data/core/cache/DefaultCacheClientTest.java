package com.yulinlin.data.core.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCacheClientTest {
    @Test void noProviderRemainsSafeAndReportsUnavailable() {
        CacheClient client = new DefaultCacheClient(NoOpQueryCache.INSTANCE);
        assertThat(client.available()).isFalse();
        assertThat(client.get("user", "1", String.class)).isNull();
        assertThat(client.exists("user", "1")).isFalse();
        client.set("user", "1", "value");
        assertThat(client.remove("user", "1")).isFalse();
        client.invalidate("user");
        client.clear();
    }

    @Test void validatesPublicKeysAndDoesNotCacheNullLoaderResults() {
        CacheClient client = new DefaultCacheClient(NoOpQueryCache.INSTANCE);
        assertThatThrownBy(() -> client.get(" ", "1", String.class))
                .isInstanceOf(IllegalArgumentException.class);
        AtomicInteger loads = new AtomicInteger();
        assertThat(client.getOrLoad("user", "missing", String.class, () -> {
            loads.incrementAndGet(); return null;
        })).isNull();
        assertThat(loads).hasValue(1);
    }
}

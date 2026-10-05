package com.yulinlin.data.core.loadbalan;

import com.yulinlin.data.core.YulinlinCoreAutoConfig;
import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.exception.NoticeException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadBalanceTest {
    private LoadBalanceNode node(String group, JoinCluster tag, int weight) {
        var node = mock(LoadBalanceNode.class);
        when(node.group()).thenReturn(group);
        when(node.cluster()).thenReturn(tag);
        when(node.weight()).thenReturn(weight);
        when(node.ping()).thenReturn(true);
        return node;
    }

    @Test void singleGroupDoesNotNeedConfigurationAndHonorsCluster() {
        var balance = new RandomLoadBalance();
        var master = node("mysql", JoinCluster.master, 1);
        var slave = node("mysql", JoinCluster.slave, 1);
        balance.register(master); balance.register(slave);
        balance.setDefaultGroup("unused-in-single-group");
        assertThat(balance.defaultGroup()).isEqualTo("mysql");
        assertThat(balance.loadBalance(null, JoinCluster.master)).isSameAs(master);
        assertThat(balance.loadBalance("", JoinCluster.slave)).isSameAs(slave);
    }

    @Test void singleNodeDoesNotBypassClusterRequirements() {
        var balance = new RandomLoadBalance();
        balance.register(node("mysql", JoinCluster.master, 1));
        assertThatThrownBy(() -> balance.loadBalance(null, JoinCluster.slave))
                .isInstanceOf(NoticeException.class);
    }

    @Test void multipleGroupsUseConfiguredDefaultAndExplicitGroupWins() {
        var balance = new RandomLoadBalance();
        var mysql = node("mysql", JoinCluster.master, 1);
        var pg = node("postgresql", JoinCluster.master, 1);
        balance.register(mysql); balance.register(pg);
        assertThatThrownBy(balance::defaultGroup).isInstanceOf(NoticeException.class);
        balance.setDefaultGroup("mysql");
        assertThat(balance.loadBalance(null, JoinCluster.master)).isSameAs(mysql);
        assertThat(balance.loadBalance("postgresql", JoinCluster.master)).isSameAs(pg);
        balance.setDefaultGroup("missing");
        assertThatThrownBy(balance::defaultGroup).isInstanceOf(NoticeException.class);
        balance.setDefaultGroup(" ");
        assertThat(balance.getDefaultGroup()).isNull();
    }

    @Test void weightedOffsetsUseExclusiveUpperBoundWithoutFavoringFirstNode() {
        var balance = new FixedDrawBalance();
        var first = node("mysql", JoinCluster.master, 1);
        var second = node("mysql", JoinCluster.master, 2);
        balance.register(first); balance.register(second);
        balance.offset = 0;
        assertThat(balance.loadBalance(null, null)).isSameAs(first);
        balance.offset = 1;
        assertThat(balance.loadBalance(null, null)).isSameAs(second);
        balance.offset = 2;
        assertThat(balance.loadBalance(null, null)).isSameAs(second);
        assertThat(balance.bound).isEqualTo(3);
    }

    @Test void zeroWeightIsExcludedAndNegativeWeightRejected() {
        var balance = new FixedDrawBalance();
        var zero = node("mysql", JoinCluster.master, 0);
        var active = node("mysql", JoinCluster.master, 1);
        balance.register(zero); balance.register(active);
        assertThat(balance.loadBalance(null, null)).isSameAs(active);
        assertThat(balance.draws).isZero();
        assertThatThrownBy(() -> balance.register(node("mysql", JoinCluster.master, -1)))
                .isInstanceOf(IllegalArgumentException.class);
        balance.remove(active);
        assertThatThrownBy(() -> balance.loadBalance(null, null)).isInstanceOf(NoticeException.class);
    }

    @Test void totalWeightDoesNotOverflowIntegerRange() {
        var balance = new FixedDrawBalance();
        var first = node("mysql", JoinCluster.master, Integer.MAX_VALUE);
        var last = node("mysql", JoinCluster.master, Integer.MAX_VALUE);
        balance.register(first); balance.register(last);
        balance.offset = 2L * Integer.MAX_VALUE - 1;
        assertThat(balance.loadBalance(null, null)).isSameAs(last);
        assertThat(balance.bound).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test void registrationSnapshotsAreImmutableAndLastNodeRemovalPrunesGroup() {
        var balance = new RandomLoadBalance();
        var mysql = node("mysql", JoinCluster.master, 1);
        var pg = node("postgresql", JoinCluster.master, 1);
        balance.register(mysql);
        var snapshot = balance.loadBalanceList();
        balance.register(pg);
        assertThat(snapshot).containsExactly("mysql");
        assertThatThrownBy(() -> snapshot.remove("mysql")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(balance.remove(mysql)).isTrue();
        assertThat(balance.loadBalanceList()).containsExactly("postgresql");
        assertThat(balance.remove(mysql)).isFalse();
        verify(mysql, times(1)).shutdown();
    }

    @Test void defaultSelectionUsesHealthSnapshotInsteadOfRawFirstNode() {
        var balance = new RandomLoadBalance();
        var dead = node("mysql", JoinCluster.master, 1);
        var live = node("mysql", JoinCluster.master, 1);
        when(dead.ping()).thenReturn(false);
        balance.register(dead); balance.register(live);
        balance.ping();
        assertThat(balance.loadBalance(null, JoinCluster.master)).isSameAs(live);
    }

    @Test void offlineDefaultDoesNotSilentlySwitchDatabase() {
        var balance = new RandomLoadBalance();
        var mysql = node("mysql", JoinCluster.master, 1);
        var sqlite = node("sqlite", JoinCluster.master, 1);
        when(mysql.ping()).thenThrow(new IllegalStateException("offline"));
        balance.register(mysql); balance.register(sqlite);
        balance.setDefaultGroup("mysql");
        balance.ping();
        assertThatThrownBy(() -> balance.loadBalance(null, null)).isInstanceOf(NoticeException.class);
        assertThat(balance.loadBalance("sqlite", null)).isSameAs(sqlite);
    }

    @Test void registrationChangeDuringPingDoesNotRestoreRemovedNodes() {
        var balance = new RandomLoadBalance();
        var first = node("mysql", JoinCluster.master, 1);
        var removed = node("mysql", JoinCluster.master, 1);
        balance.register(first); balance.register(removed);
        when(first.ping()).thenAnswer(call -> {
            balance.remove(removed);
            return true;
        });
        balance.ping();
        assertThat(balance.loadBalance(null, null)).isSameAs(first);
        verify(removed, times(1)).shutdown();
    }

    @Test void springPropertyConfiguresFrameworkDefaultLoadBalancer() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("yulinlin.datasource.default-group=postgresql",
                        "yulinlin.datasource.jdbc.parallel-connections=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var balance = context.getBean(LoadBalance.class);
                    assertThat(balance.getDefaultGroup()).isEqualTo("postgresql");
                    balance.register(node("mysql", JoinCluster.master, 1));
                    balance.register(node("postgresql", JoinCluster.master, 1));
                    assertThat(balance.defaultGroup()).isEqualTo("postgresql");
                });
    }

    private static class FixedDrawBalance extends RandomLoadBalance {
        long offset; long bound; int draws;
        @Override protected long randomWeight(long bound) {
            this.bound = bound;
            draws++;
            return offset;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoadBalanceProperties.class)
    static class PropertiesConfiguration {
        @Bean LoadBalance loadBalance(LoadBalanceProperties properties) {
            return new YulinlinCoreAutoConfig().loadBalance(properties);
        }
    }
}

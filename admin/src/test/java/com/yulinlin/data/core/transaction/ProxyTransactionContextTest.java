package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyFactory;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.ClassUpdateWrapperFactory;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真代理与修改节点，数据库执行使用 mock；仅记录用例，不自动运行。 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ProxyTransactionContextTest {
    private RouteSession previous;
    private RouteSession route;
    private final TransactionListenerManager manager = new TransactionListenerManager();
    private final EntityProxyFactory factory = new EntityProxyFactory();

    @BeforeEach void prepare() {
        previous = SessionUtil.route();
        route = RouteSession.builder().transactionListenerManager(manager).filterManager(new IFilterManager() { }).build();
        route.setLoadBalance(new RandomLoadBalance());
        route.setWrapperFactory(WrapperFactoryImpl.builder().update(new ClassUpdateWrapperFactory(UpdateWrapper.class)).build());
        new SessionUtil(route);
    }

    @AfterEach void clean() {
        try {
            while (TransactionListenerManager.currentContext() != null) manager.rollbackTransaction();
        } finally { new SessionUtil(previous); }
    }

    private EntitySession database(String group) {
        EntitySession session = mock(EntitySession.class);
        when(session.group()).thenReturn(group);
        when(session.cluster()).thenReturn(JoinCluster.master);
        when(session.weight()).thenReturn(1);
        when(session.update(any(ExecuteRequest.class))).thenAnswer(invocation -> {
            ExecuteRequest<?> request = invocation.getArgument(0);
            return request.getWrappers().size();
        });
        route.registerSession(session);
        return session;
    }

    private User user(String id) { User user = new User(); user.setId(id); user.setName("old"); return user; }

    @Test void settersShareOneChangesObjectAndFlushOnlyAtOutermostCommit() {
        EntitySession database = database("mysql");
        manager.startTransaction();
        TransactionContext context = TransactionListenerManager.currentContext();
        assertThat(context.proxyChanges()).isNull();
        User target = user("1");
        User proxy = factory.getManagedProxy("mysql", target);
        var changes = context.proxyChanges();
        proxy.setName("first");

        route.transaction(() -> {
            assertThat(TransactionListenerManager.currentContext()).isSameAs(context);
            proxy.setName("last");
            assertThat(context.proxyChanges()).isSameAs(changes);
            return null;
        });
        verify(database, never()).update(any(ExecuteRequest.class));
        manager.commitTransaction();

        verify(database).update(any(ExecuteRequest.class));
        verify(database).startTransaction();
        verify(database).commitTransaction();
        assertThat(target.getName()).isEqualTo("last");
        assertThat(target.getVersion()).isEqualTo(1);
        assertThat(changes.hasDirty()).isFalse();
        assertThat(TransactionListenerManager.currentContext()).isNull();
        assertThatThrownBy(() -> proxy.setName("too late")).hasMessageContaining("原始事务");
    }

    @Test void dirtyEntitiesAreGroupedBySourceAndTypeBeforeAnyResourceCommit() {
        EntitySession mysql = database("mysql");
        EntitySession sqlite = database("sqlite");
        manager.startTransaction();
        User first = factory.getManagedProxy("mysql", user("1"));
        User second = factory.getManagedProxy("mysql", user("2"));
        User local = factory.getManagedProxy("sqlite", user("3"));
        first.setName("mysql-one");
        second.setName("mysql-two");
        local.setName("local");
        manager.commitTransaction();

        var order = inOrder(mysql, sqlite);
        order.verify(mysql).update(any(ExecuteRequest.class));
        order.verify(sqlite).update(any(ExecuteRequest.class));
        order.verify(mysql).commitTransaction();
        order.verify(sqlite).commitTransaction();
        var request = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(mysql).update(request.capture());
        assertThat(request.getValue().getWrappers()).hasSize(2);
    }

    @Test void rollbackDiscardsDirtyStateAndNeverWritesOrExecutesSuccessCallbacks() {
        EntitySession database = database("mysql");
        manager.startTransaction();
        User proxy = factory.getManagedProxy("mysql", user("1"));
        proxy.setName("discarded");
        var changes = TransactionListenerManager.currentContext().proxyChanges();
        java.util.ArrayList<String> events = new java.util.ArrayList<>();
        TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
        manager.rollbackTransaction();

        verify(database, never()).update(any(ExecuteRequest.class));
        verify(database, never()).commitTransaction();
        assertThat(changes.hasDirty()).isFalse();
        assertThat(events).isEmpty();
        manager.startTransaction();
        assertThat(TransactionListenerManager.currentContext().proxyChanges()).isNull();
        assertThatThrownBy(() -> proxy.setName("another transaction")).hasMessageContaining("原始事务");
        manager.commitTransaction();
    }

    @Test void managedProxyRejectsMissingRouteSessionInsteadOfUsingAStandaloneFallback() {
        EntitySession database = database("local");
        new SessionUtil(null);
        manager.startTransaction();
        manager.enlist(database);
        assertThatThrownBy(() -> factory.getManagedProxy("local", user("1")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RouteSession");
        manager.rollbackTransaction();
        verify(database, never()).update(any(ExecuteRequest.class));
    }

    @JoinTable("proxy_user")
    public static class User {
        @JoinMeta(primaryKey = true) private String id;
        private String name;
        @JoinField(version = true) private int version;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }
}

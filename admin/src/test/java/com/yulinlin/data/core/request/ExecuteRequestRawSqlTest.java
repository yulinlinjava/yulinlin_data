package com.yulinlin.data.core.request;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecuteRequestRawSqlTest {
    @Test void rawSqlFactoryInitializesWriteRequestTypeAndCommand() {
        Map<String, Object> params = Map.of("name", "alice", "id", 7);
        var request = ExecuteRequest.newInstance("update users set name=#{name} where id=#{id}", params);
        assertThat(request.getRequestType()).isEqualTo(RequestType.update);
        assertThat(request.getWrappers()).hasSize(1);
        var command = (CommandNode<?>) request.getWrappers().getFirst();
        assertThat(command.getType()).isEqualTo(ParseType.update);
        assertThat(command.getParams()).isSameAs(params);
        assertThat(command.getExpression()).isEqualTo("update users set name=#{name} where id=#{id}");
    }

    @Test void executeDelegatesToRouteAndReturnsAffectedRowsInsteadOfNull() {
        RouteSession previous = SessionUtil.route();
        var route = mock(RouteSession.class);
        var request = ExecuteRequest.newInstance("delete from users where id=#{id}", Map.of("id", 7));
        when(route.update(request)).thenReturn(3);
        new SessionUtil(route);
        try {
            assertThat(request.execute()).isEqualTo(3);
            verify(route).update(request);
        } finally {
            new SessionUtil(previous);
        }
    }
}

package com.yulinlin.jdbc.sql;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Parse-only regression cases: no database or connection is used. */
class RawSqlParametersTest {
    private SimpParamsContext context(RequestType type) {
        return new SimpParamsContext(type, Map.of(), new JdbcCoderManager().createEncoderBuffer(),
                Map.class, false);
    }

    @Test void queryBindsBareKeysInSqlOrderIncludingRepeatedParameters() {
        var request = QueryRequest.newInstance(
                "select * from users where id=#{id} and (name=#{name} or nickname=#{name})",
                Map.of("name", "alice", "id", 7), Map.class);
        var context = context(RequestType.select);
        var result = (ParseResult) new SqlParseManager().parse(request.getWrapper(), context);
        var sql = (SqlNode) result.getRequest();
        assertThat(result.getType()).isEqualTo(ParseType.select);
        assertThat(sql.getSql()).doesNotContain("#{");
        assertThat(sql.getList()).containsExactly(7, "alice", "alice");
        assertThat(context.getDataBuffer().toMap()).containsEntry("id", 7).containsEntry("name", "alice")
                .doesNotContainKeys("#{id}", "#{name}");
    }

    @Test void writeRequestProducesBoundParametersAndKeepsValuesOutOfSqlText() {
        String value = "alice' OR 1=1 --";
        var request = ExecuteRequest.newInstance(
                "update users set name=#{name} where id=#{id}", Map.of("id", 7, "name", value));
        var result = (ParseResult) new SqlParseManager().parse(
                request.getWrappers().getFirst(), context(request.getRequestType()));
        var sql = (SqlNode) result.getRequest();
        assertThat(result.getType()).isEqualTo(ParseType.update);
        assertThat(sql.getSql()).doesNotContain(value).doesNotContain("#{");
        assertThat(sql.getList()).containsExactly(value, 7);
    }

    @Test void noParameterCommandStillWorksWithEmptyOrAbsentParameterMap() {
        for (var command : new CommandNode[]{
                new CommandNode<>("show tables", Map.of(), ParseType.select),
                new CommandNode<>("show tables", ParseType.select)}) {
            var result = (ParseResult) new SqlParseManager().parse(command, context(RequestType.select));
            var sql = (SqlNode) result.getRequest();
            assertThat(sql.getSql()).isEqualTo("show tables");
            assertThat(sql.getList()).isEmpty();
        }
    }

    @Test void rawQueryAndWriteBindParametersWithoutAnEntitySource() {
        for (Class<?> source : new Class<?>[]{null, Object.class}) {
            for (var command : new CommandNode[]{
                    new CommandNode<>("select display_name from users where id=#{id}",
                            Map.of("id", 7), ParseType.select),
                    new CommandNode<>("update users set display_name=#{name} where id=#{id}",
                            Map.of("name", "alice", "id", 7), ParseType.update)}) {
                RequestType requestType = command.getType() == ParseType.select ? RequestType.select : RequestType.update;
                var context = new SimpParamsContext(requestType, Map.of(),
                        new JdbcCoderManager().createEncoderBuffer(), source, true);
                var result = (ParseResult) new SqlParseManager().parse(command, context);
                var sql = (SqlNode) result.getRequest();
                assertThat(sql.getSql()).doesNotContain("#{").contains("display_name");
                assertThat(context.toColumn("displayName")).isEqualTo("displayName");
                if (command.getType() == ParseType.select) assertThat(sql.getList()).containsExactly(7);
                else assertThat(sql.getList()).containsExactly("alice", 7);
            }
        }
    }
}

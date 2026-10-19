package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.wrapper.impl.DeleteWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachePartitionResolverTest {

    private static final CacheNamespace USERS = CacheNamespace.of(AbstractSession.class, "mysql", "users");

    @Test
    void queryRecognizesOneEqBehindEmptyPredicateContainers() {
        SelectWrapper<Object> wrapper = new SelectWrapper<>();
        wrapper.table("users", "u");
        wrapper.where()
                .and(empty -> { })
                .or(empty -> { })
                .and(nested -> nested.or(one -> one.eq("u.username", "Alice ")));

        assertThat(CachePartitionResolver.query(USERS, wrapper))
                .contains(CachePartition.of(USERS, "username", "alice").orElseThrow());
    }

    @Test
    void queryRejectsLockJoinAndMoreThanOneEffectiveCondition() {
        SelectWrapper<Object> locked = select("users", "username", "alice").lock();
        SelectWrapper<Object> joined = select("users", "username", "alice");
        joined.left("roles", "r").eq("r.user_id", 1);
        SelectWrapper<Object> multiple = select("users", "username", "alice");
        multiple.where().eq("status", 1);

        assertThat(CachePartitionResolver.query(USERS, locked)).isEmpty();
        assertThat(CachePartitionResolver.query(USERS, joined)).isEmpty();
        assertThat(CachePartitionResolver.query(USERS, multiple)).isEmpty();
    }

    @Test
    void queryRejectsAnotherTableAndUnsupportedScalar() {
        SelectWrapper<Object> anotherTable = select("roles", "username", "alice");
        SelectWrapper<Object> complex = select("users", "metadata", Map.of("a", 1));
        SelectWrapper<Object> unknownPrefix = select("users", "other.username", "alice");
        SelectWrapper<Object> jsonPath = select("users", "payload.name", "alice");

        assertThat(CachePartitionResolver.query(USERS, anotherTable)).isEmpty();
        assertThat(CachePartitionResolver.query(USERS, complex)).isEmpty();
        assertThat(CachePartitionResolver.query(USERS, unknownPrefix)).isEmpty();
        assertThat(CachePartitionResolver.query(USERS, jsonPath)).isEmpty();
    }

    @Test
    void updateAndDeleteRecognizeExactUnchangedPartition() {
        UpdateWrapper<Object> update = new UpdateWrapper<>();
        update.table("users").fields().field("display_name", "Alice");
        update.where().eq("username", "alice");
        DeleteWrapper<Object> delete = new DeleteWrapper<>();
        delete.table("users").where().eq("username", "alice");

        CachePartition expected = CachePartition.of(USERS, "username", "alice").orElseThrow();
        assertThat(CachePartitionResolver.write(USERS, update)).contains(expected);
        assertThat(CachePartitionResolver.write(USERS, delete)).contains(expected);
    }

    @Test
    void updateChangingPartitionFieldAndCustomSqlAreImprecise() {
        UpdateWrapper<Object> update = new UpdateWrapper<>();
        update.table("users").fields().field("users.username", "bob");
        update.where().eq("users.username", "alice");
        CommandNode<Object> custom = new CommandNode<>("delete from users where username=#{name}",
                Map.of("name", "alice"), ParseType.delete);

        assertThat(CachePartitionResolver.write(USERS, update)).isEmpty();
        assertThat(CachePartitionResolver.write(USERS, custom)).isEmpty();
    }

    @Test
    void mappedColumnsAndEncodedValuesUseDatabaseRepresentations() {
        SelectWrapper<Object> query = select("users", "statusCode", Status.ACTIVE);
        UpdateWrapper<Object> update = new UpdateWrapper<>();
        update.table("users").fields().field("status_code", 2);
        update.where().eq("statusCode", Status.ACTIVE);

        java.util.function.Function<String, String> columns = name ->
                name.equals("statusCode") ? "status_code" : name;
        java.util.function.Function<Object, Object> values = value ->
                value instanceof Status status ? status.code : value;

        assertThat(CachePartitionResolver.query(USERS, query, columns, values))
                .contains(CachePartition.of(USERS, "status_code", 1).orElseThrow());
        assertThat(CachePartitionResolver.write(USERS, update, columns, values)).isEmpty();
    }

    private enum Status {
        ACTIVE(1);
        private final int code;
        Status(int code) { this.code = code; }
    }

    private static SelectWrapper<Object> select(String table, String field, Object value) {
        SelectWrapper<Object> wrapper = new SelectWrapper<>();
        wrapper.table(table).where().eq(field, value);
        return wrapper;
    }
}

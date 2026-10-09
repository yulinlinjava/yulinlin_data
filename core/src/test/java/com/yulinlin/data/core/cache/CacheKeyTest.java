package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyTest {

    @Test
    void sameMetadataProducesSameKeyRegardlessOfMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("status", 1);
        first.put("name", "admin");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "admin");
        second.put("status", 1);

        CacheKey left = key("mysql", new CommandNode<>("select * from user where status=#{status} and name=#{name}", first, ParseType.select));
        CacheKey right = key("mysql", new CommandNode<>("select * from user where status=#{status} and name=#{name}", second, ParseType.select));

        assertThat(left.value()).isEqualTo(right.value()).startsWith("v2:").hasSize(35);
    }

    @Test
    void groupSqlAndTypedParametersAreIsolated() {
        CacheKey one = key("mysql", command(1));
        CacheKey two = key("mysql", command(2));
        CacheKey textOne = key("mysql", command("1"));
        CacheKey anotherGroup = key("postgresql", command(1));

        assertThat(one).isNotEqualTo(two).isNotEqualTo(textOne).isNotEqualTo(anotherGroup);
    }

    private static CommandNode<String> command(Object id) {
        return new CommandNode<>("select * from user where id=#{id}", Map.of("id", id), ParseType.select);
    }

    private static CacheKey key(String group, CommandNode<?> node) {
        return CacheKey.query(group, "master", AbstractSession.class,
                UserRow.class, UserRow.class, ParseType.select, node);
    }

    private record UserRow(long id, String name) {
    }
}

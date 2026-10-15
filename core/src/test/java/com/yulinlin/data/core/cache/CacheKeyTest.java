package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
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

    @Test
    void builtInCountWrapperAndItsSelectUseTheSameLogicalKey() {
        SelectWrapper<UserRow> select = new SelectWrapper<>();
        select.table("users").where().eq("status", 1);

        assertThat(key("mysql", ParseType.count, select))
                .isEqualTo(key("mysql", ParseType.count, new CountWrapper(select)));
    }

    @Test
    void unknownWrapperSubtypeKeepsItsAdditionalCountState() {
        TenantSelect first = new TenantSelect("tenant-a");
        first.table("users").where().eq("status", 1);
        TenantSelect second = new TenantSelect("tenant-b");
        second.table("users").where().eq("status", 1);

        assertThat(key("mysql", ParseType.count, first))
                .isNotEqualTo(key("mysql", ParseType.count, second));
    }

    private static CommandNode<String> command(Object id) {
        return new CommandNode<>("select * from user where id=#{id}", Map.of("id", id), ParseType.select);
    }

    private static CacheKey key(String group, CommandNode<?> node) {
        return key(group, ParseType.select, node);
    }

    private static CacheKey key(String group, ParseType type, INode node) {
        return OrmCacheKeyFactory.query(group, "master", AbstractSession.class,
                UserRow.class, UserRow.class, type, node);
    }

    private static final class TenantSelect extends SelectWrapper<UserRow> {
        private final String tenant;

        private TenantSelect(String tenant) {
            this.tenant = tenant;
        }
    }

    private record UserRow(long id, String name) {
    }
}

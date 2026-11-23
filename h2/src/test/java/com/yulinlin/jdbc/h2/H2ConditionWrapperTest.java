package com.yulinlin.jdbc.h2;

import com.yulinlin.common.model.*;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.model.*;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fact;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2ConditionWrapperTest {
    @Test
    void modelApisHaveOneTypeParameterAndPreserveConcreteFluentReturns() throws Exception {
        List<Class<?>> types = List.of(ModelConditionWrapper.class,
                BaseModelSelectWrapper.class, BaseModelGroupWrapper.class, BaseModelUpdateWrapper.class,
                BaseModelDeleteWrapper.class, BaseModelInsertWrapper.class,
                ModelSelectWrapper.class, ModelGroupWrapper.class, ModelUpdateWrapper.class,
                ModelDeleteWrapper.class, ModelInsertWrapper.class);
        for (Class<?> type : types) {
            assertEquals(1, type.getTypeParameters().length, type.getName());
            for (var method : type.getMethods()) {
                if (method.isBridge() || method.isSynthetic()
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                if (types.contains(method.getReturnType())) {
                    assertEquals(type, method.getReturnType(), method.toString());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void singleEntityGenericSupportsInsertCallbacksAndConcreteQueryMethods(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            ModelInsertWrapper<Fact> insert = ModelInsertWrapper.newInstance(Fact.class)
                    .field(Fact::getId, 11L).field(Fact::getProjectCode, "new-project")
                    .field(Fact::getAmount, 7d).batch(false).clearCache(true);
            assertEquals(1, insert.execute());
            ModelSelectWrapper<Fact> query = ModelSelectWrapper.newInstance(Fact.class)
                    .eq(Fact::getProjectCode, "new-project")
                    .apply(w -> w.where(c -> c.gt(Fact::getAmount, 6d)))
                    .fieldMeta(fields -> fields.field(Fact::getAmount, "amount"))
                    .orderBy(order -> order.orderByDesc(Fact::getId))
                    .cache(false).autoUpdate(false).page(1, 10);
            assertEquals(List.of(11L), query.selectList().stream().map(Fact::getId).toList());
            assertTrue(query.exist());
            BaseModelSelectWrapper<Fact> base = new BaseModelSelectWrapper<Fact>(null, Fact.class)
                    .eq(Fact::getId, 11L).page(1, 1);
            assertEquals(11L, base.selectOne().getId());
            ModelDeleteWrapper<Fact> delete = ModelDeleteWrapper.newInstance(Fact.class)
                    .eq(Fact::getId, 11L).batch(false);
            assertEquals(1, delete.execute());
            assertFalse(query.exist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelQueryPreservesFluentTypeBooleanGroupsBindingsAndPagination(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            IConditionWrapper<Fact> shared = new ConditionManager<Fact>().eq(Fact::getProjectCode, "123");
            Consumer<IConditionWrapper<Fact>> choices = c -> c.or(or -> or.eq(Fact::getId, 1L).eq(Fact::getId, 2L));
            ModelSelectWrapper<Fact> query = ModelSelectWrapper
                    .<Fact>newInstance(Fact.class)
                    .and(shared).where(choices).not(c -> c.eq(Fact::getIncomplete, 1))
                    .orderByAsc("id").page(1, 5);
            assertEquals(List.of(1L), query.selectList().stream().map(Fact::getId).toList());
            assertEquals(List.of("123", 1L, 2L, 1), f.lastSql().getList());
            assertEquals(1, query.count());
            assertEquals(1, query.selectPage().getTotal());
            assertEquals(1, query.selectEach(row -> assertEquals(1L, row.getId())));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelUpdateAndDeleteKeepOwnReturnTypesAndOnlyAffectMatchingRows(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            IConditionWrapper<?> project = new ConditionManager<Fact>().eq(Fact::getProjectCode, "123");
            Consumer<IConditionWrapper<Fact>> firstTwo = c -> c.lte(Fact::getId, 2L);
            ModelUpdateWrapper<Fact> update = ModelUpdateWrapper
                    .<Fact>newInstance(Fact.class)
                    .and(project).where(firstTwo).field(Fact::getAmount, 42d);
            assertEquals(2, update.execute());
            assertEquals(List.of(1L, 2L), ModelSelectWrapper.newInstance(Fact.class)
                    .eq(Fact::getAmount, 42d).orderByAsc("id").selectList().stream().map(Fact::getId).toList());
            ModelDeleteWrapper<Fact> delete = ModelDeleteWrapper
                    .<Fact>newInstance(Fact.class)
                    .and(project).or(c -> c.eq(Fact::getId, 2L).eq(Fact::getId, 4L));
            assertEquals(2, delete.execute());
            assertEquals(List.of(1L, 3L, 5L, 6L, 7L, 8L, 9L, 10L), ModelSelectWrapper.newInstance(Fact.class)
                    .orderByAsc("id").selectList().stream().map(Fact::getId).toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void modelGroupHavingAndCountKeepAggregateBindings(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            Consumer<IConditionWrapper<Total>> having = c -> c.gt(Total::getAmount, 5d);
            ModelGroupWrapper<Total> query = ModelGroupWrapper
                    .<Total>newInstance(Total.class)
                    .where(c -> c.lte("id", 4L)).having(having).orderByAsc("metricCode");
            var rows = query.selectList();
            assertEquals(1, rows.size());
            assertEquals("m1", rows.getFirst().getMetricCode());
            assertEquals(9d, rows.getFirst().getAmount());
            assertEquals(List.of(4L, 5d), f.lastSql().getList());
            assertEquals(1, query.count());
            IConditionWrapper<Total> condition = query.having();
            condition.lt(Total::getAmount, 9d);
            assertTrue(query.selectList().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void factoryWrappersReuseInterfaceConsumersForReadsWritesAndHaving(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var factory = SessionUtil.route().getWrapperFactory();
            var insert = factory.<Fact>createInsertWrapper().table("repro_minute")
                    .fields(fields -> fields.field(Fact::getId, 11L).field(Fact::getAmount, 1d));
            var insertion = ExecuteRequest.ofInsert(Fact.class); insertion.addRequest(insert);
            assertEquals(1, f.session.insert(insertion));
            Consumer<IConditionWrapper<Fact>> firstTwo = c -> c.lte(Fact::getId, 2L);
            var select = factory.<Fact>createSelectWrapper();
            select.table("repro_minute").where(firstTwo).orderByAsc("id");
            select.fields().field("id");
            assertEquals(List.of(1L, 2L), f.session.select(QueryRequest.newInstance(Fact.class, select))
                    .stream().map(Fact::getId).toList());
            var update = factory.<Fact>createUpdateWrapper();
            update.table("repro_minute").where(firstTwo);
            update.fields().field("amount", 8d);
            var write = ExecuteRequest.ofUpdate(Fact.class); write.addRequest(update);
            assertEquals(2, f.session.update(write));
            var group = factory.<Fact>createGroupWrapper();
            group.table("repro_minute").where(firstTwo);
            group.aggregations().field("metricCode", "metricCode");
            group.metrics().sum("amount", "amount");
            Consumer<IConditionWrapper<Fact>> having = c -> c.gt("amount", 15d);
            group.having(having);
            assertEquals(16d, f.session.group(QueryRequest.newInstance(Fact.class, group)).getFirst().getAmount());
            var delete = factory.<Fact>createDeleteWrapper();
            delete.table("repro_minute").where(firstTwo);
            var removal = ExecuteRequest.ofDelete(Fact.class); removal.addRequest(delete);
            assertEquals(2, f.session.delete(removal));
            assertTrue(f.session.select(QueryRequest.newInstance(Fact.class, select)).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void factoryJoinAndExistingConditionKeepSqlBindingsAndPagination(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            IConditionWrapper<Fact> condition = new ConditionManager<Fact>().eq("a.id", 2L);
            var query = SessionUtil.route().getWrapperFactory().<Fact>createSelectWrapper()
                    .table("repro_minute", "a")
                    .fields(fields -> fields.field("a.id", "id"))
                    .join(join -> join.left("repro_minute", "b").eq("b.id", 1L))
                    .where(condition).orderByAsc("a.id").page(1, 1);
            assertEquals(List.of(2L), f.session.select(QueryRequest.newInstance(Fact.class, query))
                    .stream().map(Fact::getId).toList());
            assertEquals(List.of(1L, 2L), f.lastSql().getList());
            assertEquals(1, condition.getSize());
        }
    }

    @Data @JoinTable("repro_minute")
    public static class Total {
        @JoinAggregations private String metricCode;
        @JoinMetrics(MetricsEnum.sum) private Double amount;
    }
}

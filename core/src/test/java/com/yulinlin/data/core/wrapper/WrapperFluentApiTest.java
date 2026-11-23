package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.anno.AggregationsEnum;
import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WrapperFluentApiTest {
    @Test
    void publicApisAndFactoriesOnlyAskForEntityType() {
        for (Class<?> type : List.of(ISelectWrapper.class, IGroupWrapper.class, IUpdateWrapper.class,
                IDeleteWrapper.class, IInsertWrapper.class, IConditionWrapper.class, IFieldsWrapper.class,
                IInsertFieldsWrapper.class, IUpdateFieldsWrapper.class, IAsFieldListWrapper.class,
                IMetricsWrapper.class, IAggregationsWrapper.class)) {
            assertEquals(1, type.getTypeParameters().length, type.getName());
        }
        for (var method : IWrapperFactory.class.getDeclaredMethods()) {
            if (!method.getName().equals("createCountWrapper")) {
                assertEquals(1, method.getTypeParameters().length, method.toString());
            }
        }
    }

    @Test
    void concreteFluentMethodsKeepConcreteTypesIncludingDefaultsAndMetadata() {
        for (Class<?> type : List.of(SelectWrapper.class, GroupWrapper.class, InsertWrapper.class,
                UpdateWrapper.class, DeleteWrapper.class, AsFieldListWrapper.class, InsertFieldsWrapper.class,
                UpdateFieldsWrapper.class, MetricsWrapper.class, AggregationsWrapper.class)) {
            assertEquals(1, type.getTypeParameters().length);
            for (var method : type.getMethods()) {
                if (method.isBridge() || method.isSynthetic() || Modifier.isStatic(method.getModifiers())) continue;
                Class<?> returns = method.getReturnType();
                if (returns != Object.class && returns.isAssignableFrom(type)) {
                    assertEquals(type, returns, method.toString());
                }
            }
        }
    }

    @Test
    void queryAndJoinCallbacksUseInterfacesAndReturnTheOwningQuery() {
        var concrete = new CopyableSelect<Person>();
        Consumer<IConditionWrapper<Person>> condition = c -> c.eq(Person::getAge, 18);
        Consumer<IAsFieldListWrapper<Person>> fields = c -> c.field(Person::getAge, "age");
        Consumer<ISelectWrapper<Person>> join = q -> q.left("profile", "p").expression("p.id = u.id");
        ISelectWrapper<Person> query = concrete.table("person", "u").fields(fields)
                .where(condition).join(join).orderByAsc("age").page(1, 10).lock();
        assertSame(concrete, query);
        assertEquals(1, query.where().getSize());
        assertEquals(10, concrete.getPageSize());
        assertTrue(concrete.isLock());
        SelectWrapper<Person> copy = concrete.clone().page(2, 3).orderByDesc(Person::getAge);
        assertNotSame(concrete, copy);
        assertEquals(10, concrete.getPageSize());
        assertEquals(3, copy.getPageSize());
    }

    @Test
    void fieldAndMetadataChainsKeepUpdateOperationsAndOneShotMetadata() {
        UpdateFieldsWrapper<Person> concrete = new UpdateFieldsWrapper<>();
        IUpdateFieldsWrapper<Person> fields = concrete;
        assertSame(fields, fields.meta("source", "input").field("name", "alice")
                .meta(Map.of("source", "counter")).inc(Person::getAge, 2).dec("remaining", 1));
        var nodes = concrete.getList().stream().toList();
        assertEquals(List.of(AtomicValue.OperateEnum.store, AtomicValue.OperateEnum.inc, AtomicValue.OperateEnum.dec),
                nodes.stream().map(AtomicValue::getOperate).toList());
        assertEquals(Map.of("source", "input"), nodes.get(0).getMeta());
        assertEquals(Map.of("source", "counter"), nodes.get(1).getMeta());
        assertTrue(nodes.get(2).getMeta().isEmpty());
        UpdateFieldsWrapper<Person> chained = concrete.field("other", 1).inc("total", 2).meta("next", true);
        assertSame(concrete, chained);
        IInsertFieldsWrapper<Person> insert = new InsertFieldsWrapper<>();
        assertSame(insert, insert.meta("tag", true).field(Person::getAge, 20).field("name", "bob"));
    }

    @Test
    void groupComponentsAndCallbacksUseTheirOwnInterfaces() {
        GroupWrapper<Person> concrete = new GroupWrapper<>();
        Consumer<IGroupWrapper<Person>> join = q -> q.inner("profile", "p").expression("p.id = u.id");
        Consumer<IConditionWrapper<Person>> filter = c -> c.gt("total", 10);
        IGroupWrapper<Person> group = concrete.table("person", "u").join(join).having(filter)
                .where(c -> c.gt(Person::getAge, 18)).orderByDesc("total").page(1, 5);
        assertSame(concrete, group);
        assertSame(group.metrics(), group.metrics().meta("tag", true)
                .field(MetricsEnum.sum, "age", "total").count("id", "count"));
        assertSame(group.aggregations(), group.aggregations().field(AggregationsEnum.day, "created", "day")
                .interval("age", "bucket", 10));
        assertEquals(1, group.having().getSize());
    }

    @Test
    void factoryKeepsConfiguredImplementationWithoutCallerImplementationTypeArguments() {
        IWrapperFactory factory = WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, CustomSelect.class, GroupWrapper.class);
        ISelectWrapper<Person> select = factory.<Person>createSelectWrapper()
                .table("person").fields(f -> f.field(Person::getAge, "age")).page(1, 3);
        assertInstanceOf(CustomSelect.class, select);
        IUpdateWrapper<Person> update = factory.<Person>createUpdateWrapper().table("person")
                .fields(f -> f.field("name", "alice").inc(Person::getAge, 1)).where(c -> c.eq("id", 1));
        assertEquals(1, update.where().getSize());
        IInsertWrapper<Person> insert = factory.<Person>createInsertWrapper().table("person")
                .fields(f -> f.meta("tag", true).field(Person::getAge, 18));
        assertNotNull(insert.fields());
        IDeleteWrapper<Person> delete = factory.<Person>createDeleteWrapper().table("person")
                .where(c -> c.eq("id", 1)).meta("tag", true);
        assertEquals(1, delete.where().getSize());
    }

    @Test
    void existingCustomConditionsAreAttachedWithoutModifyingTheirOwnTree() {
        IConditionWrapper<Person> condition = new ConditionWrapperTest.CustomConditions<Person>().eq(Person::getAge, 18);
        var select = new SelectWrapper<Person>().where(condition);
        var root = assertInstanceOf(And.class, select.where().getCondition());
        assertEquals(1, root.getList().size());
        assertSame(condition.getCondition(), root.getList().getFirst());
        assertEquals(1, condition.getSize());
    }

    public static class CustomSelect<E> extends SelectWrapper<E> { }
    public static class CopyableSelect<E> extends SelectWrapper<E> implements Cloneable { }
    public static class Person {
        private Integer age;
        public Integer getAge() { return age; }
    }
}

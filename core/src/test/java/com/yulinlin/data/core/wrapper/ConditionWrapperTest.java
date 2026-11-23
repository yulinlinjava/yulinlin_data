package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.node.AbstractCondition;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.wrapper.impl.AbstractConditionWrapper;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConditionWrapperTest {
    @Test void interfaceKeepsLambdaTypingAndOneShotMetadataAcrossFluentCalls() {
        IConditionWrapper<Person> conditions = new ConditionManager<>();
        assertSame(conditions, conditions.meta("source", "filter")
                .meta(Map.of("tag", "first"))
                .eq(Person::getAge, 18).gt("age", 10));
        var nodes = ((And) conditions.getCondition()).getList();
        var first = assertInstanceOf(Eq.class, nodes.getFirst());
        assertEquals("age", first.getKey());
        assertEquals(18, first.getValue());
        assertEquals(Map.of("source", "filter", "tag", "first"), first.getMeta());
        assertTrue(((AbstractCondition<?>) nodes.get(1)).getMeta().isEmpty());
        assertEquals(2, conditions.getSize());
    }

    @Test void groupedCallbacksReturnParentWhileNoArgMethodsReturnAttachedChildren() {
        IConditionWrapper<Person> root = new ConditionManager<>();
        Consumer<IConditionWrapper<Person>> choices = c -> c.eq(Person::getAge, 18).eq(Person::getAge, 21);
        assertSame(root, root.or(choices).and(c -> c.gt("age", 10)).not(c -> c.eq("age", 99)));
        var nodes = ((And) root.getCondition()).getList();
        assertEquals(2, assertInstanceOf(Or.class, nodes.get(0)).getList().size());
        assertEquals(1, assertInstanceOf(And.class, nodes.get(1)).getList().size());
        assertEquals(1, assertInstanceOf(Not.class, nodes.get(2)).getList().size());
        var child = root.and();
        assertNotSame(root, child);
        assertSame(child.getCondition(), nodes.get(3));
        child.in(Person::getAge, List.of(18, 21));
        assertEquals(1, ((And) nodes.get(3)).getList().size());
    }

    @Test void compositionAcceptsAnotherImplementationAndUnknownEntityWithoutConcreteCasts() {
        IConditionWrapper<Person> root = new ConditionManager<>();
        IConditionWrapper<?> other = new CustomConditions<Person>().eq(Person::getAge, 18);
        assertSame(root, root.and(other));
        var original = root.getCondition();
        assertSame(other.getCondition(), ((And) original).getList().getFirst());
        assertSame(root, root.struct());
        assertSame(original, ((And) root.getCondition()).getList().getFirst());
        IConditionWrapper<Person> custom = new CustomConditions<>();
        custom.or(c -> c.eq(Person::getAge, 20));
        assertEquals(1, ((Or) ((And) custom.getCondition()).getList().getFirst()).getList().size());
    }

    @Test void optionalValuesKeepExistingSkipSemantics() {
        IConditionWrapper<Person> condition = new ConditionManager<>();
        assertSame(condition, condition.eq("name", null).eq("name", "").in("age", List.of()));
        assertEquals(0, condition.getSize());
        condition.isNull("name").expression("age > #{minimum}", Map.of("minimum", 10));
        assertEquals(2, condition.getSize());
    }

    public static class CustomConditions<E> extends AbstractConditionWrapper<E> {
        public CustomConditions() { }
    }

    public static class Person {
        private Integer age;
        public Integer getAge() { return age; }
    }
}

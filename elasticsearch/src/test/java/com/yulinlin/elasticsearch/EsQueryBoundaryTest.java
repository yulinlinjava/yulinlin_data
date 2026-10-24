package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EsQueryBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "com.yulinlin.elasticsearch.node.NestedNode",
            "com.yulinlin.elasticsearch.parse.base.NestedParse",
            "com.yulinlin.elasticsearch.parse.CommandNodeParse"
    })
    void removedLegacyTypesAreNotPackaged(String type) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
    }

    @Test
    void noNestedWrapperEntryPointRemains() {
        assertFalse(Arrays.stream(IConditionWrapper.class.getMethods()).anyMatch(m -> m.getName().equals("nested")));
        assertFalse(Arrays.stream(ConditionManager.class.getMethods()).anyMatch(m -> m.getName().equals("nested")));
    }

    @Test
    void dottedFieldNamesRemainOrdinaryTermQueriesWithoutImplicitNestedWrapping() {
        Query query = parse(new Eq("profile.name", "Alice"));
        assertTrue(query.isTerm());
        assertEquals("profile.name", query.term().field());
        assertEquals("Alice", query.term().value().stringValue());
    }

    @Test
    void ordinaryAndOrNotGroupsRemainAvailable() {
        var conditions = new ConditionManager<>();
        conditions.eq("status", "active")
                .or(group -> group.eq("region", "east").eq("region", "west"))
                .not(group -> group.eq("role", "blocked"));

        Query query = parse(conditions);
        assertTrue(query.isBool());
        var required = query.bool().must();
        assertEquals(3, required.size());
        assertEquals("status", required.get(0).term().field());
        assertEquals("active", required.get(0).term().value().stringValue());
        var alternatives = required.get(1).bool();
        assertEquals("1", alternatives.minimumShouldMatch());
        assertEquals(2, alternatives.should().size());
        assertEquals("east", alternatives.should().get(0).term().value().stringValue());
        assertEquals("west", alternatives.should().get(1).term().value().stringValue());
        var excluded = required.get(2).bool().mustNot();
        assertEquals(1, excluded.size());
        assertEquals("role", excluded.getFirst().term().field());
        assertEquals("blocked", excluded.getFirst().term().value().stringValue());
    }

    private static Query parse(INode node) {
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new ElasticCoderManager().createEncoderBuffer(), Object.class, true);
        return (Query) new ElasticSearchParseManager().parse(node, context);
    }
}

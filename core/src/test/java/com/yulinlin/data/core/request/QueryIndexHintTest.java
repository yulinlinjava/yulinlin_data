package com.yulinlin.data.core.request;

import com.yulinlin.data.core.cache.OrmCacheKeyFactory;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryIndexHintTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "idx name", "idx; DROP TABLE t", "idx--comment", "idx/*x*/", "\"idx\"", "`idx`", "a.idx", "#{index}", "idx)", "1idx"})
    void rejectsNonIdentifiers(String name) {
        assertThrows(IllegalArgumentException.class, () -> request().useIndex(name));
    }

    @Test
    void rejectsEmptyAndNullArraysWithoutReplacingAnExistingHint() {
        var request = request().useIndex("idx_one");
        assertThrows(IllegalArgumentException.class, request::useIndex);
        assertThrows(IllegalArgumentException.class, () -> request.useIndex((String[]) null));
        assertEquals(List.of("idx_one"), request.getIndexHint().indexes());
    }

    @Test
    void snapshotsDeduplicatesAndReplacesNamesWithoutSharingMutableLists() {
        String[] names = {"idx_one", "idx_two", "idx_one"};
        var original = request().useIndex(names);
        names[0] = "changed";
        var copy = original.copy(new CountWrapper(original.getWrapper()));
        assertEquals(List.of("idx_one", "idx_two"), original.getIndexHint().indexes());
        assertThrows(UnsupportedOperationException.class, () -> original.getIndexHint().indexes().add("x"));
        original.useIndex("idx_three");
        assertEquals(List.of("idx_one", "idx_two"), copy.getIndexHint().indexes());
        assertEquals(List.of("idx_three"), original.getIndexHint().indexes());
    }

    @Test
    void copyAndFilterReplacementInheritTheCallersHint() {
        var source = request().useIndex("idx_one").cache().autoUpdate(false);
        source.setRoot(Map.of("tenant", "p"));
        source.setSession("h2");
        var replacement = request().useIndex("idx_filter");
        replacement.inheritExecutionOptions(source);
        assertEquals(source.getIndexHint(), replacement.getIndexHint());
        assertSame(source.getRoot(), replacement.getRoot());
        assertEquals("h2", replacement.getSession());
        assertFalse(replacement.getAutoUpdate());
        assertEquals(source.getCacheMode(), replacement.getCacheMode());
        assertEquals(source.getIndexHint(), source.copy(new GroupWrapper<>().table("samples")).getIndexHint());
        source.useIndex("idx_other");
        assertEquals(List.of("idx_one"), replacement.getIndexHint().indexes());
    }

    @Test
    void changingTablesAliasesOrJoiningCannotSilentlyRetargetTheHint() {
        var source = request().useIndex("idx_one");
        for (var other : List.of(new SelectWrapper<>().table("other"), new SelectWrapper<>().table("samples", "s"))) {
            assertThrows(IllegalArgumentException.class, () -> source.copy(other));
            assertThrows(IllegalArgumentException.class, () -> QueryRequest.newInstance(Map.class, other).inheritExecutionOptions(source));
        }
        var joined = new SelectWrapper<>().table("samples");
        joined.left("other", "o");
        assertThrows(UnsupportedOperationException.class, () -> source.copy(joined));
        assertThrows(UnsupportedOperationException.class, () -> QueryRequest.newInstance(Map.class, joined).useIndex("idx"));
    }

    @Test
    void rawSqlMissingTableAndDynamicTableAreNotRewritten() {
        var raw = CommandNode.of("select * from samples", Map.of(), ParseType.select);
        assertThrows(UnsupportedOperationException.class, () -> QueryRequest.newInstance(Map.class, raw).useIndex("idx"));
        assertThrows(UnsupportedOperationException.class, () -> QueryRequest.newInstance(Map.class, new SelectWrapper<>()).useIndex("idx"));
        assertThrows(IllegalArgumentException.class, () -> QueryRequest.newInstance(Map.class,
                new SelectWrapper<>().table("samples_#{tenant}")).useIndex("idx"));
    }

    @Test
    void unadaptedParsersIgnoreHintsAndUseTheOriginalMethod() {
        IParseManager parser = (node, context) -> "unchanged";
        var request = request();
        assertEquals("unchanged", parser.parse(request.getWrapper(), null, null));
        request.useIndex("idx");
        assertEquals("unchanged", parser.parse(request.getWrapper(), null, request.getIndexHint()));
    }

    @Test
    void countKeysRetainPageIndependenceButSeparateIndexHints() {
        var wrapper = new SelectWrapper<>().table("samples").page(1, 10);
        wrapper.where().eq("id", 1);
        var request = QueryRequest.newInstance(Map.class, wrapper);
        var plain = key(request, ParseType.count);
        assertEquals(plain, OrmCacheKeyFactory.query("h2", null, getClass(), Map.class,
                Map.class, ParseType.count, wrapper));
        request.useIndex("idx_one");
        var hinted = key(request, ParseType.count);
        var hintedSelect = key(request, ParseType.select);
        assertNotEquals(plain, hinted);
        wrapper.page(2, 30).orderByDesc("id");
        wrapper.fields().field("id");
        assertEquals(hinted, key(request, ParseType.count));
        assertNotEquals(hintedSelect, key(request, ParseType.select));
        request.useIndex("idx_two");
        assertNotEquals(hinted, key(request, ParseType.count));
    }

    private com.yulinlin.data.core.cache.CacheKey key(QueryRequest<?> request, ParseType type) {
        return OrmCacheKeyFactory.query("h2", null, getClass(), Map.class, Map.class,
                type, request.getWrapper(), request.getIndexHint());
    }

    private static QueryRequest<Map> request() {
        return QueryRequest.newInstance(Map.class, new SelectWrapper<>().table("samples"));
    }
}

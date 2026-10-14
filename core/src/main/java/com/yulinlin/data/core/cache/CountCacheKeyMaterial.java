package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Logical cache-key material for a count query.
 *
 * <p>A count does not depend on pagination, ordering, selected result fields,
 * highlights or row locks. Keeping those values in the key creates a separate
 * cached total for every page even though the backend executes the same count.
 */
final class CountCacheKeyMaterial implements INode {

    private static final String VERSION = "count-logical-v1";

    private final String version = VERSION;
    private final Kind kind;
    private final Class<?> wrapperType;
    private final Map<String, Object> meta;
    private final Object from;
    private final Object where;
    private final Object aggregations;
    private final Object metrics;
    private final Object having;

    private CountCacheKeyMaterial(Kind kind,
                                  Class<?> wrapperType,
                                  Map<String, Object> meta,
                                  Object from,
                                  Object where,
                                  Object aggregations,
                                  Object metrics,
                                  Object having) {
        this.kind = kind;
        this.wrapperType = wrapperType;
        this.meta = immutableMeta(meta);
        this.from = from;
        this.where = where;
        this.aggregations = aggregations;
        this.metrics = metrics;
        this.having = having;
    }

    /**
     * Builds a count-only projection for built-in wrappers. Unknown wrappers
     * are returned unchanged so third-party semantics cannot accidentally
     * collide.
     */
    static INode from(INode node) {
        INode unwrapped = unwrap(node);
        if (unwrapped != null && unwrapped.getClass() == SelectWrapper.class) {
            SelectWrapper<?> select = (SelectWrapper<?>) unwrapped;
            return new CountCacheKeyMaterial(
                    Kind.SELECT,
                    unwrapped.getClass(),
                    select.getMeta(),
                    select.getFrom(),
                    select.where(),
                    null,
                    null,
                    null);
        }
        if (unwrapped != null && unwrapped.getClass() == GroupWrapper.class) {
            GroupWrapper<?> group = (GroupWrapper<?>) unwrapped;
            return new CountCacheKeyMaterial(
                    Kind.GROUP,
                    unwrapped.getClass(),
                    group.getMeta(),
                    group.getFrom(),
                    group.where(),
                    group.aggregations(),
                    group.metrics(),
                    group.having());
        }
        return unwrapped;
    }

    private static INode unwrap(INode node) {
        INode current = node;
        Set<INode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && current.getClass() == CountWrapper.class && visited.add(current)) {
            CountWrapper count = (CountWrapper) current;
            INode nested = count.getWrapper();
            if (nested == null) return current;
            current = nested;
        }
        return current;
    }

    private static Map<String, Object> immutableMeta(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }

    private enum Kind {
        SELECT,
        GROUP
    }
}

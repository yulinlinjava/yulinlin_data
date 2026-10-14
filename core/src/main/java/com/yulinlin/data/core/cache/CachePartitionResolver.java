package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.ICondition;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.node.base.Nil;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Or;
import com.yulinlin.data.core.node.predicate.Predicates;
import com.yulinlin.data.core.wrapper.impl.AbstractDeleteWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractFieldsWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractUpdateWrapper;

import java.util.Optional;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Recognizes the deliberately small, safe subset used by equality-condition cache partitions. */
public final class CachePartitionResolver {

    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private CachePartitionResolver() {
    }

    /** Resolves an unlocked, single-table select whose effective condition tree contains one Eq. */
    public static Optional<CachePartition> query(CacheNamespace namespace, INode node) {
        return query(namespace, node, Function.identity(), Function.identity());
    }

    /** Uses the same column mapping and value encoding as the owning Session. */
    public static Optional<CachePartition> query(CacheNamespace namespace, INode node,
                                                  Function<String, String> columnMapper,
                                                  Function<Object, Object> valueEncoder) {
        if (!(node instanceof AbstractSelectWrapper<?, ?, ?, ?> wrapper)
                || wrapper.isLock()
                || !sameTable(namespace, wrapper.getFrom())) {
            return Optional.empty();
        }
        Store store = (Store) wrapper.getFrom();
        return partition(namespace, store, singleEq(wrapper.where().getCondition()),
                columnMapper, valueEncoder);
    }

    /**
     * Resolves an exact update/delete bucket. Updates that change the bucket field are deliberately
     * treated as imprecise, so callers can fall back to table invalidation.
     */
    public static Optional<CachePartition> write(CacheNamespace namespace, INode node) {
        return write(namespace, node, Function.identity(), Function.identity());
    }

    /** Uses the same column mapping and value encoding as the owning Session. */
    public static Optional<CachePartition> write(CacheNamespace namespace, INode node,
                                                  Function<String, String> columnMapper,
                                                  Function<Object, Object> valueEncoder) {
        if (node instanceof AbstractDeleteWrapper<?, ?, ?> wrapper) {
            if (!sameTable(namespace, wrapper.getFrom())) return Optional.empty();
            return partition(namespace, (Store) wrapper.getFrom(), singleEq(wrapper.where().getCondition()),
                    columnMapper, valueEncoder);
        }
        if (node instanceof AbstractUpdateWrapper<?, ?, ?, ?> wrapper) {
            if (!sameTable(namespace, wrapper.getFrom())) return Optional.empty();
            Store store = (Store) wrapper.getFrom();
            Eq condition = singleEq(wrapper.where().getCondition());
            if (condition == null || !(wrapper.fields() instanceof AbstractFieldsWrapper<?, ?> fields)) {
                return Optional.empty();
            }
            String conditionField = canonicalField(store, condition.getKey(), columnMapper);
            if (conditionField == null) return Optional.empty();
            for (AtomicValue field : fields.getList()) {
                String updateField = canonicalField(store, field.getKey(), columnMapper);
                if (updateField == null || conditionField.equals(updateField)) {
                    return Optional.empty();
                }
            }
            return partition(namespace, conditionField, condition.getValue(), valueEncoder);
        }
        return Optional.empty();
    }

    private static Optional<CachePartition> partition(CacheNamespace namespace, Store store, Eq condition,
                                                       Function<String, String> columnMapper,
                                                       Function<Object, Object> valueEncoder) {
        if (condition == null) return Optional.empty();
        String field = canonicalField(store, condition.getKey(), columnMapper);
        if (field == null) return Optional.empty();
        return partition(namespace, field, condition.getValue(), valueEncoder);
    }

    private static Optional<CachePartition> partition(CacheNamespace namespace, String field, Object value,
                                                       Function<Object, Object> valueEncoder) {
        try {
            return CachePartition.of(namespace, field, valueEncoder.apply(value));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String canonicalField(Store store, Object value,
                                         Function<String, String> columnMapper) {
        if (!(value instanceof String raw)) return null;
        String field = raw.trim();
        int dot = field.indexOf('.');
        if (dot >= 0) {
            if (dot != field.lastIndexOf('.')) return null;
            String prefix = field.substring(0, dot);
            field = field.substring(dot + 1);
            if (!simple(prefix) || !simple(field)) return null;
            String alias = store.getAlias();
            String table = store.getName();
            if (!prefix.equalsIgnoreCase(table)
                    && (alias == null || !prefix.equalsIgnoreCase(alias))) {
                return null;
            }
        } else if (!simple(field)) {
            return null;
        } else {
            try {
                field = columnMapper.apply(field);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (!simple(field)) return null;
        return field.toLowerCase(Locale.ROOT);
    }

    private static boolean simple(String value) {
        return value != null && SIMPLE_NAME.matcher(value).matches();
    }

    private static Eq singleEq(ICondition condition) {
        Match match = match(condition);
        return match.valid && match.count == 1 ? match.condition : null;
    }

    private static Match match(ICondition condition) {
        if (condition == null || condition instanceof Nil) return Match.EMPTY;
        if (condition instanceof Eq equality) return new Match(equality, 1, true);
        if (!(condition instanceof And) && !(condition instanceof Or)) return Match.INVALID;

        Match result = Match.EMPTY;
        for (ICondition child : ((Predicates) condition).getList()) {
            Match current = match(child);
            if (!current.valid) return Match.INVALID;
            if (current.count == 0) continue;
            if (result.count != 0 || current.count != 1) return Match.INVALID;
            result = current;
        }
        return result;
    }

    private static boolean sameTable(CacheNamespace namespace, Object from) {
        if (namespace == null || !(from instanceof Store store)
                || store.getName() == null || store.getName().isBlank()) {
            return false;
        }
        try {
            return namespace.equals(new CacheNamespace(namespace.sessionType(), namespace.group(), store.getName()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private record Match(Eq condition, int count, boolean valid) {
        private static final Match EMPTY = new Match(null, 0, true);
        private static final Match INVALID = new Match(null, 0, false);
    }
}

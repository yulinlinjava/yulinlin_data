package com.yulinlin.common.model;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.model.PrimaryUtil;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.lambda.LambdaUtils;
import com.yulinlin.data.lang.reflection.AnnotationUtil;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 对一行实体的一个或多个数值字段执行原子增减。
 * <p>
 * {@link #execute()} 立即写入数据库；{@link #submit()} 在进程内按数据源、实体和主键聚合后写入。
 * Builder 只能终结一次，也不应跨线程共享。
 */
public final class EntityCounter<E> {

    private final String session;
    private final Class<E> entityClass;
    private final Object id;
    private final String primaryKey;
    private final LinkedHashMap<String, Long> deltas = new LinkedHashMap<>();
    private Boolean clearCache;
    private boolean completed;

    private EntityCounter(String session, Class<E> entityClass, Object id) {
        this.session = normalizeSession(session);
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.id = Objects.requireNonNull(id, "id");
        this.primaryKey = PrimaryUtil.getPrimaryKeyName(entityClass);
    }

    /** 使用 SessionUtil/RouteSession 的默认数据源规则。 */
    public static <E> EntityCounter<E> newInstance(Class<E> entityClass, Object id) {
        return new EntityCounter<>(null, entityClass, id);
    }

    /** 显式选择数据源组。 */
    public static <E> EntityCounter<E> newInstance(String session, Class<E> entityClass, Object id) {
        if (session == null || session.isBlank()) {
            throw new IllegalArgumentException("session must not be blank; omit it to use the default session");
        }
        return new EntityCounter<>(session, entityClass, id);
    }

    public EntityCounter<E> inc(LambdaPropertyFunction<E> field) {
        return inc(field, 1);
    }

    public EntityCounter<E> inc(LambdaPropertyFunction<E> field, Number value) {
        return add(field, exactPositiveLong(value, "increment"));
    }

    public EntityCounter<E> dec(LambdaPropertyFunction<E> field) {
        return dec(field, 1);
    }

    public EntityCounter<E> dec(LambdaPropertyFunction<E> field, Number value) {
        long amount = exactPositiveLong(value, "decrement");
        return add(field, -amount);
    }

    /** 覆盖本次操作的 yulinlin.data.counter.clear-cache 默认值。 */
    public EntityCounter<E> clearCache(boolean clearCache) {
        ensureOpen();
        this.clearCache = clearCache;
        return this;
    }

    /** 立即执行并返回数据库影响行数，可加入当前事务。 */
    public int execute() {
        return EntityCounterManager.required().execute(complete());
    }

    /** 提交到进程内聚合器；该操作不加入调用线程事务，也不代表数据已经落库。 */
    public void submit() {
        EntityCounterManager.required().submit(complete());
    }

    private EntityCounter<E> add(LambdaPropertyFunction<E> function, long delta) {
        ensureOpen();
        Objects.requireNonNull(function, "field");
        if (delta == 0) return this;

        Field field = LambdaUtils.lambdaMethodNameToField(function);
        validateField(field);
        deltas.merge(field.getName(), delta, Math::addExact);
        if (deltas.get(field.getName()) == 0) deltas.remove(field.getName());
        return this;
    }

    private void validateField(Field field) {
        if (!field.getDeclaringClass().isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("Counter field does not belong to " + entityClass.getName()
                    + ": " + field.getName());
        }
        if (!numeric(field.getType())) {
            throw new IllegalArgumentException("Counter field must be numeric: "
                    + entityClass.getName() + "." + field.getName());
        }
        JoinField joinField = AnnotationUtil.findAnnotation(field, JoinField.class);
        if (joinField != null && (!joinField.exist() || !joinField.update())) {
            throw new IllegalArgumentException("Counter field is not writable: "
                    + entityClass.getName() + "." + field.getName());
        }
        JoinMeta joinMeta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
        if (joinMeta != null && joinMeta.primaryKey()) {
            throw new IllegalArgumentException("Primary key cannot be used as a counter field: " + field.getName());
        }
    }

    private EntityCounterManager.Command complete() {
        ensureOpen();
        completed = true;
        Map<String, Long> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(deltas));
        return new EntityCounterManager.Command(session, entityClass, id, primaryKey, snapshot, clearCache);
    }

    private void ensureOpen() {
        if (completed) throw new IllegalStateException("EntityCounter has already been executed or submitted");
    }

    private static String normalizeSession(String session) {
        return session == null || session.isBlank() ? null : session;
    }

    private static boolean numeric(Class<?> type) {
        if (Number.class.isAssignableFrom(type)) return true;
        return type == byte.class || type == short.class || type == int.class || type == long.class
                || type == float.class || type == double.class;
    }

    private static long exactPositiveLong(Number value, String label) {
        Objects.requireNonNull(value, label);
        final long result;
        try {
            if (value instanceof BigDecimal decimal) result = decimal.longValueExact();
            else if (value instanceof BigInteger integer) result = integer.longValueExact();
            else if (value instanceof Double || value instanceof Float) {
                double number = value.doubleValue();
                if (!Double.isFinite(number)) throw new ArithmeticException("not finite");
                result = BigDecimal.valueOf(number).longValueExact();
            } else result = value.longValue();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(label + " must be an integer within long range: " + value, error);
        }
        if (result < 0) throw new IllegalArgumentException(label + " must not be negative: " + value);
        return result;
    }
}

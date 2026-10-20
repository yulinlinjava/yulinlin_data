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
 * 通过 {@link #update(Class, Object)} 或 {@link #update(Object)} 按主键原子增减，
 * 或通过 {@link #insert(Object)} 按非数字维度聚合插入。
 * <p>
 * {@link #execute()} 立即写入数据库；{@link #submit()} 在进程内按数据源、实体和主键聚合后写入。
 * Builder 只能终结一次，也不应跨线程共享。
 */
public final class EntityCounter<E> {

    /** 模型中的数字是增量，不是目标值；只读取主键和可更新数字字段。 */
    public static <E> Update<E> update(E model) {
        return new Update<>(null, model);
    }

    public static <E> Update<E> update(String session, E model) {
        if (session == null || session.isBlank()) throw new IllegalArgumentException("数据源名称不能为空");
        return new Update<>(session, model);
    }

    /** 模型增量更新；与链式 inc/dec 共用按主键聚合区。 */
    public static final class Update<E> {
        private final String session;
        private final E model;
        private Boolean clearCache;
        private boolean completed;

        private Update(String session, E model) {
            this.session = session;
            this.model = Objects.requireNonNull(model, "model");
        }

        public Update<E> clearCache(boolean clearCache) {
            ensureOpen();
            this.clearCache = clearCache;
            return this;
        }

        /** 立即按主键执行数字增量，可加入当前事务，不执行普通实体更新钩子。 */
        public int execute() {
            return EntityCounterManager.required().executeUpdate(complete());
        }

        /** 此刻读取模型快照，提交到聚合区；必须在事务外调用。 */
        public void submit() {
            EntityCounterManager.required().submit(complete());
        }

        private EntityCounterManager.Command complete() {
            ensureOpen();
            completed = true;
            return CounterUpdate.snapshot(session, model, clearCache);
        }

        private void ensureOpen() {
            if (completed) throw new IllegalStateException("模型计数更新已经执行或提交");
        }
    }

    /** 按非数字字段维度聚合插入；不传数据源时使用实体注解和当前默认选库规则。 */
    public static <E> Insert<E> insert(E entity) {
        return new Insert<>(null, entity);
    }

    public static <E> Insert<E> insert(String session, E entity) {
        if (session == null || session.isBlank()) throw new IllegalArgumentException("数据源名称不能为空");
        return new Insert<>(session, entity);
    }

    /** 插入模式与按主键 inc/dec 分开，防止把统计插入误解释成更新。 */
    public static final class Insert<E> {
        private final String session;
        private final E entity;
        private Boolean clearCache;
        private boolean completed;

        private Insert(String session, E entity) {
            this.session = session;
            this.entity = Objects.requireNonNull(entity, "entity");
        }

        public Insert<E> clearCache(boolean clearCache) {
            ensureOpen();
            this.clearCache = clearCache;
            return this;
        }

        /** 立即插入一次值快照，可加入当前事务。 */
        public int execute() {
            ensureOpen();
            completed = true;
            return EntityCounterManager.required().executeInsert(session, entity, clearCache);
        }

        /** 提交时固定值快照，不修改或清零传入对象；每次提交都是一份新增贡献。 */
        public void submit() {
            ensureOpen();
            completed = true;
            EntityCounterManager.required().submitInsert(session, entity, clearCache);
        }

        private void ensureOpen() {
            if (completed) throw new IllegalStateException("聚合插入已经执行或提交");
        }
    }

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

    /** 更新已有记录；使用 SessionUtil/RouteSession 的默认数据源规则。 */
    public static <E> EntityCounter<E> update(Class<E> entityClass, Object id) {
        return new EntityCounter<>(null, entityClass, id);
    }

    /** 更新已有记录；显式选择数据源组，不会自动插入不存在的记录。 */
    public static <E> EntityCounter<E> update(String session, Class<E> entityClass, Object id) {
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
        return EntityCounterManager.required().executeUpdate(complete());
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
        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        deltas.forEach((field, delta) -> snapshot.put(field, BigDecimal.valueOf(delta)));
        return new EntityCounterManager.Command(session, entityClass, CounterUpdate.freezeId(id),
                primaryKey, Collections.unmodifiableMap(snapshot), clearCache);
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

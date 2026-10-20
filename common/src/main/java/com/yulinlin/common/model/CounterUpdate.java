package com.yulinlin.common.model;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/** 模型更新只读取主键和可更新数值，不调用实体更新钩子，不产生覆盖赋值。 */
final class CounterUpdate {
    private record Property(String name, ReflectionUtil.PropertyAccess access) { }
    private record Metadata(Property primary, List<Property> values) { }

    private static final ClassValue<Metadata> METADATA = new ClassValue<>() {
        @Override protected Metadata computeValue(Class<?> type) {
            if (AnnotationUtil.findAnnotation(type, JoinTable.class) == null)
                throw new IllegalArgumentException("计数更新实体缺少 @JoinTable：" + type.getName());
            List<Field> fields = ReflectionUtil.getAllDeclaredFields(type).stream()
                    .filter(field -> !Modifier.isStatic(field.getModifiers())
                            && !Modifier.isTransient(field.getModifiers()) && !field.isSynthetic())
                    .sorted(Comparator.comparing(Field::getName)).toList();
            List<Field> primary = fields.stream().filter(field -> {
                JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
                return meta != null && meta.primaryKey();
            }).toList();
            if (primary.size() != 1) throw new IllegalArgumentException("计数更新实体必须有且只有一个主键：" + type.getName());
            Field id = primary.getFirst();
            JoinField mapping = AnnotationUtil.findAnnotation(id, JoinField.class);
            if (mapping != null && !mapping.exist())
                throw new IllegalArgumentException("计数更新主键不能设置 exist=false：" + id);
            List<Property> values = fields.stream().filter(field -> !field.equals(id))
                    .filter(field -> {
                        JoinField annotation = AnnotationUtil.findAnnotation(field, JoinField.class);
                        return annotation == null || (annotation.exist() && annotation.update());
                    })
                    .map(field -> new Property(field.getName(), ReflectionUtil.property(field))).toList();
            return new Metadata(new Property(id.getName(), ReflectionUtil.property(id)), values);
        }
    };

    static EntityCounterManager.Command snapshot(String session, Object entity, Boolean clearCache) {
        Metadata metadata = METADATA.get(entity.getClass());
        Object id = freezeId(metadata.primary().access().get(entity));
        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        for (Property property : metadata.values()) {
            Object value = property.access().get(entity);
            if (!(value instanceof Number number)) continue;
            BigDecimal delta = CounterNumbers.snapshot(number);
            if (delta.signum() != 0) deltas.put(property.name(), delta);
        }
        return new EntityCounterManager.Command(session, entity.getClass(), id,
                metadata.primary().name(), Map.copyOf(deltas), clearCache);
    }

    static Object freezeId(Object id) {
        if (id == null || id instanceof CharSequence text && text.toString().isBlank())
            throw new IllegalArgumentException("计数更新主键不能为空");
        // 原子计数器的 equals 按对象身份比较，主键须转为稳定标量再参与聚合键。
        if (id instanceof AtomicInteger value) return value.get();
        if (id instanceof AtomicLong value) return value.get();
        if (id instanceof LongAdder value) return value.sum();
        if (id instanceof DoubleAdder value) return value.sum();
        return ReflectionUtil.deepClone(id);
    }
}

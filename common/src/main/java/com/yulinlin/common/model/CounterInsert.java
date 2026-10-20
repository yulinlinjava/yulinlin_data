package com.yulinlin.common.model;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/** 聚合插入的字段元数据、值快照与数字求和；不负责调度和数据库操作。 */
final class CounterInsert {
    private record Property(Field field, ReflectionUtil.PropertyAccess access) { }

    private static final ClassValue<List<Property>> PROPERTIES = new ClassValue<>() {
        @Override protected List<Property> computeValue(Class<?> type) {
            if (AnnotationUtil.findAnnotation(type, JoinTable.class) == null)
                throw new IllegalArgumentException("聚合插入实体缺少 @JoinTable：" + type.getName());
            if (Modifier.isAbstract(type.getModifiers()))
                throw new IllegalArgumentException("聚合插入实体不能是抽象类：" + type.getName());
            try {
                type.getDeclaredConstructor();
            } catch (NoSuchMethodException error) {
                throw new IllegalArgumentException("聚合插入实体需要无参构造器：" + type.getName(), error);
            }
            return ReflectionUtil.getAllDeclaredFields(type).stream()
                    .filter(field -> !Modifier.isStatic(field.getModifiers())
                            && !Modifier.isTransient(field.getModifiers()) && !field.isSynthetic())
                    .filter(field -> {
                        JoinField annotation = AnnotationUtil.findAnnotation(field, JoinField.class);
                        return annotation == null || annotation.exist();
                    })
                    .sorted(Comparator.comparing(Field::getName))
                    .map(field -> {
                        if (Modifier.isFinal(field.getModifiers()))
                            throw new IllegalArgumentException("聚合插入要求可写字段：" + field);
                        return new Property(field, ReflectionUtil.property(field));
                    }).toList();
        }
    };

    record Key(String session, Class<?> entityClass, Map<String, String> dimensions) { }

    record Snapshot(Key key, Map<String, Object> dimensions, Map<String, Numeric> numbers) {
        Snapshot merge(Snapshot other) {
            if (!key.equals(other.key)) throw new IllegalArgumentException("不能合并不同维度的统计数据");
            Map<String, Numeric> merged = new LinkedHashMap<>(numbers);
            other.numbers.forEach((name, value) ->
                    merged.merge(name, value, (left, right) -> left.add(right, name)));
            // 所有字段校验成功后才发布，溢出时不能留下部分字段已经累加的状态。
            return new Snapshot(key, dimensions, Map.copyOf(merged));
        }

        Object entity() {
            Object entity = ReflectionUtil.newInstance(key.entityClass());
            for (Property property : PROPERTIES.get(key.entityClass())) {
                String name = property.field().getName();
                Numeric number = numbers.get(name);
                Object value = number != null ? number.value() : copy(dimensions.get(name));
                // 去掉构造器默认值，不能让提交时为 null 的字段在刷新时重新出现。
                if (value != null || !property.field().getType().isPrimitive())
                    property.access().set(entity, value);
            }
            return entity;
        }
    }

    static Snapshot snapshot(String session, Object entity) {
        Map<String, String> key = new LinkedHashMap<>();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        Map<String, Numeric> numbers = new LinkedHashMap<>();
        for (Property property : PROPERTIES.get(entity.getClass())) {
            Object value = property.access().get(entity);
            if (value == null) continue;
            String name = property.field().getName();
            if (value instanceof Number number) {
                numbers.put(name, Numeric.of(number));
            } else {
                if (value.getClass().getPackageName().equals("java.util.concurrent.atomic")
                        && !(value instanceof AtomicBoolean))
                    throw new IllegalArgumentException("暂不支持此原子字段类型：" + property.field());
                Object frozen = copy(value);
                // 时间不截断；字段名也进入 Key，null 不占位且不是通配符。
                key.put(name, value instanceof AtomicBoolean ? frozen.toString() : value.toString());
                dimensions.put(name, frozen);
            }
        }
        return new Snapshot(new Key(session, entity.getClass(), Map.copyOf(key)),
                Map.copyOf(dimensions), Map.copyOf(numbers));
    }

    private static Object copy(Object value) {
        if (value instanceof AtomicBoolean atomic) return new AtomicBoolean(atomic.get());
        return ReflectionUtil.deepClone(value);
    }

    /** 使用不可变精确值保存快照，避免 long 溢出及 double 转换丢失大整数。 */
    record Numeric(Class<?> type, BigDecimal sum) {
        static Numeric of(Number value) {
            return new Numeric(value.getClass(), CounterNumbers.snapshot(value));
        }

        Numeric add(Numeric other, String field) {
            if (type != other.type)
                throw new IllegalArgumentException("同一统计字段的数字类型必须一致：" + field);
            Numeric result = new Numeric(type, sum.add(other.sum));
            try {
                result.validate();
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("统计字段累加溢出：" + field, error);
            }
            return result;
        }

        private void validate() {
            if (type == Byte.class) sum.byteValueExact();
            else if (type == Short.class) sum.shortValueExact();
            else if (type == Integer.class || type == AtomicInteger.class) sum.intValueExact();
            else if (type == Long.class || type == AtomicLong.class || type == LongAdder.class) sum.longValueExact();
            else if (type == Float.class && !Float.isFinite(sum.floatValue()))
                throw new ArithmeticException("float 溢出");
            else if ((type == Double.class || type == DoubleAdder.class) && !Double.isFinite(sum.doubleValue()))
                throw new ArithmeticException("double 溢出");
        }

        Object value() {
            if (type == Byte.class) return sum.byteValueExact();
            if (type == Short.class) return sum.shortValueExact();
            if (type == Integer.class) return sum.intValueExact();
            if (type == Long.class) return sum.longValueExact();
            if (type == BigInteger.class) return sum.toBigIntegerExact();
            if (type == BigDecimal.class) return sum;
            if (type == Float.class) return sum.floatValue();
            if (type == Double.class) return sum.doubleValue();
            if (type == AtomicInteger.class) return new AtomicInteger(sum.intValueExact());
            if (type == AtomicLong.class) return new AtomicLong(sum.longValueExact());
            if (type == LongAdder.class) {
                LongAdder value = new LongAdder();
                value.add(sum.longValueExact());
                return value;
            }
            DoubleAdder value = new DoubleAdder();
            value.add(sum.doubleValue());
            return value;
        }
    }
}

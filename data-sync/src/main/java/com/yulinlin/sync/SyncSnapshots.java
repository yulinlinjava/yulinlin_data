package com.yulinlin.sync;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.base.Expression;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.objenesis.SpringObjenesis;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** 只复制框架拥有的节点结构；字段值复用 lang 深复制，不保留业务 root/连接/代理。 */
final class SyncSnapshots {
    private static final SpringObjenesis ALLOCATOR = new SpringObjenesis();
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    if (Modifier.isFinal(field.getModifiers()) || !field.trySetAccessible())
                        throw new IllegalArgumentException("同步节点字段无法快照：" + field);
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
    };
    private final IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();

    static ExecuteRequest<?> copy(ExecuteRequest<?> source) {
        ExecuteRequest<?> result = switch (source.getRequestType()) {
            case insert -> ExecuteRequest.ofInsert(source.getFromClass());
            case update -> ExecuteRequest.ofUpdate(source.getFromClass());
            case delete -> ExecuteRequest.ofDelete(source.getFromClass());
            default -> throw new IllegalArgumentException("同步仅支持插入、更新、删除");
        };
        result.setEntityClass(source.getEntityClass());
        result.setBatch(source.isBatch());
        result.setClearCache(source.getClearCache());
        result.addCacheNamespaces(source.getCacheNamespaces().toArray(String[]::new));
        SyncSnapshots snapshot = new SyncSnapshots();
        for (INode node : source.getWrappers()) result.addRequest((INode) snapshot.copyValue(node));
        return result;
    }

    private Object copyValue(Object value) {
        if (value == null) return null;
        if (value instanceof CommandNode || value instanceof Expression)
            throw new IllegalArgumentException("同步不支持自定义 SQL/Expression，请改用可跨库解析的条件节点");
        if (copies.containsKey(value)) return copies.get(value);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            copies.put(value, result);
            map.forEach((key, item) -> result.put(copyValue(key), copyValue(item)));
            return result;
        }
        if (value instanceof Collection<?> items) {
            Collection<Object> result = value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
            copies.put(value, result);
            for (Object item : items) result.add(copyValue(item));
            return result;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object result = Array.newInstance(type.getComponentType(), length);
            copies.put(value, result);
            for (int i = 0; i < length; i++) Array.set(result, i, copyValue(Array.get(value, i)));
            return result;
        }
        if (value instanceof INode) {
            String name = type.getName();
            if (!name.startsWith("com.yulinlin.data.core.node.")
                    && !name.startsWith("com.yulinlin.data.core.wrapper."))
                throw new IllegalArgumentException("同步暂不支持第三方自定义节点：" + name);
            Object result = ALLOCATOR.newInstance(type);
            copies.put(value, result);
            try {
                for (Field field : FIELDS.get(type)) field.set(result, copyValue(field.get(value)));
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("同步节点快照失败：" + name, error);
            }
            return result;
        }
        Object result = ReflectionUtil.deepClone(value);
        copies.put(value, result);
        return result;
    }
}

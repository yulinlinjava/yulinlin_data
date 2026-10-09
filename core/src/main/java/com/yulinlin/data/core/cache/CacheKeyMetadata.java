package com.yulinlin.data.core.cache;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Canonical metadata writer used by {@link CacheKey}. */
final class CacheKeyMetadata {

    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) continue;
                    field.trySetAccessible();
                    fields.add(field);
                }
            }
            fields.sort(Comparator.comparing(field -> field.getDeclaringClass().getName() + "#" + field.getName()));
            return fields.toArray(Field[]::new);
        }
    };

    private CacheKeyMetadata() {
    }

    static void write(Murmur3Hash128 target, Object value) {
        new Writer(new HashTarget(target)).write(value);
        target.putAscii('|');
    }

    private static final class Writer {
        private final Target target;
        private final IdentityHashMap<Object, Integer> visited = new IdentityHashMap<>();

        private Writer(Target target) {
            this.target = target;
        }

        private void write(Object value) {
            if (value == null) { token("null", ""); return; }
            if (value instanceof CharSequence || value instanceof Character) {
                token(value.getClass().getName(), value.toString()); return;
            }
            if (value instanceof BigDecimal decimal) {
                token(BigDecimal.class.getName(), decimal.toPlainString()); return;
            }
            if (value instanceof Number || value instanceof Boolean || value instanceof UUID) {
                token(value.getClass().getName(), value.toString()); return;
            }
            if (value instanceof Enum<?> enumValue) {
                token(enumValue.getDeclaringClass().getName(), enumValue.name()); return;
            }
            if (value instanceof Class<?> type) { token("class", type.getName()); return; }
            if (value instanceof Date date) {
                token(value.getClass().getName(), Long.toString(date.getTime())); return;
            }
            if (value instanceof TemporalAccessor) {
                token(value.getClass().getName(), value.toString()); return;
            }
            if (value instanceof byte[] bytes) {
                token("bytes", Base64.getEncoder().encodeToString(bytes)); return;
            }
            if (value instanceof Optional<?> optional) {
                target.append("optional["); write(optional.orElse(null)); target.append(']'); return;
            }

            Integer reference = visited.get(value);
            if (reference != null) { token("ref", reference.toString()); return; }
            visited.put(value, visited.size());

            Class<?> type = value.getClass();
            if (type.isArray()) {
                target.append(type.getName()).append('[');
                for (int index = 0; index < Array.getLength(value); index++) write(Array.get(value, index));
                target.append(']');
            } else if (value instanceof Map<?, ?> map) {
                writeMap(map);
            } else if (value instanceof Collection<?> collection) {
                target.append(type.getName()).append('[');
                for (Object element : collection) write(element);
                target.append(']');
            } else {
                writeObject(value, type);
            }
        }

        private void writeMap(Map<?, ?> map) {
            List<MapEntry> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                StringBuilder key = new StringBuilder();
                new Writer(new TextTarget(key)).write(entry.getKey());
                entries.add(new MapEntry(key.toString(), entry.getValue()));
            }
            entries.sort(Comparator.comparing(MapEntry::key));
            target.append(map.getClass().getName()).append('{');
            for (MapEntry entry : entries) { token("key", entry.key()); write(entry.value()); }
            target.append('}');
        }

        private void writeObject(Object value, Class<?> type) {
            target.append(type.getName()).append('{');
            for (Field field : FIELDS.get(type)) {
                token("field", field.getDeclaringClass().getName() + "#" + field.getName());
                try {
                    if (!field.canAccess(value) && !field.trySetAccessible()) {
                        token("unavailable", field.getType().getName());
                    } else {
                        write(field.get(value));
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot read cache-key metadata " + field, error);
                }
            }
            target.append('}');
        }

        private void token(String type, String value) {
            target.append(Integer.toString(type.length())).append(':').append(type)
                    .append('=').append(Integer.toString(value.length())).append(':').append(value).append(';');
        }
    }

    private interface Target {
        Target append(CharSequence value);
        Target append(char value);
    }

    private record HashTarget(Murmur3Hash128 hasher) implements Target {
        @Override
        public Target append(CharSequence value) {
            hasher.putUtf8(value);
            return this;
        }

        @Override
        public Target append(char value) {
            hasher.putAscii(value);
            return this;
        }
    }

    private record TextTarget(StringBuilder builder) implements Target {
        @Override
        public Target append(CharSequence value) {
            builder.append(value);
            return this;
        }

        @Override
        public Target append(char value) {
            builder.append(value);
            return this;
        }
    }

    private record MapEntry(String key, Object value) {
    }
}

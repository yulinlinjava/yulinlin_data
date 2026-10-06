package com.yulinlin.jdbc.postgresql.parse;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.Map;

/** Resolves explicit @JoinField(fullText=true) declarations for the current request entity. */
public final class PostgresqlFullTextMetadata {
    private PostgresqlFullTextMetadata() { }

    public static boolean enabled(IParamsContext params, String requestedName) {
        Class<?> source = params.getSourceClass();
        if (source == null || source == Object.class || Map.class.isAssignableFrom(source)
                || requestedName == null || requestedName.contains("->")) return false;
        String name = requestedName;
        int qualifier = name.lastIndexOf('.');
        if (qualifier >= 0) name = name.substring(qualifier + 1);
        String property = params.toKey(name);
        Field field = ReflectionUtil.findField(source, property);
        if (field == null) return false;
        JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
        return mapping != null && mapping.exist() && mapping.function().isEmpty() && mapping.fullText();
    }
}

package com.yulinlin.data.core.schema;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recursively discovers one explicit schema owner for each physical table, index, or collection.
 * Package patterns accept {@code *} for one package segment and {@code **} for any depth.
 */
public final class SchemaEntityScanner {
    private SchemaEntityScanner() { }

    public static List<Class<?>> scan(Collection<String> packages) {
        return scan(packages, ClassUtils.getDefaultClassLoader());
    }

    static List<Class<?>> scan(Collection<String> packages, ClassLoader classLoader) {
        if (packages == null || packages.isEmpty()) return List.of();
        ClassLoader loader = classLoader == null ? SchemaEntityScanner.class.getClassLoader() : classLoader;
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(loader));
        scanner.addIncludeFilter(new AnnotationTypeFilter(JoinTable.class, true));

        Set<String> classNames = new LinkedHashSet<>();
        for (String configured : packages) {
            if (configured == null || configured.isBlank()) continue;
            String basePackage = packagePattern(configured.trim());
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                if (candidate.getBeanClassName() != null) classNames.add(candidate.getBeanClassName());
            }
        }

        List<Class<?>> candidates = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                Class<?> type = ClassUtils.forName(className, loader);
                JoinTable table = AnnotationUtils.findAnnotation(type, JoinTable.class);
                if (isSchemaOwner(type, table)) candidates.add(type);
            } catch (LinkageError | ClassNotFoundException error) {
                throw new IllegalStateException("无法加载 Schema 实体；请检查依赖版本和运行时 classpath。实体：" + className, error);
            }
        }
        candidates.sort(java.util.Comparator.comparing(Class::getName));

        Map<String, Class<?>> owners = new LinkedHashMap<>();
        for (Class<?> type : candidates) {
            JoinTable table = AnnotationUtils.findAnnotation(type, JoinTable.class);
            String key = table.value().toLowerCase(Locale.ROOT);
            Class<?> existing = owners.putIfAbsent(key, type);
            if (existing != null && existing != type) {
                throw new IllegalStateException("同一张表存在多个自动建表实体。表：" + table.value()
                        + ": " + existing.getName() + " 与 " + type.getName()
                        + "\n处理方式：同一张表仅保留一个完整实体的 @JoinTable(autoSchema=true)，其他实体设为 false；测试实体应位于业务扫描包之外");
            }
        }
        return List.copyOf(owners.values());
    }

    private static String packagePattern(String value) {
        String[] segments = value.split("\\.", -1);
        for (String segment : segments) {
            if (segment.equals("*") || segment.equals("**")) continue;
            if (!segment.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*")) {
                throw new IllegalArgumentException("Schema 扫描包表达式不合法：" + value
                        + "\n处理方式：使用完整 Java 包段；* 匹配一级包，** 匹配任意层级，不支持半段通配符");
            }
        }
        return value;
    }

    private static boolean isSchemaOwner(Class<?> type, JoinTable table) {
        return table != null && table.autoSchema() && !table.value().isBlank()
                && table.left().isEmpty() && table.right().isEmpty() && table.on().isEmpty()
                && AnnotationUtils.findAnnotation(type, JoinTableList.class) == null
                && !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }
}

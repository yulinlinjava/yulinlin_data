package com.yulinlin.jdbc.schema;

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
 * Recursively discovers the single schema-owning entity for each physical table.
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
                throw new IllegalStateException("Cannot load schema entity " + className, error);
            }
        }
        candidates.sort(java.util.Comparator.comparing(Class::getName));

        Map<String, Class<?>> owners = new LinkedHashMap<>();
        for (Class<?> type : candidates) {
            JoinTable table = AnnotationUtils.findAnnotation(type, JoinTable.class);
            String key = table.value().toLowerCase(Locale.ROOT);
            Class<?> existing = owners.putIfAbsent(key, type);
            if (existing != null && existing != type) {
                throw new IllegalStateException("Multiple auto-schema owners for table " + table.value()
                        + ": " + existing.getName() + " and " + type.getName()
                        + "; mark exactly one complete entity with @JoinTable(autoSchema = true)");
            }
        }
        return List.copyOf(owners.values());
    }

    private static String packagePattern(String value) {
        String[] segments = value.split("\\.", -1);
        for (String segment : segments) {
            if (segment.equals("*") || segment.equals("**")) continue;
            if (!segment.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*")) {
                throw new IllegalArgumentException("Invalid schema package pattern: " + value
                        + "; use Java package segments, * for one level, or ** for any depth");
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

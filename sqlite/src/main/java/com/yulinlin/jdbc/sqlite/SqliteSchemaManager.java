package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Creates missing local tables only. Existing data and schemas are never migrated. */
public final class SqliteSchemaManager {
    private static final Logger log = LoggerFactory.getLogger(SqliteSchemaManager.class);
    private final SqliteDataSource dataSource;
    private final boolean underscore;

    public SqliteSchemaManager(SqliteDataSource dataSource, boolean mapUnderscoreToCamelCase) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.underscore = mapUnderscoreToCamelCase;
    }

    public record SchemaResult(int entities, int created, int existing) {}
    private record Column(String name, String type, boolean primary) {}
    private record Table(String name, List<Column> columns) {}

    /** Scans annotated concrete classes without instantiating entities or initializing classes. */
    public SchemaResult scanAndCreate(String... packages) {
        if (packages == null || packages.length == 0) throw new IllegalArgumentException("SQLite schema packages must not be empty");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(JoinTable.class));
        Map<String, Class<?>> classes = new TreeMap<>();
        for (String base : packages) {
            if (base == null || !base.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*")) {
                throw new IllegalArgumentException("SQLite schema requires explicit package names: " + base);
            }
            for (var bean : scanner.findCandidateComponents(base)) {
                String name = bean.getBeanClassName();
                try {
                    Class<?> type = Class.forName(name, false, ClassUtils.getDefaultClassLoader());
                    JoinTable table = AnnotationUtil.findAnnotation(type, JoinTable.class);
                    if (isSimpleTable(type, table)) classes.put(name, type);
                    else log.debug("Skipping SQLite query-only model {}", name);
                } catch (ClassNotFoundException | LinkageError e) {
                    throw new IllegalStateException("Cannot load SQLite entity " + name, e);
                }
            }
        }
        if (classes.isEmpty()) throw new IllegalArgumentException("No SQLite table entities found in " + Arrays.toString(packages));
        return createTables(classes.values().toArray(Class<?>[]::new));
    }

    /** Uses a dedicated local transaction. Call outside business transactions. */
    public synchronized SchemaResult createTables(Class<?>... entities) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Run SQLite schema initialization outside business transactions");
        }
        if (entities == null || entities.length == 0) throw new IllegalArgumentException("SQLite entities must not be empty");
        Map<String, Table> tables = new TreeMap<>();
        for (Class<?> entity : entities) {
            Table table = describe(Objects.requireNonNull(entity, "entity"));
            String key = table.name().toLowerCase(Locale.ROOT);
            Table previous = tables.putIfAbsent(key, table);
            if (previous != null && !previous.equals(table)) {
                throw new IllegalArgumentException("Conflicting SQLite mappings for table " + table.name() + ": " + entity.getName());
            }
        }
        int created = 0;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Table table : tables.values()) {
                    boolean exists = exists(connection, table.name());
                    if (!exists) {
                        StringJoiner columns = new StringJoiner(", ");
                        for (Column column : table.columns()) {
                            columns.add(quote(column.name()) + " " + column.type()
                                    + (column.primary() ? " NOT NULL PRIMARY KEY" : ""));
                        }
                        try (var statement = connection.createStatement()) {
                            statement.executeUpdate("CREATE TABLE " + quote(table.name()) + " (" + columns + ")");
                        }
                        created++;
                    }
                    validate(connection, table);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite schema creation/validation failed: " + e.getMessage(), e);
        }
        var result = new SchemaResult(entities.length, created, tables.size() - created);
        log.info("SQLite schema: entities={}, created={}, existing={}", result.entities(), result.created(), result.existing());
        return result;
    }

    private static boolean isSimpleTable(Class<?> type, JoinTable table) {
        return table != null && !table.value().isBlank() && table.left().isEmpty() && table.right().isEmpty()
                && table.on().isEmpty() && AnnotationUtil.findAnnotation(type, JoinTableList.class) == null
                && !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    private Table describe(Class<?> entity) {
        JoinTable table = AnnotationUtil.findAnnotation(entity, JoinTable.class);
        if (!isSimpleTable(entity, table)) throw new IllegalArgumentException("Not a concrete SQLite table entity: " + entity.getName());
        identifier(table.value());
        var aliases = AliasContent.newInstance(entity, underscore);
        Map<String, Column> columns = new TreeMap<>();
        for (var field : ReflectionUtil.getAllDeclaredFields(entity)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) continue;
            JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (mapping != null && (!mapping.exist() || !mapping.function().isEmpty())) continue;
            String name = aliases.toColumn(field.getName());
            // Qualified names are fields of a joined/query-only model, not physical local columns.
            if (name.contains(".")) throw new IllegalArgumentException("Qualified SQLite column in " + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            var column = new Column(name, storageType(field.getType()), meta != null && meta.primaryKey());
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("Duplicate SQLite column " + table.value() + "." + name);
            }
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("No persistent columns: " + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException("Composite primary keys are not supported by ORM model operations: " + entity.getName());
        }
        return new Table(table.value(), List.copyOf(columns.values()));
    }

    private static String storageType(Class<?> type) {
        // ByteArrayCoder also emits Base64 text, so byte[] follows the TEXT fallback.
        if (type == boolean.class || type == Boolean.class || type == byte.class || type == Byte.class
                || type == short.class || type == Short.class || type == int.class || type == Integer.class
                || type == long.class || type == Long.class) return "INTEGER";
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) return "REAL";
        // Dates, enums, BigDecimal/BigInteger, strings and JSON values retain their existing codecs.
        return "TEXT";
    }

    private static boolean exists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT type FROM sqlite_schema WHERE name = ? COLLATE NOCASE")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                if (!"table".equals(rows.getString(1))) throw new IllegalStateException("SQLite object is not a table: " + name);
                return true;
            }
        }
    }

    private static void validate(Connection connection, Table table) throws SQLException {
        Map<String, Column> actual = new HashMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA table_xinfo(" + quote(table.name()) + ")")) {
            while (rows.next()) {
                String name = rows.getString("name");
                if (rows.getInt("hidden") != 0) {
                    if (table.columns().stream().anyMatch(c -> c.name().equalsIgnoreCase(name))) {
                        throw new IllegalStateException("SQLite mapped column is generated/hidden: " + table.name() + "." + name);
                    }
                    continue;
                }
                actual.put(name.toLowerCase(Locale.ROOT), new Column(name, affinity(rows.getString("type")), rows.getInt("pk") != 0));
            }
        }
        for (Column expected : table.columns()) {
            Column column = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (column == null || !column.type().equals(expected.type()) || column.primary() != expected.primary()) {
                throw new IllegalStateException("Incompatible SQLite column " + table.name() + "." + expected.name()
                        + ": expected " + expected + ", actual " + column + "; migrate manually");
            }
        }
        if (actual.values().stream().filter(Column::primary).count() != table.columns().stream().filter(Column::primary).count()) {
            throw new IllegalStateException("Incompatible SQLite primary key: " + table.name());
        }
    }

    private static String affinity(String declared) {
        String type = declared.toUpperCase(Locale.ROOT);
        if (type.contains("INT")) return "INTEGER";
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) return "TEXT";
        if (type.isEmpty() || type.contains("BLOB")) return "BLOB";
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) return "REAL";
        return "NUMERIC";
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*") || name.toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
            throw new IllegalArgumentException("SQLite schema needs a plain table/column name: " + name);
        }
    }
    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}

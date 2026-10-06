package com.yulinlin.jdbc.postgresql;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("yulinlin.postgresql")
public class PostgresqlProperties {
    public enum SchemaMode { CREATE, VALIDATE, NONE }

    private SchemaMode schemaMode = SchemaMode.NONE;
    private List<String> schemaPackages = new ArrayList<>();

    public SchemaMode getSchemaMode() { return schemaMode; }
    public void setSchemaMode(SchemaMode schemaMode) { this.schemaMode = schemaMode; }
    public List<String> getSchemaPackages() { return schemaPackages; }
    public void setSchemaPackages(List<String> schemaPackages) {
        this.schemaPackages = schemaPackages == null ? new ArrayList<>() : new ArrayList<>(schemaPackages);
    }
}

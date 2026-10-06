package com.yulinlin.data.core.session;

import com.yulinlin.data.core.schema.SchemaMode;

import java.util.ArrayList;
import java.util.List;

/** Settings shared by relational and non-relational entity sessions. */
public class EntitySessionProperties {
    private boolean log;
    private boolean mapUnderscoreToCamelCase = true;
    private SchemaMode schemaMode = SchemaMode.NONE;
    private List<String> schemaPackages = new ArrayList<>();
    private HighlightProperties highlight = new HighlightProperties();

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public boolean isMapUnderscoreToCamelCase() {
        return mapUnderscoreToCamelCase;
    }

    public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
        this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
    }

    public SchemaMode getSchemaMode() {
        return schemaMode;
    }

    public void setSchemaMode(SchemaMode schemaMode) {
        if (schemaMode == null) throw new IllegalArgumentException("schemaMode must not be null");
        this.schemaMode = schemaMode;
    }

    public List<String> getSchemaPackages() {
        return schemaPackages;
    }

    public void setSchemaPackages(List<String> schemaPackages) {
        this.schemaPackages = schemaPackages == null ? new ArrayList<>() : new ArrayList<>(schemaPackages);
    }

    public HighlightProperties getHighlight() {
        return highlight;
    }

    public void setHighlight(HighlightProperties highlight) {
        this.highlight = highlight == null ? new HighlightProperties() : highlight;
    }
}

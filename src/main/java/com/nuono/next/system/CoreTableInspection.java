package com.nuono.next.system;

import java.util.List;

public class CoreTableInspection {

    private final String schema;
    private final List<String> expectedTables;
    private final List<String> existingTables;
    private final List<String> missingTables;
    private final List<String> missingRequiredColumns;

    public CoreTableInspection(
            String schema,
            List<String> expectedTables,
            List<String> existingTables,
            List<String> missingTables
    ) {
        this(schema, expectedTables, existingTables, missingTables, List.of());
    }

    public CoreTableInspection(
            String schema,
            List<String> expectedTables,
            List<String> existingTables,
            List<String> missingTables,
            List<String> missingRequiredColumns
    ) {
        this.schema = schema;
        this.expectedTables = expectedTables;
        this.existingTables = existingTables;
        this.missingTables = missingTables;
        this.missingRequiredColumns = missingRequiredColumns == null ? List.of() : missingRequiredColumns;
    }

    public String getSchema() {
        return schema;
    }

    public List<String> getExpectedTables() {
        return expectedTables;
    }

    public List<String> getExistingTables() {
        return existingTables;
    }

    public List<String> getMissingTables() {
        return missingTables;
    }

    public List<String> getMissingRequiredColumns() {
        return missingRequiredColumns;
    }

    public boolean isReady() {
        return missingTables.isEmpty() && missingRequiredColumns.isEmpty();
    }
}

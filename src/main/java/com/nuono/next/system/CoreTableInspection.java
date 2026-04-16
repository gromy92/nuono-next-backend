package com.nuono.next.system;

import java.util.List;

public class CoreTableInspection {

    private final String schema;
    private final List<String> expectedTables;
    private final List<String> existingTables;
    private final List<String> missingTables;

    public CoreTableInspection(
            String schema,
            List<String> expectedTables,
            List<String> existingTables,
            List<String> missingTables
    ) {
        this.schema = schema;
        this.expectedTables = expectedTables;
        this.existingTables = existingTables;
        this.missingTables = missingTables;
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

    public boolean isReady() {
        return missingTables.isEmpty();
    }
}

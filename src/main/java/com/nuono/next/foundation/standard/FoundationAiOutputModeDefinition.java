package com.nuono.next.foundation.standard;

import java.util.List;

public class FoundationAiOutputModeDefinition {

    private final String key;
    private final String name;
    private final String description;
    private final List<String> lifecycleSteps;
    private final List<String> requiredContracts;
    private final boolean externalWriteAllowed;

    public FoundationAiOutputModeDefinition(
            String key,
            String name,
            String description,
            List<String> lifecycleSteps,
            List<String> requiredContracts,
            boolean externalWriteAllowed
    ) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.lifecycleSteps = List.copyOf(lifecycleSteps);
        this.requiredContracts = List.copyOf(requiredContracts);
        this.externalWriteAllowed = externalWriteAllowed;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getLifecycleSteps() {
        return lifecycleSteps;
    }

    public List<String> getRequiredContracts() {
        return requiredContracts;
    }

    public boolean isExternalWriteAllowed() {
        return externalWriteAllowed;
    }
}

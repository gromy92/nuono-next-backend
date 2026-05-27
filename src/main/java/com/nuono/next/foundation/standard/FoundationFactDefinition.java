package com.nuono.next.foundation.standard;

import java.util.List;

public class FoundationFactDefinition {

    private final String key;
    private final String name;
    private final String description;
    private final String referenceModel;
    private final List<String> identityFields;
    private final List<String> sourceFields;
    private final List<String> valueSemantics;
    private final List<String> requiredContractFields;
    private final List<String> valueRules;

    public FoundationFactDefinition(
            String key,
            String name,
            String description,
            String referenceModel,
            List<String> identityFields,
            List<String> sourceFields,
            List<String> valueSemantics,
            List<String> requiredContractFields,
            List<String> valueRules
    ) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.referenceModel = referenceModel;
        this.identityFields = List.copyOf(identityFields);
        this.sourceFields = List.copyOf(sourceFields);
        this.valueSemantics = List.copyOf(valueSemantics);
        this.requiredContractFields = List.copyOf(requiredContractFields);
        this.valueRules = List.copyOf(valueRules);
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

    public String getReferenceModel() {
        return referenceModel;
    }

    public List<String> getIdentityFields() {
        return identityFields;
    }

    public List<String> getSourceFields() {
        return sourceFields;
    }

    public List<String> getValueSemantics() {
        return valueSemantics;
    }

    public List<String> getRequiredContractFields() {
        return requiredContractFields;
    }

    public List<String> getValueRules() {
        return valueRules;
    }
}

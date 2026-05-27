package com.nuono.next.foundation.standard;

import java.util.List;

public class FoundationMetricDefinition {

    private final String key;
    private final String name;
    private final String description;
    private final List<String> inputFactKeys;
    private final String calculationOwner;
    private final String freshnessRule;
    private final String qualityRule;
    private final String resultShape;
    private final boolean authoritative;

    public FoundationMetricDefinition(
            String key,
            String name,
            String description,
            List<String> inputFactKeys,
            String calculationOwner,
            String freshnessRule,
            String qualityRule,
            String resultShape,
            boolean authoritative
    ) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.inputFactKeys = List.copyOf(inputFactKeys);
        this.calculationOwner = calculationOwner;
        this.freshnessRule = freshnessRule;
        this.qualityRule = qualityRule;
        this.resultShape = resultShape;
        this.authoritative = authoritative;
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

    public List<String> getInputFactKeys() {
        return inputFactKeys;
    }

    public String getCalculationOwner() {
        return calculationOwner;
    }

    public String getFreshnessRule() {
        return freshnessRule;
    }

    public String getQualityRule() {
        return qualityRule;
    }

    public String getResultShape() {
        return resultShape;
    }

    public boolean isAuthoritative() {
        return authoritative;
    }
}

package com.nuono.next.foundation.standard;

import java.util.List;

public class FoundationMetricPreview {

    private final String metricKey;
    private final Integer integerValue;
    private final List<String> qualityStates;

    public FoundationMetricPreview(String metricKey, Integer integerValue, List<String> qualityStates) {
        this.metricKey = metricKey;
        this.integerValue = integerValue;
        this.qualityStates = List.copyOf(qualityStates);
    }

    public String getMetricKey() {
        return metricKey;
    }

    public Integer getIntegerValue() {
        return integerValue;
    }

    public List<String> getQualityStates() {
        return qualityStates;
    }
}

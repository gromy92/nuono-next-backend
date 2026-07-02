package com.nuono.next.noonads;

public enum NoonAdvertisingPlanType {
    EXPLORATION("探索计划"),
    CORE("核心计划"),
    UNCLASSIFIED("未分类");

    private final String label;

    NoonAdvertisingPlanType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

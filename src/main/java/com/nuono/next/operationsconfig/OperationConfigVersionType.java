package com.nuono.next.operationsconfig;

public enum OperationConfigVersionType {
    BUSINESS_CALENDAR("日历版本"),
    PRODUCT_LIFECYCLE("生命周期版本"),
    REPLENISHMENT_PLAN("补货计划参数");

    private final String label;

    OperationConfigVersionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

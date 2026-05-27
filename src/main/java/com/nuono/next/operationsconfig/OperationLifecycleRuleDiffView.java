package com.nuono.next.operationsconfig;

public class OperationLifecycleRuleDiffView {

    private final String field;
    private final String label;
    private final String beforeValue;
    private final String afterValue;

    public OperationLifecycleRuleDiffView(
            String field,
            String label,
            String beforeValue,
            String afterValue
    ) {
        this.field = field;
        this.label = label;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public String getField() { return field; }
    public String getLabel() { return label; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
}

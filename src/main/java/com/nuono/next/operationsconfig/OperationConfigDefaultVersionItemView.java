package com.nuono.next.operationsconfig;

public class OperationConfigDefaultVersionItemView {

    private final String groupName;
    private final String itemName;
    private final String cadence;
    private final String valueType;
    private final String defaultValue;
    private final String resultShape;
    private final String note;

    public OperationConfigDefaultVersionItemView(
            String groupName,
            String itemName,
            String cadence,
            String valueType,
            String defaultValue,
            String resultShape,
            String note
    ) {
        this.groupName = groupName;
        this.itemName = itemName;
        this.cadence = cadence;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.resultShape = resultShape;
        this.note = note;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getCadence() {
        return cadence;
    }

    public String getValueType() {
        return valueType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getResultShape() {
        return resultShape;
    }

    public String getNote() {
        return note;
    }
}

package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationConfigVersionUpdateRequest {
    private String configType;
    private String displayName;
    private String summary;
    private List<Item> items = List.of();

    public OperationConfigVersionUpdateRequest() {
    }

    public OperationConfigVersionUpdateRequest(String configType, List<Item> items) {
        this.configType = configType;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public OperationConfigVersionUpdateRequest(String configType, String displayName, String summary, List<Item> items) {
        this.configType = configType;
        this.displayName = displayName;
        this.summary = summary;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public List<OperationConfigDefaultVersionItemView> toItemViews() {
        return items.stream().map(Item::toView).collect(java.util.stream.Collectors.toList());
    }

    public static class Item {
        private String groupName;
        private String itemName;
        private String cadence;
        private String valueType;
        private String defaultValue;
        private String resultShape;
        private String note;

        public Item() {
        }

        public Item(
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

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getCadence() {
            return cadence;
        }

        public void setCadence(String cadence) {
            this.cadence = cadence;
        }

        public String getValueType() {
            return valueType;
        }

        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getResultShape() {
            return resultShape;
        }

        public void setResultShape(String resultShape) {
            this.resultShape = resultShape;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        private OperationConfigDefaultVersionItemView toView() {
            return new OperationConfigDefaultVersionItemView(
                    groupName,
                    itemName,
                    cadence,
                    valueType,
                    defaultValue,
                    resultShape,
                    note
            );
        }
    }
}

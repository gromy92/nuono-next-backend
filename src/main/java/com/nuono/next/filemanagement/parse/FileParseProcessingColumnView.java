package com.nuono.next.filemanagement.parse;

public class FileParseProcessingColumnView {

    private String key;
    private String label;
    private String type;
    private boolean tableVisible = true;
    private Integer width;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isTableVisible() {
        return tableVisible;
    }

    public void setTableVisible(boolean tableVisible) {
        this.tableVisible = tableVisible;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }
}

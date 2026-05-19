package com.nuono.next.filemanagement.parse;

public class FileParseWorkflowStepView {

    private String key;
    private String label;
    private String status;
    private Integer count;

    public FileParseWorkflowStepView() {
    }

    public FileParseWorkflowStepView(String key, String label, String status, Integer count) {
        this.key = key;
        this.label = label;
        this.status = status;
        this.count = count;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}

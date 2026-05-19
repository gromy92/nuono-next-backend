package com.nuono.next.filemanagement.parse;

import java.util.List;

public class FileParseCreateTaskCommand {

    private String documentTitle;
    private Long targetPlanId;
    private Long parentTaskId;
    private List<FileParseTaskInputCommand> inputItems;
    private String remark;

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public Long getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public List<FileParseTaskInputCommand> getInputItems() {
        return inputItems;
    }

    public void setInputItems(List<FileParseTaskInputCommand> inputItems) {
        this.inputItems = inputItems;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

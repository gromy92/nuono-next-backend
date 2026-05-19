package com.nuono.next.filemanagement.parse;

public class FileParsePublishCommand {

    private Long expectedResultId;
    private String confirmMessage;
    private String remark;

    public Long getExpectedResultId() {
        return expectedResultId;
    }

    public void setExpectedResultId(Long expectedResultId) {
        this.expectedResultId = expectedResultId;
    }

    public String getConfirmMessage() {
        return confirmMessage;
    }

    public void setConfirmMessage(String confirmMessage) {
        this.confirmMessage = confirmMessage;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

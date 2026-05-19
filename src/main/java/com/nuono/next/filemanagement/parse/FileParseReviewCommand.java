package com.nuono.next.filemanagement.parse;

import java.util.LinkedHashMap;
import java.util.Map;

public class FileParseReviewCommand {

    private Long expectedResultId;
    private Map<String, Object> fields = new LinkedHashMap<>();
    private String remark;
    private String reason;

    public Long getExpectedResultId() {
        return expectedResultId;
    }

    public void setExpectedResultId(Long expectedResultId) {
        this.expectedResultId = expectedResultId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : fields;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

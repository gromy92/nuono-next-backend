package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseBatchReviewCommand {

    private List<Long> itemIds = new ArrayList<>();
    private Long expectedResultId;
    private String remark;

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds == null ? new ArrayList<>() : itemIds;
    }

    public Long getExpectedResultId() {
        return expectedResultId;
    }

    public void setExpectedResultId(Long expectedResultId) {
        this.expectedResultId = expectedResultId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

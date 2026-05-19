package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseBatchReviewView {

    private Long taskId;
    private Long resultId;
    private Integer totalCount = 0;
    private Integer successCount = 0;
    private List<FileParseProcessingItemView> items = new ArrayList<>();

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount == null ? 0 : totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount == null ? 0 : successCount;
    }

    public List<FileParseProcessingItemView> getItems() {
        return items;
    }

    public void setItems(List<FileParseProcessingItemView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}

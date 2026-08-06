package com.nuono.next.datapull.snapshot;

import java.util.Objects;

/** Persisted page envelope; empty pages are represented explicitly. */
public final class SnapshotStagePageRow {
    private Long taskId;
    private Integer pageNo;
    private Integer nextPage;
    private Boolean lastPage;
    private Integer totalPages;
    private Integer itemCount;
    private Integer sourceItemCount;
    private Integer businessSkippedItemCount;

    public static SnapshotStagePageRow from(
            long taskId,
            SnapshotStagePageCandidate<?> candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        SnapshotStagePageRow row = new SnapshotStagePageRow();
        row.taskId = taskId;
        row.pageNo = candidate.getPageNo();
        row.nextPage = candidate.getNextPage();
        row.lastPage = candidate.getLastPage();
        row.totalPages = candidate.getTotalPages();
        row.itemCount = candidate.getItems().size();
        row.sourceItemCount = candidate.getSourceItemCount();
        row.businessSkippedItemCount = candidate.getBusinessSkippedItemCount();
        return row;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getNextPage() {
        return nextPage;
    }

    public void setNextPage(Integer nextPage) {
        this.nextPage = nextPage;
    }

    public Boolean getLastPage() {
        return lastPage;
    }

    public void setLastPage(Boolean lastPage) {
        this.lastPage = lastPage;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Integer getSourceItemCount() { return sourceItemCount; }
    public void setSourceItemCount(Integer value) { sourceItemCount = value; }
    public Integer getBusinessSkippedItemCount() { return businessSkippedItemCount; }
    public void setBusinessSkippedItemCount(Integer value) {
        businessSkippedItemCount = value;
    }
}

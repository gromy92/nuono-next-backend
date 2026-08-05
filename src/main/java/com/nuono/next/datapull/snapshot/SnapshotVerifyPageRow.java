package com.nuono.next.datapull.snapshot;

import java.util.Objects;

/** Durable pass-two page replay fence; it is not provider collection authority. */
public final class SnapshotVerifyPageRow {
    private Long taskId;
    private Integer pageNo;
    private Integer nextPage;
    private Boolean lastPage;
    private Integer totalPages;
    private Integer sourceItemCount;
    private Integer businessSkippedItemCount;
    private String pageDigestSha256;

    static SnapshotVerifyPageRow from(
            long taskId,
            SnapshotStagePageCandidate<?> page,
            String digest
    ) {
        SnapshotVerifyPageRow row = new SnapshotVerifyPageRow();
        row.taskId = taskId;
        row.pageNo = page.getPageNo();
        row.nextPage = page.getNextPage();
        row.lastPage = page.getLastPage();
        row.totalPages = page.getTotalPages();
        row.sourceItemCount = page.getSourceItemCount();
        row.businessSkippedItemCount = page.getBusinessSkippedItemCount();
        row.pageDigestSha256 = digest;
        return row;
    }

    boolean sameObservation(SnapshotVerifyPageRow other) {
        return other != null
                && Objects.equals(pageNo, other.pageNo)
                && Objects.equals(nextPage, other.nextPage)
                && Objects.equals(lastPage, other.lastPage)
                && Objects.equals(totalPages, other.totalPages)
                && Objects.equals(sourceItemCount, other.sourceItemCount)
                && Objects.equals(businessSkippedItemCount, other.businessSkippedItemCount)
                && Objects.equals(pageDigestSha256, other.pageDigestSha256);
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer value) { pageNo = value; }
    public Integer getNextPage() { return nextPage; }
    public void setNextPage(Integer value) { nextPage = value; }
    public Boolean getLastPage() { return lastPage; }
    public void setLastPage(Boolean value) { lastPage = value; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer value) { totalPages = value; }
    public Integer getSourceItemCount() { return sourceItemCount; }
    public void setSourceItemCount(Integer value) { sourceItemCount = value; }
    public Integer getBusinessSkippedItemCount() { return businessSkippedItemCount; }
    public void setBusinessSkippedItemCount(Integer value) { businessSkippedItemCount = value; }
    public String getPageDigestSha256() { return pageDigestSha256; }
    public void setPageDigestSha256(String value) { pageDigestSha256 = value; }
}

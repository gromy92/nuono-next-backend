package com.nuono.next.datapull.snapshot;

/** One ordered staged item and its explicit domain payload. */
public final class SnapshotStageItemRow {
    private Long taskId;
    private Integer pageNo;
    private Integer itemOrdinal;
    private String stableIdentity;
    private String contentFingerprint;
    private String payload;
    private Boolean validatedIdentityCandidate;
    private Boolean absenceReconciliationSafe;

    public static SnapshotStageItemRow from(
            long taskId,
            int pageNo,
            int itemOrdinal,
            SnapshotStagePageCandidate.EncodedItem<?> item
    ) {
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.taskId = taskId;
        row.pageNo = pageNo;
        row.itemOrdinal = itemOrdinal;
        row.stableIdentity = item.getStableIdentity();
        row.contentFingerprint = item.getContentFingerprint();
        row.payload = item.getPayload();
        row.validatedIdentityCandidate = item.isValidatedIdentityCandidate();
        row.absenceReconciliationSafe = item.isAbsenceReconciliationSafe();
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

    public Integer getItemOrdinal() {
        return itemOrdinal;
    }

    public void setItemOrdinal(Integer itemOrdinal) {
        this.itemOrdinal = itemOrdinal;
    }

    public String getStableIdentity() {
        return stableIdentity;
    }

    public void setStableIdentity(String stableIdentity) {
        this.stableIdentity = stableIdentity;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Boolean getValidatedIdentityCandidate() {
        return validatedIdentityCandidate;
    }

    public void setValidatedIdentityCandidate(Boolean value) {
        this.validatedIdentityCandidate = value;
    }

    public Boolean getAbsenceReconciliationSafe() {
        return absenceReconciliationSafe;
    }

    public void setAbsenceReconciliationSafe(Boolean value) {
        this.absenceReconciliationSafe = value;
    }
}

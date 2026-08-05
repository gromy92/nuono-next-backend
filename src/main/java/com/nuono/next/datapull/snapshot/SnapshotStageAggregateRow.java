package com.nuono.next.datapull.snapshot;

import java.time.LocalDateTime;

/** Per-task durable complete-snapshot staging header. */
public final class SnapshotStageAggregateRow {
    private Long taskId;
    private Long activeFenceEpoch;
    private Integer declaredTotalPages;
    private Integer knownLastPage;
    private String poisonCode;
    private String authorityKind;
    private String authorityTokenSha256;
    private LocalDateTime snapshotAsOfUtc;
    private Long declaredCollectionCount;
    private String collectionMode;
    private String verificationState;
    private Integer passOnePageCount;
    private Long passOneSourceItemCount;
    private Integer verificationNextPage;
    private Integer verificationPageCount;
    private Long verificationSourceItemCount;
    private String comparisonAfterFingerprint;
    private String comparisonDigestSha256;
    private Long comparisonKeyCount;
    private Long comparisonSourceItemCount;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getActiveFenceEpoch() {
        return activeFenceEpoch;
    }

    public void setActiveFenceEpoch(Long activeFenceEpoch) {
        this.activeFenceEpoch = activeFenceEpoch;
    }

    public Integer getDeclaredTotalPages() {
        return declaredTotalPages;
    }

    public void setDeclaredTotalPages(Integer declaredTotalPages) {
        this.declaredTotalPages = declaredTotalPages;
    }

    public Integer getKnownLastPage() {
        return knownLastPage;
    }

    public void setKnownLastPage(Integer knownLastPage) {
        this.knownLastPage = knownLastPage;
    }

    public String getPoisonCode() {
        return poisonCode;
    }

    public void setPoisonCode(String poisonCode) {
        this.poisonCode = poisonCode;
    }

    public String getAuthorityKind() { return authorityKind; }
    public void setAuthorityKind(String value) { authorityKind = value; }
    public String getAuthorityTokenSha256() { return authorityTokenSha256; }
    public void setAuthorityTokenSha256(String value) { authorityTokenSha256 = value; }
    public LocalDateTime getSnapshotAsOfUtc() { return snapshotAsOfUtc; }
    public void setSnapshotAsOfUtc(LocalDateTime value) { snapshotAsOfUtc = value; }
    public Long getDeclaredCollectionCount() { return declaredCollectionCount; }
    public void setDeclaredCollectionCount(Long value) { declaredCollectionCount = value; }
    public String getCollectionMode() { return collectionMode; }
    public void setCollectionMode(String value) { collectionMode = value; }
    public String getVerificationState() { return verificationState; }
    public void setVerificationState(String value) { verificationState = value; }
    public Integer getPassOnePageCount() { return passOnePageCount; }
    public void setPassOnePageCount(Integer value) { passOnePageCount = value; }
    public Long getPassOneSourceItemCount() { return passOneSourceItemCount; }
    public void setPassOneSourceItemCount(Long value) { passOneSourceItemCount = value; }
    public Integer getVerificationNextPage() { return verificationNextPage; }
    public void setVerificationNextPage(Integer value) { verificationNextPage = value; }
    public Integer getVerificationPageCount() { return verificationPageCount; }
    public void setVerificationPageCount(Integer value) { verificationPageCount = value; }
    public Long getVerificationSourceItemCount() { return verificationSourceItemCount; }
    public void setVerificationSourceItemCount(Long value) { verificationSourceItemCount = value; }
    public String getComparisonAfterFingerprint() { return comparisonAfterFingerprint; }
    public void setComparisonAfterFingerprint(String value) { comparisonAfterFingerprint = value; }
    public String getComparisonDigestSha256() { return comparisonDigestSha256; }
    public void setComparisonDigestSha256(String value) { comparisonDigestSha256 = value; }
    public Long getComparisonKeyCount() { return comparisonKeyCount; }
    public void setComparisonKeyCount(Long value) { comparisonKeyCount = value; }
    public Long getComparisonSourceItemCount() { return comparisonSourceItemCount; }
    public void setComparisonSourceItemCount(Long value) { comparisonSourceItemCount = value; }
}

package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonPullSmokeEvidenceView {
    private String dataDomain;
    private String targetIdentity;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer rowOrItemCount;
    private Long taskId;
    private String sourceBatchId;
    private String fileDigestSha256;
    private Integer requestCount;
    private long elapsedMillis;
    private LocalDate latestFactDate;
    private String status;
    private String qualityState;
    private String failureClassification;

    public String getDataDomain() {
        return dataDomain;
    }

    public void setDataDomain(String dataDomain) {
        this.dataDomain = dataDomain;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public void setTargetIdentity(String targetIdentity) {
        this.targetIdentity = targetIdentity;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public Integer getRowOrItemCount() {
        return rowOrItemCount;
    }

    public void setRowOrItemCount(Integer rowOrItemCount) {
        this.rowOrItemCount = rowOrItemCount;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getFileDigestSha256() {
        return fileDigestSha256;
    }

    public void setFileDigestSha256(String fileDigestSha256) {
        this.fileDigestSha256 = fileDigestSha256;
    }

    public Integer getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Integer requestCount) {
        this.requestCount = requestCount;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public void setLatestFactDate(LocalDate latestFactDate) {
        this.latestFactDate = latestFactDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQualityState() {
        return qualityState;
    }

    public void setQualityState(String qualityState) {
        this.qualityState = qualityState;
    }

    public String getFailureClassification() {
        return failureClassification;
    }

    public void setFailureClassification(String failureClassification) {
        this.failureClassification = failureClassification;
    }
}

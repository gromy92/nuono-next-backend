package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoonPullSmokeEvidenceRecord {
    private Long id;
    private Long runId;
    private Integer sequenceNo;
    private String dataDomain;
    private String targetIdentity;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer rowOrItemCount;
    private Long taskId;
    private String sourceBatchId;
    private String fileDigestSha256;
    private Integer requestCount;
    private Long elapsedMillis;
    private LocalDate latestFactDate;
    private String status;
    private String qualityState;
    private String failureClassification;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonPullSmokeEvidenceRecord copy() {
        NoonPullSmokeEvidenceRecord copy = new NoonPullSmokeEvidenceRecord();
        copy.id = id;
        copy.runId = runId;
        copy.sequenceNo = sequenceNo;
        copy.dataDomain = dataDomain;
        copy.targetIdentity = targetIdentity;
        copy.dateFrom = dateFrom;
        copy.dateTo = dateTo;
        copy.rowOrItemCount = rowOrItemCount;
        copy.taskId = taskId;
        copy.sourceBatchId = sourceBatchId;
        copy.fileDigestSha256 = fileDigestSha256;
        copy.requestCount = requestCount;
        copy.elapsedMillis = elapsedMillis;
        copy.latestFactDate = latestFactDate;
        copy.status = status;
        copy.qualityState = qualityState;
        copy.failureClassification = failureClassification;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

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

    public Long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(Long elapsedMillis) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.nuono.next.filemanagement.parse;

import java.time.LocalDateTime;

public class FileParsePublishView {

    private Long versionId;
    private String versionNo;
    private String status;
    private LocalDateTime publishedAt;
    private Integer logisticsFactInsertedCount;
    private Integer logisticsFactUnchangedCount;
    private Integer logisticsFactSupersededCount;
    private Integer logisticsFactSkippedCount;
    private Integer logisticsFactConflictCount;
    private Integer outboundFeeFactInsertedCount;
    private Integer outboundFeeFactUnchangedCount;
    private Integer outboundFeeFactSupersededCount;
    private Integer outboundFeeFactSkippedCount;
    private Integer outboundFeeFactConflictCount;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Integer getLogisticsFactInsertedCount() {
        return logisticsFactInsertedCount;
    }

    public void setLogisticsFactInsertedCount(Integer logisticsFactInsertedCount) {
        this.logisticsFactInsertedCount = logisticsFactInsertedCount;
    }

    public Integer getLogisticsFactUnchangedCount() {
        return logisticsFactUnchangedCount;
    }

    public void setLogisticsFactUnchangedCount(Integer logisticsFactUnchangedCount) {
        this.logisticsFactUnchangedCount = logisticsFactUnchangedCount;
    }

    public Integer getLogisticsFactSupersededCount() {
        return logisticsFactSupersededCount;
    }

    public void setLogisticsFactSupersededCount(Integer logisticsFactSupersededCount) {
        this.logisticsFactSupersededCount = logisticsFactSupersededCount;
    }

    public Integer getLogisticsFactSkippedCount() {
        return logisticsFactSkippedCount;
    }

    public void setLogisticsFactSkippedCount(Integer logisticsFactSkippedCount) {
        this.logisticsFactSkippedCount = logisticsFactSkippedCount;
    }

    public Integer getLogisticsFactConflictCount() {
        return logisticsFactConflictCount;
    }

    public void setLogisticsFactConflictCount(Integer logisticsFactConflictCount) {
        this.logisticsFactConflictCount = logisticsFactConflictCount;
    }

    public Integer getOutboundFeeFactInsertedCount() {
        return outboundFeeFactInsertedCount;
    }

    public void setOutboundFeeFactInsertedCount(Integer outboundFeeFactInsertedCount) {
        this.outboundFeeFactInsertedCount = outboundFeeFactInsertedCount;
    }

    public Integer getOutboundFeeFactUnchangedCount() {
        return outboundFeeFactUnchangedCount;
    }

    public void setOutboundFeeFactUnchangedCount(Integer outboundFeeFactUnchangedCount) {
        this.outboundFeeFactUnchangedCount = outboundFeeFactUnchangedCount;
    }

    public Integer getOutboundFeeFactSupersededCount() {
        return outboundFeeFactSupersededCount;
    }

    public void setOutboundFeeFactSupersededCount(Integer outboundFeeFactSupersededCount) {
        this.outboundFeeFactSupersededCount = outboundFeeFactSupersededCount;
    }

    public Integer getOutboundFeeFactSkippedCount() {
        return outboundFeeFactSkippedCount;
    }

    public void setOutboundFeeFactSkippedCount(Integer outboundFeeFactSkippedCount) {
        this.outboundFeeFactSkippedCount = outboundFeeFactSkippedCount;
    }

    public Integer getOutboundFeeFactConflictCount() {
        return outboundFeeFactConflictCount;
    }

    public void setOutboundFeeFactConflictCount(Integer outboundFeeFactConflictCount) {
        this.outboundFeeFactConflictCount = outboundFeeFactConflictCount;
    }
}

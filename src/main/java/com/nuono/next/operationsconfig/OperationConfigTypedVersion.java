package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;

public class OperationConfigTypedVersion {
    private final Long id;
    private final String versionNo;
    private final String displayName;
    private final String configType;
    private final String status;
    private final String sourceVersionNo;
    private final String sourceLabel;
    private final String summary;
    private final int itemCount;
    private final String scopeSummary;
    private final String contentJson;
    private final String auditJson;
    private final Long createdBy;
    private final Long updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public OperationConfigTypedVersion(
            Long id,
            String versionNo,
            String displayName,
            String configType,
            String status,
            String sourceVersionNo,
            String sourceLabel,
            String summary,
            int itemCount,
            String scopeSummary,
            String contentJson,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                versionNo,
                displayName,
                configType,
                status,
                sourceVersionNo,
                sourceLabel,
                summary,
                itemCount,
                scopeSummary,
                contentJson,
                "[]",
                createdBy,
                updatedBy,
                createdAt,
                updatedAt
        );
    }

    public OperationConfigTypedVersion(
            Long id,
            String versionNo,
            String displayName,
            String configType,
            String status,
            String sourceVersionNo,
            String sourceLabel,
            String summary,
            int itemCount,
            String scopeSummary,
            String contentJson,
            String auditJson,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.versionNo = versionNo;
        this.displayName = displayName;
        this.configType = configType;
        this.status = status;
        this.sourceVersionNo = sourceVersionNo;
        this.sourceLabel = sourceLabel;
        this.summary = summary;
        this.itemCount = itemCount;
        this.scopeSummary = scopeSummary;
        this.contentJson = contentJson;
        this.auditJson = auditJson;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigType() {
        return configType;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceVersionNo() {
        return sourceVersionNo;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getSummary() {
        return summary;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getAuditJson() {
        return auditJson;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

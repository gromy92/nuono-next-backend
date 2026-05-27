package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;

public class OperationConfigBundle {

    private final Long id;
    private final Long publishRecordId;
    private final String versionNo;
    private final String displayName;
    private final String publishSourceRole;
    private final String publishSourceLabel;
    private final String scopeSummary;
    private final Integer affectedStoreCount;
    private final Integer activityRuleCount;
    private final String lifecycleRuleSummary;
    private final Long createdBy;
    private final Long updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public OperationConfigBundle(
            Long id,
            Long publishRecordId,
            String versionNo,
            String displayName,
            String publishSourceRole,
            String publishSourceLabel,
            String scopeSummary,
            Integer affectedStoreCount,
            Integer activityRuleCount,
            String lifecycleRuleSummary,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.publishRecordId = publishRecordId;
        this.versionNo = versionNo;
        this.displayName = displayName;
        this.publishSourceRole = OperationConfigVersionSource.safeRole(publishSourceRole);
        this.publishSourceLabel = OperationConfigVersionSource.safeLabel(publishSourceRole, publishSourceLabel);
        this.scopeSummary = scopeSummary == null || scopeSummary.trim().isEmpty() ? "未设置范围" : scopeSummary;
        this.affectedStoreCount = affectedStoreCount == null ? 0 : affectedStoreCount;
        this.activityRuleCount = activityRuleCount == null ? 0 : activityRuleCount;
        this.lifecycleRuleSummary = lifecycleRuleSummary == null || lifecycleRuleSummary.trim().isEmpty()
                ? "未配置"
                : lifecycleRuleSummary;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPublishRecordId() {
        return publishRecordId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPublishSourceRole() {
        return publishSourceRole;
    }

    public String getPublishSourceLabel() {
        return publishSourceLabel;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public Integer getAffectedStoreCount() {
        return affectedStoreCount;
    }

    public Integer getActivityRuleCount() {
        return activityRuleCount;
    }

    public String getLifecycleRuleSummary() {
        return lifecycleRuleSummary;
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

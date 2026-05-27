package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;
import java.util.List;

public class OperationConfigBundleVersionView {

    private final Long id;
    private final Long publishRecordId;
    private final String versionNo;
    private final String displayName;
    private final String status;
    private final String publishSourceRole;
    private final String publishSourceLabel;
    private final String scopeSummary;
    private final Integer affectedStoreCount;
    private final Integer activityRuleCount;
    private final String lifecycleRuleSummary;
    private final Long publishedBy;
    private final LocalDateTime publishedAt;
    private final Long createdBy;
    private final LocalDateTime createdAt;
    private final List<OperationConfigBundleScopeStore> scopeStores;

    public OperationConfigBundleVersionView(
            Long id,
            Long publishRecordId,
            String versionNo,
            String displayName,
            String status,
            String publishSourceRole,
            String publishSourceLabel,
            String scopeSummary,
            Integer affectedStoreCount,
            Integer activityRuleCount,
            String lifecycleRuleSummary,
            Long publishedBy,
            LocalDateTime publishedAt,
            Long createdBy,
            LocalDateTime createdAt
    ) {
        this(
                id,
                publishRecordId,
                versionNo,
                displayName,
                status,
                publishSourceRole,
                publishSourceLabel,
                scopeSummary,
                affectedStoreCount,
                activityRuleCount,
                lifecycleRuleSummary,
                publishedBy,
                publishedAt,
                createdBy,
                createdAt,
                List.of()
        );
    }

    public OperationConfigBundleVersionView(
            Long id,
            Long publishRecordId,
            String versionNo,
            String displayName,
            String status,
            String publishSourceRole,
            String publishSourceLabel,
            String scopeSummary,
            Integer affectedStoreCount,
            Integer activityRuleCount,
            String lifecycleRuleSummary,
            Long publishedBy,
            LocalDateTime publishedAt,
            Long createdBy,
            LocalDateTime createdAt,
            List<OperationConfigBundleScopeStore> scopeStores
    ) {
        this.id = id;
        this.publishRecordId = publishRecordId;
        this.versionNo = versionNo;
        this.displayName = displayName;
        this.status = status;
        this.publishSourceRole = publishSourceRole;
        this.publishSourceLabel = publishSourceLabel;
        this.scopeSummary = scopeSummary;
        this.affectedStoreCount = affectedStoreCount;
        this.activityRuleCount = activityRuleCount;
        this.lifecycleRuleSummary = lifecycleRuleSummary;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.scopeStores = scopeStores == null ? List.of() : List.copyOf(scopeStores);
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

    public String getStatus() {
        return status;
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

    public Long getPublishedBy() {
        return publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<OperationConfigBundleScopeStore> getScopeStores() {
        return scopeStores;
    }
}

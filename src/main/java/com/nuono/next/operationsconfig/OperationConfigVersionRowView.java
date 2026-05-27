package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;
import java.util.List;

public class OperationConfigVersionRowView {
    private final String versionNo;
    private final String displayName;
    private final String configType;
    private final String configTypeLabel;
    private final String status;
    private final String statusLabel;
    private final String sourceLabel;
    private final String summary;
    private final int itemCount;
    private final String scopeSummary;
    private final Long updatedBy;
    private final LocalDateTime updatedAt;
    private final List<OperationConfigVersionActionView> actions;

    public OperationConfigVersionRowView(
            String versionNo,
            String displayName,
            String configType,
            String configTypeLabel,
            String status,
            String statusLabel,
            String sourceLabel,
            String summary,
            int itemCount,
            String scopeSummary,
            Long updatedBy,
            LocalDateTime updatedAt,
            List<OperationConfigVersionActionView> actions
    ) {
        this.versionNo = versionNo;
        this.displayName = displayName;
        this.configType = configType;
        this.configTypeLabel = configTypeLabel;
        this.status = status;
        this.statusLabel = statusLabel;
        this.sourceLabel = sourceLabel;
        this.summary = summary;
        this.itemCount = itemCount;
        this.scopeSummary = scopeSummary;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
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

    public String getConfigTypeLabel() {
        return configTypeLabel;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
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

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OperationConfigVersionActionView> getActions() {
        return actions;
    }
}

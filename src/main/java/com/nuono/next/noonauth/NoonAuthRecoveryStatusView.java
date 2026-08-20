package com.nuono.next.noonauth;

import java.time.LocalDateTime;

public final class NoonAuthRecoveryStatusView {
    private final boolean enabled;
    private final String status;
    private final Long recoveryId;
    private final Integer generationNo;
    private final Integer sendAttemptCount;
    private final LocalDateTime nextAttemptAt;
    private final String failureCode;
    private final boolean allProjectsEnabled;
    private final boolean sessionAuditEnabled;
    private final boolean startupAuditEnabled;
    private final boolean auditReady;
    private final String projectScopeMode;
    private final String auditStatus;
    private final int totalProjects;
    private final int scopedProjects;
    private final int verifiedProjects;
    private final int excludedProjects;
    private final int unverifiedProjects;

    NoonAuthRecoveryStatusView(
            boolean enabled,
            String status,
            Long recoveryId,
            Integer generationNo,
            Integer sendAttemptCount,
            LocalDateTime nextAttemptAt,
            String failureCode,
            boolean allProjectsEnabled,
            boolean sessionAuditEnabled,
            boolean startupAuditEnabled,
            boolean auditReady,
            String projectScopeMode,
            String auditStatus,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects,
            int excludedProjects,
            int unverifiedProjects
    ) {
        this.enabled = enabled;
        this.status = status;
        this.recoveryId = recoveryId;
        this.generationNo = generationNo;
        this.sendAttemptCount = sendAttemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.failureCode = failureCode;
        this.allProjectsEnabled = allProjectsEnabled;
        this.sessionAuditEnabled = sessionAuditEnabled;
        this.startupAuditEnabled = startupAuditEnabled;
        this.auditReady = auditReady;
        this.projectScopeMode = projectScopeMode;
        this.auditStatus = auditStatus;
        this.totalProjects = totalProjects;
        this.scopedProjects = scopedProjects;
        this.verifiedProjects = verifiedProjects;
        this.excludedProjects = excludedProjects;
        this.unverifiedProjects = unverifiedProjects;
    }

    public boolean isEnabled() { return enabled; }
    public String getStatus() { return status; }
    public Long getRecoveryId() { return recoveryId; }
    public Integer getGenerationNo() { return generationNo; }
    public Integer getSendAttemptCount() { return sendAttemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getFailureCode() { return failureCode; }
    public boolean isAllProjectsEnabled() { return allProjectsEnabled; }
    public boolean isSessionAuditEnabled() { return sessionAuditEnabled; }
    public boolean isStartupAuditEnabled() { return startupAuditEnabled; }
    public boolean isAuditReady() { return auditReady; }
    public String getProjectScopeMode() { return projectScopeMode; }
    public String getAuditStatus() { return auditStatus; }
    public int getTotalProjects() { return totalProjects; }
    public int getScopedProjects() { return scopedProjects; }
    public int getVerifiedProjects() { return verifiedProjects; }
    public int getExcludedProjects() { return excludedProjects; }
    public int getUnverifiedProjects() { return unverifiedProjects; }
}

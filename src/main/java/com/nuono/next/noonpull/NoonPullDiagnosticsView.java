package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoonPullDiagnosticsView {
    private List<PlanView> plans = new ArrayList<>();
    private List<TaskView> tasks = new ArrayList<>();
    private List<SmokeRunView> smokeRuns = new ArrayList<>();

    public List<PlanView> getPlans() {
        return plans;
    }

    public void setPlans(List<PlanView> plans) {
        this.plans = plans;
    }

    public List<TaskView> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskView> tasks) {
        this.tasks = tasks;
    }

    public List<SmokeRunView> getSmokeRuns() {
        return smokeRuns;
    }

    public void setSmokeRuns(List<SmokeRunView> smokeRuns) {
        this.smokeRuns = smokeRuns;
    }

    public static class SmokeRunView {
        private Long id;
        private String targetEnvironment;
        private Long ownerUserId;
        private String projectCode;
        private String projectName;
        private String storeCode;
        private String siteCode;
        private String rollbackOrGlobalPauseStrategy;
        private List<String> requestedDataDomains = new ArrayList<>();
        private List<String> missingRequirements = new ArrayList<>();
        private boolean evidenceGateSatisfied;
        private boolean productionSchedulingAllowed;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<NoonPullSmokeEvidenceView> evidence = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTargetEnvironment() {
            return targetEnvironment;
        }

        public void setTargetEnvironment(String targetEnvironment) {
            this.targetEnvironment = targetEnvironment;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(String projectCode) {
            this.projectCode = projectCode;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getRollbackOrGlobalPauseStrategy() {
            return rollbackOrGlobalPauseStrategy;
        }

        public void setRollbackOrGlobalPauseStrategy(String rollbackOrGlobalPauseStrategy) {
            this.rollbackOrGlobalPauseStrategy = rollbackOrGlobalPauseStrategy;
        }

        public List<String> getRequestedDataDomains() {
            return requestedDataDomains;
        }

        public void setRequestedDataDomains(List<String> requestedDataDomains) {
            this.requestedDataDomains = requestedDataDomains == null ? new ArrayList<>() : requestedDataDomains;
        }

        public List<String> getMissingRequirements() {
            return missingRequirements;
        }

        public void setMissingRequirements(List<String> missingRequirements) {
            this.missingRequirements = missingRequirements == null ? new ArrayList<>() : missingRequirements;
        }

        public boolean isEvidenceGateSatisfied() {
            return evidenceGateSatisfied;
        }

        public void setEvidenceGateSatisfied(boolean evidenceGateSatisfied) {
            this.evidenceGateSatisfied = evidenceGateSatisfied;
        }

        public boolean isProductionSchedulingAllowed() {
            return productionSchedulingAllowed;
        }

        public void setProductionSchedulingAllowed(boolean productionSchedulingAllowed) {
            this.productionSchedulingAllowed = productionSchedulingAllowed;
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

        public List<NoonPullSmokeEvidenceView> getEvidence() {
            return evidence;
        }

        public void setEvidence(List<NoonPullSmokeEvidenceView> evidence) {
            this.evidence = evidence == null ? new ArrayList<>() : evidence;
        }
    }

    public static class PlanView {
        private Long id;
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private String pullType;
        private String dataDomain;
        private String triggerMode;
        private boolean enabled;
        private boolean paused;
        private String pauseReason;
        private LocalDateTime latestSuccessAt;
        private LocalDateTime latestFailureAt;
        private String latestFailureType;
        private LocalDateTime nextRetryAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getPullType() {
            return pullType;
        }

        public void setPullType(String pullType) {
            this.pullType = pullType;
        }

        public String getDataDomain() {
            return dataDomain;
        }

        public void setDataDomain(String dataDomain) {
            this.dataDomain = dataDomain;
        }

        public String getTriggerMode() {
            return triggerMode;
        }

        public void setTriggerMode(String triggerMode) {
            this.triggerMode = triggerMode;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPaused() {
            return paused;
        }

        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        public String getPauseReason() {
            return pauseReason;
        }

        public void setPauseReason(String pauseReason) {
            this.pauseReason = pauseReason;
        }

        public LocalDateTime getLatestSuccessAt() {
            return latestSuccessAt;
        }

        public void setLatestSuccessAt(LocalDateTime latestSuccessAt) {
            this.latestSuccessAt = latestSuccessAt;
        }

        public LocalDateTime getLatestFailureAt() {
            return latestFailureAt;
        }

        public void setLatestFailureAt(LocalDateTime latestFailureAt) {
            this.latestFailureAt = latestFailureAt;
        }

        public String getLatestFailureType() {
            return latestFailureType;
        }

        public void setLatestFailureType(String latestFailureType) {
            this.latestFailureType = latestFailureType;
        }

        public LocalDateTime getNextRetryAt() {
            return nextRetryAt;
        }

        public void setNextRetryAt(LocalDateTime nextRetryAt) {
            this.nextRetryAt = nextRetryAt;
        }
    }

    public static class TaskView {
        private Long id;
        private Long planId;
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private String pullType;
        private String dataDomain;
        private String triggerMode;
        private String targetIdentity;
        private String status;
        private String sourceBatchId;
        private String failureType;
        private String retryAction;
        private Boolean retryable;
        private Boolean requiresManualAction;
        private String diagnosticSummary;
        private String lockedBy;
        private LocalDateTime queuedAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private String phase;
        private long totalElapsedMillis;
        private long queueWaitMillis;
        private long executionElapsedMillis;
        private long reportWaitMillis;
        private String reportExportStatus;
        private Integer reportPollAttempts;
        private LocalDateTime reportLastPollAt;
        private LocalDateTime reportNextPollAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getPlanId() {
            return planId;
        }

        public void setPlanId(Long planId) {
            this.planId = planId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getPullType() {
            return pullType;
        }

        public void setPullType(String pullType) {
            this.pullType = pullType;
        }

        public String getDataDomain() {
            return dataDomain;
        }

        public void setDataDomain(String dataDomain) {
            this.dataDomain = dataDomain;
        }

        public String getTriggerMode() {
            return triggerMode;
        }

        public void setTriggerMode(String triggerMode) {
            this.triggerMode = triggerMode;
        }

        public String getTargetIdentity() {
            return targetIdentity;
        }

        public void setTargetIdentity(String targetIdentity) {
            this.targetIdentity = targetIdentity;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSourceBatchId() {
            return sourceBatchId;
        }

        public void setSourceBatchId(String sourceBatchId) {
            this.sourceBatchId = sourceBatchId;
        }

        public String getFailureType() {
            return failureType;
        }

        public void setFailureType(String failureType) {
            this.failureType = failureType;
        }

        public String getRetryAction() {
            return retryAction;
        }

        public void setRetryAction(String retryAction) {
            this.retryAction = retryAction;
        }

        public Boolean getRetryable() {
            return retryable;
        }

        public void setRetryable(Boolean retryable) {
            this.retryable = retryable;
        }

        public Boolean getRequiresManualAction() {
            return requiresManualAction;
        }

        public void setRequiresManualAction(Boolean requiresManualAction) {
            this.requiresManualAction = requiresManualAction;
        }

        public String getDiagnosticSummary() {
            return diagnosticSummary;
        }

        public void setDiagnosticSummary(String diagnosticSummary) {
            this.diagnosticSummary = diagnosticSummary;
        }

        public String getLockedBy() {
            return lockedBy;
        }

        public void setLockedBy(String lockedBy) {
            this.lockedBy = lockedBy;
        }

        public LocalDateTime getQueuedAt() {
            return queuedAt;
        }

        public void setQueuedAt(LocalDateTime queuedAt) {
            this.queuedAt = queuedAt;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
        }

        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(LocalDateTime finishedAt) {
            this.finishedAt = finishedAt;
        }

        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }
        public long getTotalElapsedMillis() { return totalElapsedMillis; }
        public void setTotalElapsedMillis(long totalElapsedMillis) { this.totalElapsedMillis = totalElapsedMillis; }
        public long getQueueWaitMillis() { return queueWaitMillis; }
        public void setQueueWaitMillis(long queueWaitMillis) { this.queueWaitMillis = queueWaitMillis; }
        public long getExecutionElapsedMillis() { return executionElapsedMillis; }
        public void setExecutionElapsedMillis(long executionElapsedMillis) { this.executionElapsedMillis = executionElapsedMillis; }
        public long getReportWaitMillis() { return reportWaitMillis; }
        public void setReportWaitMillis(long reportWaitMillis) { this.reportWaitMillis = reportWaitMillis; }
        public String getReportExportStatus() { return reportExportStatus; }
        public void setReportExportStatus(String reportExportStatus) { this.reportExportStatus = reportExportStatus; }
        public Integer getReportPollAttempts() { return reportPollAttempts; }
        public void setReportPollAttempts(Integer reportPollAttempts) { this.reportPollAttempts = reportPollAttempts; }
        public LocalDateTime getReportLastPollAt() { return reportLastPollAt; }
        public void setReportLastPollAt(LocalDateTime reportLastPollAt) { this.reportLastPollAt = reportLastPollAt; }
        public LocalDateTime getReportNextPollAt() { return reportNextPollAt; }
        public void setReportNextPollAt(LocalDateTime reportNextPollAt) { this.reportNextPollAt = reportNextPollAt; }
    }
}

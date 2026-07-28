package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

final class CompetitorMonitoringCheckpoint {
    private static final ObjectMapper JSON = new ObjectMapper();

    private String batchKind;
    private String batchKey;
    private String triggerMode;
    private String executionMode;
    private Long requestedBy;
    private Long upperWatchProductId;
    private long eligibleScopeTotal;
    private long eligibleProductTotal;
    private Long upperScopeOwnerUserId;
    private String upperScopeStoreCode;
    private String upperScopeSiteCode;
    private Long afterScopeOwnerUserId;
    private String afterScopeStoreCode;
    private String afterScopeSiteCode;
    private Long currentOwnerUserId;
    private String currentStoreCode;
    private String currentSiteCode;
    private long afterWatchProductId;
    private long completedScopeCount;
    private long eligibleSeen;
    private long newlyQueued;
    private long alreadyAttempted;
    private long deferred;
    private long failed;
    private boolean completed;

    static CompetitorMonitoringCheckpoint fromJson(String value) {
        try {
            return JSON.readValue(value, CompetitorMonitoringCheckpoint.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid competitor monitoring checkpoint", exception);
        }
    }

    String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize competitor monitoring checkpoint", exception);
        }
    }

    void record(CompetitorMonitoringEnqueueOutcome outcome) {
        eligibleSeen++;
        if (outcome == CompetitorMonitoringEnqueueOutcome.CREATED) {
            newlyQueued++;
        } else if (outcome == CompetitorMonitoringEnqueueOutcome.REUSED_SAME_BATCH) {
            alreadyAttempted++;
        } else {
            deferred++;
        }
    }

    void recordFailure() {
        eligibleSeen++;
        failed++;
    }

    int progressPercent() {
        long total = "CYCLE".equals(batchKind) ? eligibleScopeTotal : eligibleProductTotal;
        long finished = "CYCLE".equals(batchKind) ? completedScopeCount : eligibleSeen;
        if (total <= 0L) {
            return 5;
        }
        return Math.min(95, 5 + (int) Math.floor((finished * 90.0d) / total));
    }

    public String getBatchKind() { return batchKind; }
    public void setBatchKind(String batchKind) { this.batchKind = batchKind; }
    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }
    public String getTriggerMode() { return triggerMode; }
    public void setTriggerMode(String triggerMode) { this.triggerMode = triggerMode; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public Long getUpperWatchProductId() { return upperWatchProductId; }
    public void setUpperWatchProductId(Long upperWatchProductId) { this.upperWatchProductId = upperWatchProductId; }
    public long getEligibleScopeTotal() { return eligibleScopeTotal; }
    public void setEligibleScopeTotal(long eligibleScopeTotal) { this.eligibleScopeTotal = eligibleScopeTotal; }
    public long getEligibleProductTotal() { return eligibleProductTotal; }
    public void setEligibleProductTotal(long eligibleProductTotal) { this.eligibleProductTotal = eligibleProductTotal; }
    public Long getUpperScopeOwnerUserId() { return upperScopeOwnerUserId; }
    public void setUpperScopeOwnerUserId(Long value) { this.upperScopeOwnerUserId = value; }
    public String getUpperScopeStoreCode() { return upperScopeStoreCode; }
    public void setUpperScopeStoreCode(String value) { this.upperScopeStoreCode = value; }
    public String getUpperScopeSiteCode() { return upperScopeSiteCode; }
    public void setUpperScopeSiteCode(String value) { this.upperScopeSiteCode = value; }
    public Long getAfterScopeOwnerUserId() { return afterScopeOwnerUserId; }
    public void setAfterScopeOwnerUserId(Long value) { this.afterScopeOwnerUserId = value; }
    public String getAfterScopeStoreCode() { return afterScopeStoreCode; }
    public void setAfterScopeStoreCode(String value) { this.afterScopeStoreCode = value; }
    public String getAfterScopeSiteCode() { return afterScopeSiteCode; }
    public void setAfterScopeSiteCode(String value) { this.afterScopeSiteCode = value; }
    public Long getCurrentOwnerUserId() { return currentOwnerUserId; }
    public void setCurrentOwnerUserId(Long value) { this.currentOwnerUserId = value; }
    public String getCurrentStoreCode() { return currentStoreCode; }
    public void setCurrentStoreCode(String value) { this.currentStoreCode = value; }
    public String getCurrentSiteCode() { return currentSiteCode; }
    public void setCurrentSiteCode(String value) { this.currentSiteCode = value; }
    public long getAfterWatchProductId() { return afterWatchProductId; }
    public void setAfterWatchProductId(long value) { this.afterWatchProductId = value; }
    public long getCompletedScopeCount() { return completedScopeCount; }
    public void setCompletedScopeCount(long value) { this.completedScopeCount = value; }
    public long getEligibleSeen() { return eligibleSeen; }
    public void setEligibleSeen(long eligibleSeen) { this.eligibleSeen = eligibleSeen; }
    public long getNewlyQueued() { return newlyQueued; }
    public void setNewlyQueued(long newlyQueued) { this.newlyQueued = newlyQueued; }
    public long getAlreadyAttempted() { return alreadyAttempted; }
    public void setAlreadyAttempted(long value) { this.alreadyAttempted = value; }
    public long getDeferred() { return deferred; }
    public void setDeferred(long deferred) { this.deferred = deferred; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public long getSubmittedCount() { return newlyQueued + alreadyAttempted; }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public long getWatchProductTotal() {
        return "CYCLE".equals(batchKind) ? eligibleSeen : eligibleProductTotal;
    }
}

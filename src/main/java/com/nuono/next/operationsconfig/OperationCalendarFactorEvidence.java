package com.nuono.next.operationsconfig;

public class OperationCalendarFactorEvidence {

    private final Long ruleId;
    private final String ruleName;
    private final String targetScopeType;
    private final String targetScopeValue;
    private final String matchState;
    private final String reason;
    private final int priority;
    private final Long bundleVersionId;
    private final String bundleVersionNo;
    private final String bundleSourceRole;
    private final String bundleSourceLabel;
    private final String calendarVersionNo;
    private final String calendarVersionName;
    private final String calendarVersionSourceLabel;

    public OperationCalendarFactorEvidence(
            Long ruleId,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String matchState,
            String reason,
            int priority
    ) {
        this(ruleId, ruleName, targetScopeType, targetScopeValue, matchState, reason, priority, null, null, null, null);
    }

    public OperationCalendarFactorEvidence(
            Long ruleId,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String matchState,
            String reason,
            int priority,
            Long bundleVersionId,
            String bundleVersionNo,
            String bundleSourceRole,
            String bundleSourceLabel
    ) {
        this(
                ruleId,
                ruleName,
                targetScopeType,
                targetScopeValue,
                matchState,
                reason,
                priority,
                bundleVersionId,
                bundleVersionNo,
                bundleSourceRole,
                bundleSourceLabel,
                null,
                null,
                null
        );
    }

    public OperationCalendarFactorEvidence(
            Long ruleId,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String matchState,
            String reason,
            int priority,
            Long bundleVersionId,
            String bundleVersionNo,
            String bundleSourceRole,
            String bundleSourceLabel,
            String calendarVersionNo,
            String calendarVersionName,
            String calendarVersionSourceLabel
    ) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.targetScopeType = targetScopeType;
        this.targetScopeValue = targetScopeValue;
        this.matchState = matchState;
        this.reason = reason;
        this.priority = priority;
        this.bundleVersionId = bundleVersionId;
        this.bundleVersionNo = bundleVersionNo;
        this.bundleSourceRole = bundleSourceRole;
        this.bundleSourceLabel = bundleSourceLabel;
        this.calendarVersionNo = calendarVersionNo;
        this.calendarVersionName = calendarVersionName;
        this.calendarVersionSourceLabel = calendarVersionSourceLabel;
    }

    public OperationCalendarFactorEvidence withMatchState(String nextMatchState, String nextReason) {
        return new OperationCalendarFactorEvidence(
                ruleId,
                ruleName,
                targetScopeType,
                targetScopeValue,
                nextMatchState,
                nextReason,
                priority,
                bundleVersionId,
                bundleVersionNo,
                bundleSourceRole,
                bundleSourceLabel,
                calendarVersionNo,
                calendarVersionName,
                calendarVersionSourceLabel
        );
    }

    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public String getTargetScopeType() { return targetScopeType; }
    public String getTargetScopeValue() { return targetScopeValue; }
    public String getMatchState() { return matchState; }
    public String getReason() { return reason; }
    public int getPriority() { return priority; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public String getBundleVersionNo() { return bundleVersionNo; }
    public String getBundleSourceRole() { return bundleSourceRole; }
    public String getBundleSourceLabel() { return bundleSourceLabel; }
    public String getCalendarVersionNo() { return calendarVersionNo; }
    public String getCalendarVersionName() { return calendarVersionName; }
    public String getCalendarVersionSourceLabel() { return calendarVersionSourceLabel; }
}

package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.util.List;

public class OperationCalendarFactorMatchResult {

    private final BigDecimal appliedFactor;
    private final Long selectedRuleId;
    private final String selectedRuleName;
    private final Long selectedBundleVersionId;
    private final String selectedBundleVersionNo;
    private final String selectedBundleSourceRole;
    private final String selectedBundleSourceLabel;
    private final String calendarVersionNo;
    private final String calendarVersionName;
    private final String calendarVersionSourceLabel;
    private final List<OperationCalendarFactorEvidence> evidence;

    public OperationCalendarFactorMatchResult(
            BigDecimal appliedFactor,
            Long selectedRuleId,
            String selectedRuleName,
            List<OperationCalendarFactorEvidence> evidence
    ) {
        this(appliedFactor, selectedRuleId, selectedRuleName, null, null, null, null, evidence);
    }

    public OperationCalendarFactorMatchResult(
            BigDecimal appliedFactor,
            Long selectedRuleId,
            String selectedRuleName,
            Long selectedBundleVersionId,
            String selectedBundleVersionNo,
            String selectedBundleSourceRole,
            String selectedBundleSourceLabel,
            List<OperationCalendarFactorEvidence> evidence
    ) {
        this(
                appliedFactor,
                selectedRuleId,
                selectedRuleName,
                selectedBundleVersionId,
                selectedBundleVersionNo,
                selectedBundleSourceRole,
                selectedBundleSourceLabel,
                null,
                null,
                null,
                evidence
        );
    }

    public OperationCalendarFactorMatchResult(
            BigDecimal appliedFactor,
            Long selectedRuleId,
            String selectedRuleName,
            Long selectedBundleVersionId,
            String selectedBundleVersionNo,
            String selectedBundleSourceRole,
            String selectedBundleSourceLabel,
            String calendarVersionNo,
            String calendarVersionName,
            String calendarVersionSourceLabel,
            List<OperationCalendarFactorEvidence> evidence
    ) {
        this.appliedFactor = appliedFactor;
        this.selectedRuleId = selectedRuleId;
        this.selectedRuleName = selectedRuleName;
        this.selectedBundleVersionId = selectedBundleVersionId;
        this.selectedBundleVersionNo = selectedBundleVersionNo;
        this.selectedBundleSourceRole = selectedBundleSourceRole;
        this.selectedBundleSourceLabel = selectedBundleSourceLabel;
        this.calendarVersionNo = calendarVersionNo;
        this.calendarVersionName = calendarVersionName;
        this.calendarVersionSourceLabel = calendarVersionSourceLabel;
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public BigDecimal getAppliedFactor() { return appliedFactor; }
    public Long getSelectedRuleId() { return selectedRuleId; }
    public String getSelectedRuleName() { return selectedRuleName; }
    public Long getSelectedBundleVersionId() { return selectedBundleVersionId; }
    public String getSelectedBundleVersionNo() { return selectedBundleVersionNo; }
    public String getSelectedBundleSourceRole() { return selectedBundleSourceRole; }
    public String getSelectedBundleSourceLabel() { return selectedBundleSourceLabel; }
    public String getCalendarVersionNo() { return calendarVersionNo; }
    public String getCalendarVersionName() { return calendarVersionName; }
    public String getCalendarVersionSourceLabel() { return calendarVersionSourceLabel; }
    public List<OperationCalendarFactorEvidence> getEvidence() { return evidence; }
}

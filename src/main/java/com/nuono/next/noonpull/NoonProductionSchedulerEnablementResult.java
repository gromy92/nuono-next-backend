package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonProductionSchedulerEnablementResult {
    private boolean enabled;
    private Long smokeRunId;
    private List<String> rejectionReasons = new ArrayList<>();
    private List<Long> planIds = new ArrayList<>();
    private List<String> enabledDomains = new ArrayList<>();
    private String decisionRecordId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getSmokeRunId() {
        return smokeRunId;
    }

    public void setSmokeRunId(Long smokeRunId) {
        this.smokeRunId = smokeRunId;
    }

    public List<String> getRejectionReasons() {
        return Collections.unmodifiableList(rejectionReasons);
    }

    public void setRejectionReasons(List<String> rejectionReasons) {
        this.rejectionReasons = rejectionReasons == null ? new ArrayList<>() : new ArrayList<>(rejectionReasons);
    }

    public List<Long> getPlanIds() {
        return Collections.unmodifiableList(planIds);
    }

    public void setPlanIds(List<Long> planIds) {
        this.planIds = planIds == null ? new ArrayList<>() : new ArrayList<>(planIds);
    }

    public List<String> getEnabledDomains() {
        return Collections.unmodifiableList(enabledDomains);
    }

    public void setEnabledDomains(List<String> enabledDomains) {
        this.enabledDomains = enabledDomains == null ? new ArrayList<>() : new ArrayList<>(enabledDomains);
    }

    public String getDecisionRecordId() {
        return decisionRecordId;
    }

    public void setDecisionRecordId(String decisionRecordId) {
        this.decisionRecordId = decisionRecordId;
    }
}

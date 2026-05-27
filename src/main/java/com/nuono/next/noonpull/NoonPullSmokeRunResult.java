package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonPullSmokeRunResult {
    private Long smokeRunId;
    private String targetEnvironment;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private List<NoonPullSmokeEvidenceView> evidence = new ArrayList<>();
    private List<String> missingRequirements = new ArrayList<>();
    private boolean evidenceGateSatisfied;
    private boolean productionSchedulingAllowed;

    public Long getSmokeRunId() {
        return smokeRunId;
    }

    public void setSmokeRunId(Long smokeRunId) {
        this.smokeRunId = smokeRunId;
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

    public List<NoonPullSmokeEvidenceView> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    public void setEvidence(List<NoonPullSmokeEvidenceView> evidence) {
        this.evidence = evidence == null ? new ArrayList<>() : new ArrayList<>(evidence);
    }

    public List<String> getMissingRequirements() {
        return Collections.unmodifiableList(missingRequirements);
    }

    public void setMissingRequirements(List<String> missingRequirements) {
        this.missingRequirements = missingRequirements == null ? new ArrayList<>() : new ArrayList<>(missingRequirements);
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
}

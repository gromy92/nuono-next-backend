package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NoonPullSmokeRunRecord {
    private Long id;
    private String targetEnvironment;
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private String rollbackOrGlobalPauseStrategy;
    private String requestedDataDomainsText;
    private String missingRequirementsText;
    private boolean evidenceGateSatisfied;
    private boolean productionSchedulingAllowed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<NoonPullSmokeEvidenceRecord> evidence = new ArrayList<>();

    public NoonPullSmokeRunRecord copy() {
        NoonPullSmokeRunRecord copy = new NoonPullSmokeRunRecord();
        copy.id = id;
        copy.targetEnvironment = targetEnvironment;
        copy.ownerUserId = ownerUserId;
        copy.projectCode = projectCode;
        copy.projectName = projectName;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.rollbackOrGlobalPauseStrategy = rollbackOrGlobalPauseStrategy;
        copy.requestedDataDomainsText = requestedDataDomainsText;
        copy.missingRequirementsText = missingRequirementsText;
        copy.evidenceGateSatisfied = evidenceGateSatisfied;
        copy.productionSchedulingAllowed = productionSchedulingAllowed;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.evidence = evidence.stream().map(NoonPullSmokeEvidenceRecord::copy).collect(Collectors.toList());
        return copy;
    }

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

    public String getRequestedDataDomainsText() {
        return requestedDataDomainsText;
    }

    public void setRequestedDataDomainsText(String requestedDataDomainsText) {
        this.requestedDataDomainsText = requestedDataDomainsText;
    }

    public List<String> getRequestedDataDomains() {
        return requestedDataDomains();
    }

    public List<String> requestedDataDomains() {
        return splitList(requestedDataDomainsText);
    }

    public void setRequestedDataDomains(List<String> requestedDataDomains) {
        this.requestedDataDomainsText = joinList(requestedDataDomains);
    }

    public String getMissingRequirementsText() {
        return missingRequirementsText;
    }

    public void setMissingRequirementsText(String missingRequirementsText) {
        this.missingRequirementsText = missingRequirementsText;
    }

    public List<String> getMissingRequirements() {
        return splitList(missingRequirementsText);
    }

    public void setMissingRequirements(List<String> missingRequirements) {
        this.missingRequirementsText = joinList(missingRequirements);
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

    public List<NoonPullSmokeEvidenceRecord> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    public void setEvidence(List<NoonPullSmokeEvidenceRecord> evidence) {
        this.evidence = evidence == null
                ? new ArrayList<>()
                : evidence.stream().map(NoonPullSmokeEvidenceRecord::copy).collect(Collectors.toList());
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter((item) -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter((value) -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
    }
}

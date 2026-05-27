package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NoonProductionSchedulerEnablementRecord {
    private Long id;
    private String targetEnvironment;
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private String enabledDomainsText;
    private String scheduleBoundaries;
    private String rollbackOrGlobalPauseStrategy;
    private Long operatorUserId;
    private Long smokeRunId;
    private String decision;
    private String rejectionReasonsText;
    private String planIdsText;
    private boolean hitlApproved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoonProductionSchedulerEnablementRecord copy() {
        NoonProductionSchedulerEnablementRecord copy = new NoonProductionSchedulerEnablementRecord();
        copy.id = id;
        copy.targetEnvironment = targetEnvironment;
        copy.ownerUserId = ownerUserId;
        copy.projectCode = projectCode;
        copy.projectName = projectName;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.enabledDomainsText = enabledDomainsText;
        copy.scheduleBoundaries = scheduleBoundaries;
        copy.rollbackOrGlobalPauseStrategy = rollbackOrGlobalPauseStrategy;
        copy.operatorUserId = operatorUserId;
        copy.smokeRunId = smokeRunId;
        copy.decision = decision;
        copy.rejectionReasonsText = rejectionReasonsText;
        copy.planIdsText = planIdsText;
        copy.hitlApproved = hitlApproved;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
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

    public String getEnabledDomainsText() {
        return enabledDomainsText;
    }

    public void setEnabledDomainsText(String enabledDomainsText) {
        this.enabledDomainsText = enabledDomainsText;
    }

    public List<String> getEnabledDomains() {
        return split(enabledDomainsText);
    }

    public void setEnabledDomains(List<String> enabledDomains) {
        this.enabledDomainsText = join(enabledDomains);
    }

    public String getScheduleBoundaries() {
        return scheduleBoundaries;
    }

    public void setScheduleBoundaries(String scheduleBoundaries) {
        this.scheduleBoundaries = scheduleBoundaries;
    }

    public String getRollbackOrGlobalPauseStrategy() {
        return rollbackOrGlobalPauseStrategy;
    }

    public void setRollbackOrGlobalPauseStrategy(String rollbackOrGlobalPauseStrategy) {
        this.rollbackOrGlobalPauseStrategy = rollbackOrGlobalPauseStrategy;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getSmokeRunId() {
        return smokeRunId;
    }

    public void setSmokeRunId(Long smokeRunId) {
        this.smokeRunId = smokeRunId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRejectionReasonsText() {
        return rejectionReasonsText;
    }

    public void setRejectionReasonsText(String rejectionReasonsText) {
        this.rejectionReasonsText = rejectionReasonsText;
    }

    public List<String> getRejectionReasons() {
        return split(rejectionReasonsText);
    }

    public void setRejectionReasons(List<String> rejectionReasons) {
        this.rejectionReasonsText = join(rejectionReasons);
    }

    public String getPlanIdsText() {
        return planIdsText;
    }

    public void setPlanIdsText(String planIdsText) {
        this.planIdsText = planIdsText;
    }

    public List<Long> getPlanIds() {
        if (planIdsText == null || planIdsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(planIdsText.split(",", -1))
                .map(String::trim)
                .filter((value) -> !value.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableList());
    }

    public void setPlanIds(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            this.planIdsText = "";
            return;
        }
        this.planIdsText = planIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean isHitlApproved() {
        return hitlApproved;
    }

    public void setHitlApproved(boolean hitlApproved) {
        this.hitlApproved = hitlApproved;
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

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .filter((item) -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter((value) -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
    }
}

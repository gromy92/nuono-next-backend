package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonProductionSchedulerEnablementCommand {
    private String targetEnvironment;
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private List<NoonPullDataDomain> enabledDomains = new ArrayList<>();
    private String scheduleBoundaries;
    private String rollbackOrGlobalPauseStrategy;
    private boolean hitlApproved;

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

    public List<NoonPullDataDomain> getEnabledDomains() {
        return Collections.unmodifiableList(enabledDomains);
    }

    public void setEnabledDomains(List<NoonPullDataDomain> enabledDomains) {
        this.enabledDomains = enabledDomains == null ? new ArrayList<>() : new ArrayList<>(enabledDomains);
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

    public boolean isHitlApproved() {
        return hitlApproved;
    }

    public void setHitlApproved(boolean hitlApproved) {
        this.hitlApproved = hitlApproved;
    }
}

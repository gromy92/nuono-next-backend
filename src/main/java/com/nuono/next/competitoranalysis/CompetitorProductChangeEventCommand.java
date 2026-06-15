package com.nuono.next.competitoranalysis;

import java.time.LocalDate;

public class CompetitorProductChangeEventCommand {
    private Long id;
    private Long snapshotId;
    private Long previousSnapshotId;
    private Long ownerUserId;
    private Long watchProductId;
    private Long competitorProductId;
    private String subjectType;
    private String siteCode;
    private String noonProductCode;
    private LocalDate factDate;
    private String fieldKey;
    private String fieldLabel;
    private String changeType;
    private String oldValueJson;
    private String newValueJson;
    private String severity;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Long getPreviousSnapshotId() { return previousSnapshotId; }
    public void setPreviousSnapshotId(Long previousSnapshotId) { this.previousSnapshotId = previousSnapshotId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long competitorProductId) { this.competitorProductId = competitorProductId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getFieldLabel() { return fieldLabel; }
    public void setFieldLabel(String fieldLabel) { this.fieldLabel = fieldLabel; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getOldValueJson() { return oldValueJson; }
    public void setOldValueJson(String oldValueJson) { this.oldValueJson = oldValueJson; }
    public String getNewValueJson() { return newValueJson; }
    public void setNewValueJson(String newValueJson) { this.newValueJson = newValueJson; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}

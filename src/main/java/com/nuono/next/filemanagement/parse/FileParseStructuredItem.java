package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseStructuredItem {

    private String itemType;
    private String naturalKey;
    private String naturalKeyHash;
    private String changeType = "added";
    private String reviewStatus = "pending";
    private String confidence;
    private String validationStatus = "pass";
    private String normalizedPayloadJson;
    private String oldPayloadJson;
    private String changedFieldKeysJson;
    private String effectivePayloadJson;
    private String effectiveValidationStatus;
    private String effectivePayloadHash;
    private String evidenceJson;
    private String validationErrorJson;
    private Integer sortNo;
    private List<Long> sourceRowIds = new ArrayList<>();

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public void setNaturalKey(String naturalKey) {
        this.naturalKey = naturalKey;
    }

    public String getNaturalKeyHash() {
        return naturalKeyHash;
    }

    public void setNaturalKeyHash(String naturalKeyHash) {
        this.naturalKeyHash = naturalKeyHash;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getNormalizedPayloadJson() {
        return normalizedPayloadJson;
    }

    public void setNormalizedPayloadJson(String normalizedPayloadJson) {
        this.normalizedPayloadJson = normalizedPayloadJson;
    }

    public String getOldPayloadJson() {
        return oldPayloadJson;
    }

    public void setOldPayloadJson(String oldPayloadJson) {
        this.oldPayloadJson = oldPayloadJson;
    }

    public String getChangedFieldKeysJson() {
        return changedFieldKeysJson;
    }

    public void setChangedFieldKeysJson(String changedFieldKeysJson) {
        this.changedFieldKeysJson = changedFieldKeysJson;
    }

    public String getEffectivePayloadJson() {
        return effectivePayloadJson;
    }

    public void setEffectivePayloadJson(String effectivePayloadJson) {
        this.effectivePayloadJson = effectivePayloadJson;
    }

    public String getEffectiveValidationStatus() {
        return effectiveValidationStatus;
    }

    public void setEffectiveValidationStatus(String effectiveValidationStatus) {
        this.effectiveValidationStatus = effectiveValidationStatus;
    }

    public String getEffectivePayloadHash() {
        return effectivePayloadHash;
    }

    public void setEffectivePayloadHash(String effectivePayloadHash) {
        this.effectivePayloadHash = effectivePayloadHash;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getValidationErrorJson() {
        return validationErrorJson;
    }

    public void setValidationErrorJson(String validationErrorJson) {
        this.validationErrorJson = validationErrorJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public List<Long> getSourceRowIds() {
        return new ArrayList<>(sourceRowIds);
    }

    public void setSourceRowIds(List<Long> sourceRowIds) {
        this.sourceRowIds = sourceRowIds == null ? new ArrayList<>() : new ArrayList<>(sourceRowIds);
    }
}

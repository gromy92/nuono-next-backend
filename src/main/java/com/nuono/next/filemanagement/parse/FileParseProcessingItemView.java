package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileParseProcessingItemView {

    private Long itemId;
    private Long taskId;
    private Long resultId;
    private String itemType;
    private String naturalKey;
    private String changeType;
    private String reviewStatus;
    private String confidence;
    private String validationStatus;
    private Map<String, Object> fields = new LinkedHashMap<>();
    private Map<String, Object> oldFields = new LinkedHashMap<>();
    private List<String> changedFieldKeys = new ArrayList<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> validationError = new LinkedHashMap<>();
    private Integer sortNo;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

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

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : fields;
    }

    public Map<String, Object> getOldFields() {
        return oldFields;
    }

    public void setOldFields(Map<String, Object> oldFields) {
        this.oldFields = oldFields == null ? new LinkedHashMap<>() : oldFields;
    }

    public List<String> getChangedFieldKeys() {
        return changedFieldKeys;
    }

    public void setChangedFieldKeys(List<String> changedFieldKeys) {
        this.changedFieldKeys = changedFieldKeys == null ? new ArrayList<>() : changedFieldKeys;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence == null ? new LinkedHashMap<>() : evidence;
    }

    public Map<String, Object> getValidationError() {
        return validationError;
    }

    public void setValidationError(Map<String, Object> validationError) {
        this.validationError = validationError == null ? new LinkedHashMap<>() : validationError;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

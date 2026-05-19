package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileParseItemCompareView {

    private Long itemId;
    private Long taskId;
    private Long resultId;
    private String changeType;
    private String naturalKey;
    private List<String> changedFieldKeys = new ArrayList<>();
    private Map<String, Object> baseFields = new LinkedHashMap<>();
    private Map<String, Object> currentFields = new LinkedHashMap<>();
    private String reviewStatus;

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

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public void setNaturalKey(String naturalKey) {
        this.naturalKey = naturalKey;
    }

    public List<String> getChangedFieldKeys() {
        return changedFieldKeys;
    }

    public void setChangedFieldKeys(List<String> changedFieldKeys) {
        this.changedFieldKeys = changedFieldKeys == null ? new ArrayList<>() : changedFieldKeys;
    }

    public Map<String, Object> getBaseFields() {
        return baseFields;
    }

    public void setBaseFields(Map<String, Object> baseFields) {
        this.baseFields = baseFields == null ? new LinkedHashMap<>() : baseFields;
    }

    public Map<String, Object> getCurrentFields() {
        return currentFields;
    }

    public void setCurrentFields(Map<String, Object> currentFields) {
        this.currentFields = currentFields == null ? new LinkedHashMap<>() : currentFields;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }
}

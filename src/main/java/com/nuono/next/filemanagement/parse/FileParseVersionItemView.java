package com.nuono.next.filemanagement.parse;

import java.util.Map;

public class FileParseVersionItemView {

    private Long versionItemId;
    private Long versionId;
    private String itemType;
    private String naturalKey;
    private Map<String, Object> fields;
    private Long sourceResultItemId;
    private Integer sortNo;

    public Long getVersionItemId() {
        return versionItemId;
    }

    public void setVersionItemId(Long versionItemId) {
        this.versionItemId = versionItemId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
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

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public Long getSourceResultItemId() {
        return sourceResultItemId;
    }

    public void setSourceResultItemId(Long sourceResultItemId) {
        this.sourceResultItemId = sourceResultItemId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

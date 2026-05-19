package com.nuono.next.filemanagement.parse;

public class FileParseVersionItemRow {

    private Long id;
    private Long versionId;
    private Long targetPlanId;
    private String itemType;
    private String naturalKey;
    private String naturalKeyHash;
    private String versionPayloadJson;
    private Long sourceResultItemId;
    private Integer sortNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
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

    public String getNaturalKeyHash() {
        return naturalKeyHash;
    }

    public void setNaturalKeyHash(String naturalKeyHash) {
        this.naturalKeyHash = naturalKeyHash;
    }

    public String getVersionPayloadJson() {
        return versionPayloadJson;
    }

    public void setVersionPayloadJson(String versionPayloadJson) {
        this.versionPayloadJson = versionPayloadJson;
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

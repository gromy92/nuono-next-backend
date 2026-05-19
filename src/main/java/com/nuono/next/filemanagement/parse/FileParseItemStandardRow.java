package com.nuono.next.filemanagement.parse;

public class FileParseItemStandardRow {

    private Long id;
    private Long standardVersionId;
    private String itemType;
    private String itemLabel;
    private String naturalKeyJson;
    private String fieldSchemaJson;
    private String displayConfigJson;
    private String validationRuleJson;
    private String diffRuleJson;
    private Integer sortNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStandardVersionId() {
        return standardVersionId;
    }

    public void setStandardVersionId(Long standardVersionId) {
        this.standardVersionId = standardVersionId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public void setItemLabel(String itemLabel) {
        this.itemLabel = itemLabel;
    }

    public String getNaturalKeyJson() {
        return naturalKeyJson;
    }

    public void setNaturalKeyJson(String naturalKeyJson) {
        this.naturalKeyJson = naturalKeyJson;
    }

    public String getFieldSchemaJson() {
        return fieldSchemaJson;
    }

    public void setFieldSchemaJson(String fieldSchemaJson) {
        this.fieldSchemaJson = fieldSchemaJson;
    }

    public String getDisplayConfigJson() {
        return displayConfigJson;
    }

    public void setDisplayConfigJson(String displayConfigJson) {
        this.displayConfigJson = displayConfigJson;
    }

    public String getValidationRuleJson() {
        return validationRuleJson;
    }

    public void setValidationRuleJson(String validationRuleJson) {
        this.validationRuleJson = validationRuleJson;
    }

    public String getDiffRuleJson() {
        return diffRuleJson;
    }

    public void setDiffRuleJson(String diffRuleJson) {
        this.diffRuleJson = diffRuleJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

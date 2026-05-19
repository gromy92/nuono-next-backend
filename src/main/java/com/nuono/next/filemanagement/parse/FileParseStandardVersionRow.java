package com.nuono.next.filemanagement.parse;

public class FileParseStandardVersionRow {

    private Long id;
    private Long standardId;
    private String standardVersion;
    private String resultSchemaJson;
    private String validationRuleJson;
    private String displayConfigJson;
    private String diffRuleJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStandardId() {
        return standardId;
    }

    public void setStandardId(Long standardId) {
        this.standardId = standardId;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public void setStandardVersion(String standardVersion) {
        this.standardVersion = standardVersion;
    }

    public String getResultSchemaJson() {
        return resultSchemaJson;
    }

    public void setResultSchemaJson(String resultSchemaJson) {
        this.resultSchemaJson = resultSchemaJson;
    }

    public String getValidationRuleJson() {
        return validationRuleJson;
    }

    public void setValidationRuleJson(String validationRuleJson) {
        this.validationRuleJson = validationRuleJson;
    }

    public String getDisplayConfigJson() {
        return displayConfigJson;
    }

    public void setDisplayConfigJson(String displayConfigJson) {
        this.displayConfigJson = displayConfigJson;
    }

    public String getDiffRuleJson() {
        return diffRuleJson;
    }

    public void setDiffRuleJson(String diffRuleJson) {
        this.diffRuleJson = diffRuleJson;
    }
}

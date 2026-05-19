package com.nuono.next.filemanagement.parse;

public class FileParseTaskInputCommand {

    private String inputType;
    private String inputRole;
    private Long fileAssetId;
    private String textContent;
    private String displayName;
    private Integer sortNo;

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getInputRole() {
        return inputRole;
    }

    public void setInputRole(String inputRole) {
        this.inputRole = inputRole;
    }

    public Long getFileAssetId() {
        return fileAssetId;
    }

    public void setFileAssetId(Long fileAssetId) {
        this.fileAssetId = fileAssetId;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

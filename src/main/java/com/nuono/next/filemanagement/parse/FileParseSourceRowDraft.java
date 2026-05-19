package com.nuono.next.filemanagement.parse;

public class FileParseSourceRowDraft {

    private Long taskInputId;
    private Long fileAssetId;
    private String sourceType;
    private String sourceLocator;
    private Integer pageNo;
    private String sheetName;
    private Integer tableNo;
    private Integer rowNo;
    private String columnRange;
    private String rawText;
    private String rawCellsJson;
    private String sourceHash;
    private String extractorType;
    private String extractorVersion;
    private Integer sortNo;

    public Long getTaskInputId() {
        return taskInputId;
    }

    public void setTaskInputId(Long taskInputId) {
        this.taskInputId = taskInputId;
    }

    public Long getFileAssetId() {
        return fileAssetId;
    }

    public void setFileAssetId(Long fileAssetId) {
        this.fileAssetId = fileAssetId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public void setSourceLocator(String sourceLocator) {
        this.sourceLocator = sourceLocator;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public Integer getTableNo() {
        return tableNo;
    }

    public void setTableNo(Integer tableNo) {
        this.tableNo = tableNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getColumnRange() {
        return columnRange;
    }

    public void setColumnRange(String columnRange) {
        this.columnRange = columnRange;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getRawCellsJson() {
        return rawCellsJson;
    }

    public void setRawCellsJson(String rawCellsJson) {
        this.rawCellsJson = rawCellsJson;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public String getExtractorType() {
        return extractorType;
    }

    public void setExtractorType(String extractorType) {
        this.extractorType = extractorType;
    }

    public String getExtractorVersion() {
        return extractorVersion;
    }

    public void setExtractorVersion(String extractorVersion) {
        this.extractorVersion = extractorVersion;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}

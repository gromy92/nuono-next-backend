package com.nuono.next.filemanagement.parse;

public class FileParseExtractionSummary {

    private Long inputId;
    private String inputType;
    private String displayName;
    private String extractor;
    private String status;
    private Integer extractedTextLength;
    private String message;

    public Long getInputId() {
        return inputId;
    }

    public void setInputId(Long inputId) {
        this.inputId = inputId;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getExtractor() {
        return extractor;
    }

    public void setExtractor(String extractor) {
        this.extractor = extractor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getExtractedTextLength() {
        return extractedTextLength;
    }

    public void setExtractedTextLength(Integer extractedTextLength) {
        this.extractedTextLength = extractedTextLength;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

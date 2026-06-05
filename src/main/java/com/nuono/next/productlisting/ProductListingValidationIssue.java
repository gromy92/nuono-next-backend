package com.nuono.next.productlisting;

public class ProductListingValidationIssue {

    private String fieldKey;
    private String severity;
    private String code;
    private String message;

    public ProductListingValidationIssue() {
    }

    public ProductListingValidationIssue(String fieldKey, String severity, String code, String message) {
        this.fieldKey = fieldKey;
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package com.nuono.next.competitoranalysis;

import java.time.LocalDate;

public class CompetitorProductChangeEventRow {
    private Long id;
    private LocalDate factDate;
    private String noonProductCode;
    private String productName;
    private String subjectType;
    private String fieldKey;
    private String fieldLabel;
    private String changeType;
    private String oldValueJson;
    private String newValueJson;
    private String severity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate factDate) { this.factDate = factDate; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getFieldLabel() { return fieldLabel; }
    public void setFieldLabel(String fieldLabel) { this.fieldLabel = fieldLabel; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getOldValueJson() { return oldValueJson; }
    public void setOldValueJson(String oldValueJson) { this.oldValueJson = oldValueJson; }
    public String getNewValueJson() { return newValueJson; }
    public void setNewValueJson(String newValueJson) { this.newValueJson = newValueJson; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}

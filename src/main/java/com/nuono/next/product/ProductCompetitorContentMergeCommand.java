package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductCompetitorContentMergeCommand {

    private String fieldType;
    private String targetLang;
    private String currentText;
    private List<String> competitorTexts = new ArrayList<>();
    private Long operatorUserId;

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }

    public String getCurrentText() {
        return currentText;
    }

    public void setCurrentText(String currentText) {
        this.currentText = currentText;
    }

    public List<String> getCompetitorTexts() {
        return competitorTexts;
    }

    public void setCompetitorTexts(List<String> competitorTexts) {
        this.competitorTexts = competitorTexts == null ? new ArrayList<>() : new ArrayList<>(competitorTexts);
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}

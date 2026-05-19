package com.nuono.next.productcompletion;

import java.util.ArrayList;
import java.util.List;

public class ProductInfoCompletionView {

    private boolean ready;
    private String message;
    private String sourcePlatform = "1688";
    private String sourceUrl;
    private String extractionMode;
    private String completionLevel;
    private String aiStatus;
    private String aiModel;
    private List<ProductInfoCompletionFieldView> fields = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<ProductInfoCompletionRiskView> riskFlags = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getExtractionMode() {
        return extractionMode;
    }

    public void setExtractionMode(String extractionMode) {
        this.extractionMode = extractionMode;
    }

    public String getCompletionLevel() {
        return completionLevel;
    }

    public void setCompletionLevel(String completionLevel) {
        this.completionLevel = completionLevel;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public List<ProductInfoCompletionFieldView> getFields() {
        return fields;
    }

    public void setFields(List<ProductInfoCompletionFieldView> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields == null ? new ArrayList<>() : missingFields;
    }

    public List<ProductInfoCompletionRiskView> getRiskFlags() {
        return riskFlags;
    }

    public void setRiskFlags(List<ProductInfoCompletionRiskView> riskFlags) {
        this.riskFlags = riskFlags == null ? new ArrayList<>() : riskFlags;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions == null ? new ArrayList<>() : suggestions;
    }
}

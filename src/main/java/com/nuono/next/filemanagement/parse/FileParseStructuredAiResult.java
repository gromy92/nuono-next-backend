package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseStructuredAiResult {

    private String parserType = "ai_structured_text";
    private String parserModel;
    private String summaryJson;
    private String rawResultJson;
    private String validationSummaryJson;
    private List<FileParseStructuredItem> items = new ArrayList<>();

    public String getParserType() {
        return parserType;
    }

    public void setParserType(String parserType) {
        this.parserType = parserType;
    }

    public String getParserModel() {
        return parserModel;
    }

    public void setParserModel(String parserModel) {
        this.parserModel = parserModel;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getRawResultJson() {
        return rawResultJson;
    }

    public void setRawResultJson(String rawResultJson) {
        this.rawResultJson = rawResultJson;
    }

    public String getValidationSummaryJson() {
        return validationSummaryJson;
    }

    public void setValidationSummaryJson(String validationSummaryJson) {
        this.validationSummaryJson = validationSummaryJson;
    }

    public List<FileParseStructuredItem> getItems() {
        return items;
    }

    public void setItems(List<FileParseStructuredItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}

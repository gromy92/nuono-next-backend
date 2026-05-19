package com.nuono.next.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiStructuredTextResult {

    private String status;
    private String provider;
    private String model;
    private String outputText;
    private Map<String, Object> parsedJson;
    private String refusal;
    private String errorCode;
    private String errorMessage;
    private String requestId;
    private String responseId;
    private AiUsage usage;
    private Long durationMillis;
    private List<String> warnings = new ArrayList<>();

    public static AiStructuredTextResult success() {
        AiStructuredTextResult result = new AiStructuredTextResult();
        result.setStatus(AiResultStatus.SUCCESS);
        return result;
    }

    public static AiStructuredTextResult failure(String status, String errorCode, String errorMessage) {
        AiStructuredTextResult result = new AiStructuredTextResult();
        result.setStatus(status);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public boolean isSuccess() {
        return AiResultStatus.SUCCESS.equals(status);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public Map<String, Object> getParsedJson() {
        return parsedJson;
    }

    public void setParsedJson(Map<String, Object> parsedJson) {
        this.parsedJson = parsedJson == null ? null : new LinkedHashMap<>(parsedJson);
    }

    public String getRefusal() {
        return refusal;
    }

    public void setRefusal(String refusal) {
        this.refusal = refusal;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public AiUsage getUsage() {
        return usage;
    }

    public void setUsage(AiUsage usage) {
        this.usage = usage;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }
}

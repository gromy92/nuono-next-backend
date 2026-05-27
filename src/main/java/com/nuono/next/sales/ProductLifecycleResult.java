package com.nuono.next.sales;

import java.util.List;

public class ProductLifecycleResult {

    public static final String DEFAULT_RULE_VERSION = "DEFAULT_V1";

    private final String code;
    private final String label;
    private final String explanation;
    private final String ruleVersion;
    private final List<String> warningCodes;
    private final String qualityState;
    private final String evidenceJson;

    public ProductLifecycleResult(String code, String label, String explanation, List<String> warningCodes) {
        this(code, label, explanation, DEFAULT_RULE_VERSION, warningCodes);
    }

    public ProductLifecycleResult(
            String code,
            String label,
            String explanation,
            String ruleVersion,
            List<String> warningCodes
    ) {
        this(code, label, explanation, ruleVersion, warningCodes, "ready", null);
    }

    public ProductLifecycleResult(
            String code,
            String label,
            String explanation,
            String ruleVersion,
            List<String> warningCodes,
            String qualityState,
            String evidenceJson
    ) {
        this.code = code;
        this.label = label;
        this.explanation = explanation;
        this.ruleVersion = ruleVersion == null || ruleVersion.isBlank() ? DEFAULT_RULE_VERSION : ruleVersion;
        this.warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        this.qualityState = qualityState == null || qualityState.isBlank() ? "ready" : qualityState;
        this.evidenceJson = evidenceJson;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public List<String> getWarningCodes() {
        return warningCodes;
    }

    public String getQualityState() {
        return qualityState;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}

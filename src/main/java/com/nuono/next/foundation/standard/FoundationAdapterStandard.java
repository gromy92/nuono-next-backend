package com.nuono.next.foundation.standard;

import java.util.List;

public class FoundationAdapterStandard {

    private final List<String> outcomeKeys;
    private final List<String> errorCodes;
    private final List<String> diagnosticFields;
    private final List<String> callBoundaryRules;
    private final String referenceIntegration;

    public FoundationAdapterStandard(
            List<String> outcomeKeys,
            List<String> errorCodes,
            List<String> diagnosticFields,
            List<String> callBoundaryRules,
            String referenceIntegration
    ) {
        this.outcomeKeys = List.copyOf(outcomeKeys);
        this.errorCodes = List.copyOf(errorCodes);
        this.diagnosticFields = List.copyOf(diagnosticFields);
        this.callBoundaryRules = List.copyOf(callBoundaryRules);
        this.referenceIntegration = referenceIntegration;
    }

    public List<String> getOutcomeKeys() {
        return outcomeKeys;
    }

    public List<String> getErrorCodes() {
        return errorCodes;
    }

    public List<String> getDiagnosticFields() {
        return diagnosticFields;
    }

    public List<String> getCallBoundaryRules() {
        return callBoundaryRules;
    }

    public String getReferenceIntegration() {
        return referenceIntegration;
    }

    public boolean isSuccessfulOutcome(String value) {
        return "success".equals(value);
    }
}

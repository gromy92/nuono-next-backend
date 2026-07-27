package com.nuono.next.productlisting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingWorkflowEvidence {

    private static final String READ_BACK_STEP = "verify_noon_readback";
    private static final String CREATE_REFERENCE_STEP =
            "resolve_create_reference";
    private static final String AUTH_RECOVERY_STEP =
            "authorization_recovery";

    private ProductListingWorkflowEvidence() {
    }

    static boolean hasFailedWriteStep(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return false;
        }
        Map<String, Boolean> latestFailures = new LinkedHashMap<>();
        int unnamedIndex = 0;
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            if (step == null || !isDecisive(step.getStatus())) {
                continue;
            }
            String stepKey = normalize(step.getStepKey());
            if (READ_BACK_STEP.equals(stepKey)) {
                continue;
            }
            if (CREATE_REFERENCE_STEP.equals(stepKey)) {
                if ("succeeded".equalsIgnoreCase(step.getStatus())
                        && hasCompleteCreateReference(
                        step.getExternalReference())) {
                    latestFailures.put("create_product", false);
                }
                continue;
            }
            if (AUTH_RECOVERY_STEP.equals(stepKey)) {
                continue;
            }
            if (!StringUtils.hasText(stepKey)) {
                stepKey = "__unnamed_" + unnamedIndex++;
            }
            latestFailures.put(stepKey, "failed".equalsIgnoreCase(step.getStatus()));
        }
        return latestFailures.values().stream().anyMatch(Boolean.TRUE::equals);
    }

    static boolean hasConfirmedCreate(ProductListingNoonWriteResult result) {
        boolean confirmed = false;
        if (result == null || result.getSteps() == null) {
            return false;
        }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            String stepKey = normalize(step == null ? null : step.getStepKey());
            if ("create_product".equals(stepKey) && isDecisive(step.getStatus())) {
                confirmed = "succeeded".equalsIgnoreCase(step.getStatus())
                        && hasCompleteCreateReference(step.getExternalReference());
            } else if ("resolve_create_reference".equals(stepKey)
                    && "succeeded".equalsIgnoreCase(step.getStatus())
                    && hasCompleteCreateReference(step.getExternalReference())) {
                confirmed = true;
            }
        }
        return confirmed;
    }

    static boolean hasUnresolvedCreateOutcome(ProductListingNoonWriteResult result) {
        boolean unknown = false;
        if (result == null || result.getSteps() == null) {
            return false;
        }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            String stepKey = normalize(step == null ? null : step.getStepKey());
            if ("create_product".equals(stepKey) && isDecisive(step.getStatus())) {
                unknown = "failed".equalsIgnoreCase(step.getStatus())
                        && "noon_create_outcome_unknown".equalsIgnoreCase(
                                step.getFailureCode());
            } else if ("resolve_create_reference".equals(stepKey)
                    && "succeeded".equalsIgnoreCase(step.getStatus())
                    && hasCompleteCreateReference(step.getExternalReference())) {
                unknown = false;
            }
        }
        return unknown;
    }

    private static boolean hasCompleteCreateReference(String reference) {
        if (!StringUtils.hasText(reference)) {
            return false;
        }
        boolean skuParent = false;
        boolean pskuCode = false;
        for (String token : reference.split(";")) {
            String normalized = normalize(token);
            skuParent = skuParent || normalized.matches("skuparent=.+");
            pskuCode = pskuCode || normalized.matches("pskucode=.+");
        }
        return skuParent && pskuCode;
    }

    private static boolean isDecisive(String status) {
        return "succeeded".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

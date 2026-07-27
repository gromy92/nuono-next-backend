package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductListingNoonWriteResult {

    private boolean success;
    private String failureCategory;
    private String failureCode;
    private String failureMessage;
    private Long recoveryId;
    private Boolean writeMayHaveOccurred;
    private List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();

    public static ProductListingNoonWriteResult succeeded(List<ProductListingNoonWriteStepResult> steps) {
        ProductListingNoonWriteResult result = new ProductListingNoonWriteResult();
        result.setSuccess(true);
        result.setSteps(steps);
        return result;
    }

    public static ProductListingNoonWriteResult failed(
            String failureCategory,
            String failureCode,
            String failureMessage,
            List<ProductListingNoonWriteStepResult> steps
    ) {
        ProductListingNoonWriteResult result = new ProductListingNoonWriteResult();
        result.setSuccess(false);
        result.setFailureCategory(failureCategory);
        result.setFailureCode(failureCode);
        result.setFailureMessage(failureMessage);
        result.setSteps(steps);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getFailureCategory() {
        return failureCategory;
    }

    public void setFailureCategory(String failureCategory) {
        this.failureCategory = failureCategory;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Long getRecoveryId() {
        if (recoveryId != null || steps == null) {
            return recoveryId;
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            ProductListingNoonWriteStepResult step = steps.get(index);
            if (step != null && step.getRecoveryId() != null) {
                return step.getRecoveryId();
            }
        }
        return null;
    }

    public void setRecoveryId(Long recoveryId) {
        this.recoveryId = recoveryId;
    }

    public Boolean getWriteMayHaveOccurred() {
        boolean observed = writeMayHaveOccurred != null;
        boolean mayHaveOccurred = Boolean.TRUE.equals(writeMayHaveOccurred);
        if (steps != null) {
            for (ProductListingNoonWriteStepResult step : steps) {
                if (step != null && step.getWriteMayHaveOccurred() != null) {
                    observed = true;
                    mayHaveOccurred |= Boolean.TRUE.equals(step.getWriteMayHaveOccurred());
                }
            }
        }
        return observed ? mayHaveOccurred : null;
    }

    public void setWriteMayHaveOccurred(Boolean writeMayHaveOccurred) {
        this.writeMayHaveOccurred = writeMayHaveOccurred;
    }

    ProductListingNoonWriteResult withPriorWriteCompleted() {
        this.writeMayHaveOccurred = true;
        return this;
    }

    boolean hasUnresolvedWriteFailure() {
        Map<String, ProductListingNoonWriteStepResult> latestByStepKey = new LinkedHashMap<>();
        int anonymousStep = 0;
        for (ProductListingNoonWriteStepResult step : steps) {
            if (step == null) {
                continue;
            }
            String stepKey = step.getStepKey();
            latestByStepKey.put(hasText(stepKey) ? stepKey : "#" + anonymousStep++, step);
        }
        ProductListingNoonWriteStepResult recoveredCreate = latestByStepKey.get("resolve_create_reference");
        for (Map.Entry<String, ProductListingNoonWriteStepResult> entry : latestByStepKey.entrySet()) {
            ProductListingNoonWriteStepResult step = entry.getValue();
            if (!"failed".equals(step.getStatus()) || isControlStep(entry.getKey())) {
                continue;
            }
            if ("create_product".equals(entry.getKey())
                    && recoveredCreate != null
                    && "succeeded".equals(recoveredCreate.getStatus())
                    && hasText(recoveredCreate.getExternalReference())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isControlStep(String stepKey) {
        return "verify_noon_readback".equals(stepKey)
                || "resolve_create_reference".equals(stepKey)
                || "authorization_recovery".equals(stepKey);
    }

    public List<ProductListingNoonWriteStepResult> getSteps() {
        return steps;
    }

    public void setSteps(List<ProductListingNoonWriteStepResult> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }
}

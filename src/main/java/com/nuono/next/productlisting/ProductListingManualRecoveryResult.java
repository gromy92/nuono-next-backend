package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class ProductListingManualRecoveryResult {

    private ProductListingManualRecoveryResult() {
    }

    static ProductListingNoonWriteResult fromException(
            ProductListingNoonWriteResult previous,
            String stepKey,
            RuntimeException failure
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(stepKey);
        step.setStatus("failed");
        step.setFailureCode(ProductListingNoonWriteRequest.isExecutionLeaseLost(failure)
                ? "listing_execution_lease_lost"
                : "manual_recovery_failed");
        step.setFailureMessage(failure != null && StringUtils.hasText(failure.getMessage())
                ? failure.getMessage()
                : "Product listing manual recovery failed.");
        return fromStep(previous, step);
    }

    static ProductListingNoonWriteResult fromStep(
            ProductListingNoonWriteResult previous,
            ProductListingNoonWriteStepResult failureStep
    ) {
        ProductListingNoonWriteStepResult step = failureStep == null
                ? missingFailureStep()
                : failureStep;
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previous != null && previous.getSteps() != null) {
            steps.addAll(previous.getSteps());
        }
        steps.add(step);
        String failureCode = StringUtils.hasText(step.getFailureCode())
                ? step.getFailureCode()
                : "manual_recovery_failed";
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                ProductListingWriteAuthRecovery.FAILURE_CODE.equals(failureCode)
                        ? "authorization"
                        : "recovery",
                failureCode,
                StringUtils.hasText(step.getFailureMessage())
                        ? step.getFailureMessage()
                        : "Product listing manual recovery failed.",
                steps
        );
        result.setRecoveryId(step.getRecoveryId() != null
                ? step.getRecoveryId()
                : previous == null ? null : previous.getRecoveryId());
        return result.withPriorWriteCompleted();
    }

    private static ProductListingNoonWriteStepResult missingFailureStep() {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("manual_recovery");
        step.setStatus("failed");
        step.setFailureCode("manual_recovery_failed");
        step.setFailureMessage("Product listing manual recovery returned no result.");
        return step;
    }
}

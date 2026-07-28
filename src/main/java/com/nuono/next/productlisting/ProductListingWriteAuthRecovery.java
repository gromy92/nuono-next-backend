package com.nuono.next.productlisting;

import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class ProductListingWriteAuthRecovery {
    public static final String FAILURE_CODE = "noon_auth_required";

    private final ProductWriteAuthRecovery authRecovery;

    public ProductListingWriteAuthRecovery(ProductWriteAuthRecovery authRecovery) {
        this.authRecovery = authRecovery == null ? ProductWriteAuthRecovery.disabled() : authRecovery;
    }

    static ProductListingWriteAuthRecovery disabled() {
        return new ProductListingWriteAuthRecovery(ProductWriteAuthRecovery.disabled());
    }

    void requireAvailable(ProductListingNoonWriteRequest request, NoonPullStoreBinding binding) {
        authRecovery.requireAvailable(
                request == null ? null : request.getOwnerUserId(),
                binding == null ? null : binding.getProjectCode(),
                storeCode(request, binding)
        );
    }

    ProductListingNoonWriteResult mapFailure(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            RuntimeException failure,
            List<ProductListingNoonWriteStepResult> steps,
            String fallbackMessage
    ) {
        boolean writeMayHaveOccurred = hasWriteAttempt(steps);
        ProductWriteAuthRequiredException authRequired = authRecovery.suspendIfAuthFailure(
                request == null ? null : request.getOwnerUserId(),
                binding == null ? null : binding.getProjectCode(),
                storeCode(request, binding),
                failure,
                writeMayHaveOccurred
        );
        if (authRequired == null) {
            return ProductListingNoonWriteResult.failed(
                    "noon_api",
                    failedStepCode(steps, "noon_write_failed"),
                    message(failure, fallbackMessage),
                    steps
            );
        }
        String recoveryMessage = listingRecoveryMessage(authRequired);
        markAuthRecoveryStep(steps, authRequired, recoveryMessage);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "authorization",
                FAILURE_CODE,
                recoveryMessage,
                steps
        );
        result.setRecoveryId(authRequired.getRecoveryId());
        result.setWriteMayHaveOccurred(authRequired.isWriteMayHaveOccurred());
        return result;
    }

    ProductListingNoonWriteStepResult mapReadFailure(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            RuntimeException failure,
            boolean writeMayHaveOccurred,
            String stepKey,
            String fallbackCode,
            String fallbackMessage
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(stepKey);
        step.setStatus("failed");
        ProductWriteAuthRequiredException authRequired = authRecovery.suspendIfAuthFailure(
                request == null ? null : request.getOwnerUserId(),
                binding == null ? null : binding.getProjectCode(),
                storeCode(request, binding),
                failure,
                writeMayHaveOccurred
        );
        if (authRequired == null) {
            step.setFailureCode(fallbackCode);
            step.setFailureMessage(message(failure, fallbackMessage));
            return step;
        }
        step.setFailureCode(FAILURE_CODE);
        step.setFailureMessage(authRequired.getMessage());
        step.setRecoveryId(authRequired.getRecoveryId());
        step.setWriteMayHaveOccurred(authRequired.isWriteMayHaveOccurred());
        return step;
    }

    boolean isExplicitAuthFailure(Throwable failure) {
        return ProductWriteAuthRequiredException.find(failure) != null
                || authRecovery.isExplicitAuthFailure(failure);
    }

    boolean shouldStopImmediately(Throwable failure) {
        return isExplicitAuthFailure(failure)
                || ProductListingNoonWriteRequest.isExecutionLeaseLost(failure);
    }

    void markWriteFailure(
            ProductListingNoonWriteStepResult step,
            String stepKey,
            RuntimeException failure
    ) {
        ProductWriteAuthRequiredException authRequired =
                ProductWriteAuthRequiredException.find(failure);
        boolean authRejected = authRequired != null || isExplicitAuthFailure(failure);
        boolean leaseLost = ProductListingNoonWriteRequest.isExecutionLeaseLost(failure);
        step.setStatus("failed");
        step.setFailureCode(authRejected
                ? "noon_auth_rejected"
                : leaseLost
                ? "listing_execution_lease_lost"
                : "create_product".equals(stepKey)
                ? "noon_create_outcome_unknown"
                : "noon_write_outcome_unknown");
        step.setFailureMessage(failure.getMessage());
        step.setWriteMayHaveOccurred(authRequired != null
                ? authRequired.isWriteMayHaveOccurred()
                : !authRejected && !leaseLost);
    }

    void markUploadFailure(
            ProductListingNoonWriteStepResult step,
            RuntimeException failure,
            boolean writeMayHaveOccurred
    ) {
        step.setStatus("failed");
        step.setFailureCode("noon_image_upload_failed");
        step.setFailureMessage(message(failure, "Noon image upload failed."));
        step.setWriteMayHaveOccurred(writeMayHaveOccurred);
    }

    private boolean hasWriteAttempt(List<ProductListingNoonWriteStepResult> steps) {
        return steps != null && steps.stream().anyMatch(this::stepMayHaveWritten);
    }

    private boolean stepMayHaveWritten(ProductListingNoonWriteStepResult step) {
        if (step == null) {
            return false;
        }
        if (step.getWriteMayHaveOccurred() != null) {
            return Boolean.TRUE.equals(step.getWriteMayHaveOccurred());
        }
        return "succeeded".equals(step.getStatus())
                || isOutcomeUnknown(step.getFailureCode());
    }

    private boolean isOutcomeUnknown(String failureCode) {
        return StringUtils.hasText(failureCode)
                && failureCode.toLowerCase(Locale.ROOT).contains("outcome_unknown");
    }

    private void markAuthRecoveryStep(
            List<ProductListingNoonWriteStepResult> steps,
            ProductWriteAuthRequiredException authRequired,
            String recoveryMessage
    ) {
        if (steps == null) {
            return;
        }
        ProductListingNoonWriteStepResult step = null;
        for (int index = steps.size() - 1; index >= 0; index--) {
            ProductListingNoonWriteStepResult candidate = steps.get(index);
            if (candidate != null && "failed".equals(candidate.getStatus())) {
                step = candidate;
                break;
            }
        }
        if (step == null) {
            step = new ProductListingNoonWriteStepResult();
            step.setStepKey("authorization_recovery");
            step.setStatus("failed");
            steps.add(step);
        }
        step.setFailureCode(FAILURE_CODE);
        step.setFailureMessage(recoveryMessage);
        step.setRecoveryId(authRequired.getRecoveryId());
        step.setWriteMayHaveOccurred(authRequired.isWriteMayHaveOccurred());
    }

    private String listingRecoveryMessage(ProductWriteAuthRequiredException authRequired) {
        if (authRequired.isWriteMayHaveOccurred()) {
            return authRequired.getMessage();
        }
        return "Noon Project 授权恢复中。恢复成功后只会恢复当前尚未开始写入的上架任务，"
                + "不会创建第二个任务。";
    }

    private String failedStepCode(List<ProductListingNoonWriteStepResult> steps, String fallback) {
        if (steps != null) {
            for (int index = steps.size() - 1; index >= 0; index--) {
                ProductListingNoonWriteStepResult step = steps.get(index);
                if (step != null && "failed".equals(step.getStatus()) && StringUtils.hasText(step.getFailureCode())) {
                    return step.getFailureCode();
                }
            }
        }
        return fallback;
    }

    private String storeCode(ProductListingNoonWriteRequest request, NoonPullStoreBinding binding) {
        if (binding != null && StringUtils.hasText(binding.getStoreCode())) {
            return binding.getStoreCode();
        }
        return request == null ? null : request.getStoreCode();
    }

    private String message(RuntimeException failure, String fallback) {
        return failure != null && StringUtils.hasText(failure.getMessage())
                ? failure.getMessage()
                : fallback;
    }

}

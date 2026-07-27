package com.nuono.next.productlisting;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import java.util.List;
import org.springframework.util.StringUtils;

final class ProductListingNoonWriteFailureSupport {

    ProductListingNoonWriteResult unsupportedWarehouseStockResult(
            ProductListingDraftCommand draft
    ) {
        if (draft == null
                || (draft.getFbp() == null
                && !StringUtils.hasText(draft.getWarehouseId())
                && !StringUtils.hasText(draft.getWarehouseCode())
                && draft.getQuantity() == null)) {
            return null;
        }
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("pre_create");
        step.setStatus("failed");
        step.setFailureCode("noon_warehouse_stock_not_supported");
        step.setFailureMessage(
                "当前上架流程不会写入 Noon FBP、仓库或库存数量；"
                        + "请清空这些字段后重新检查。");
        return ProductListingNoonWriteResult.failed(
                "validation", step.getFailureCode(),
                step.getFailureMessage(), List.of(step));
    }

    ProductListingNoonWriteResult preCreateFailure(RuntimeException exception) {
        boolean authenticationFailure =
                NoonAuthenticationFailureClassifier.isAuthenticationFailure(exception);
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("pre_create");
        step.setStatus("failed");
        step.setFailureCode(authenticationFailure
                ? "noon_auth_required" : "noon_pre_create_failed");
        step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : "Noon create preflight failed before any write started.");
        return ProductListingNoonWriteResult.failed(
                authenticationFailure ? "authentication" : "noon_pre_create",
                step.getFailureCode(), step.getFailureMessage(), List.of(step));
    }

    String failedStepCode(
            List<ProductListingNoonWriteStepResult> steps,
            String fallback
    ) {
        if (steps != null) {
            for (int index = steps.size() - 1; index >= 0; index--) {
                ProductListingNoonWriteStepResult step = steps.get(index);
                if (step != null && "failed".equals(step.getStatus())
                        && StringUtils.hasText(step.getFailureCode())) {
                    return step.getFailureCode();
                }
            }
        }
        return fallback;
    }

    ProductListingNoonWriteStepResult continuationPreflightFailure(
            String skuParent,
            String pskuCode,
            boolean authenticationFailure,
            String failureMessage
    ) {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("continue_after_create_preflight");
        step.setStatus("failed");
        step.setExternalReference(externalReference(skuParent, pskuCode));
        step.setFailureCode(authenticationFailure
                ? "noon_auth_required" : "noon_write_continuation_failed");
        step.setFailureMessage(StringUtils.hasText(failureMessage)
                ? failureMessage
                : "Product listing Noon write continuation preflight failed.");
        return step;
    }

    void markLastFailedStepAuthenticationRequired(
            List<ProductListingNoonWriteStepResult> steps,
            String failureMessage
    ) {
        if (steps == null) {
            return;
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            ProductListingNoonWriteStepResult step = steps.get(index);
            if (step == null || !"failed".equalsIgnoreCase(step.getStatus())) {
                continue;
            }
            if ("create_product".equalsIgnoreCase(step.getStepKey())) {
                if (!"noon_auth_required".equalsIgnoreCase(step.getFailureCode())) {
                    step.setFailureCode("noon_create_outcome_unknown");
                }
            } else {
                step.setFailureCode("noon_auth_required");
            }
            if (StringUtils.hasText(failureMessage)) {
                step.setFailureMessage(failureMessage);
            }
            return;
        }
    }

    private String externalReference(String skuParent, String pskuCode) {
        return "skuParent=" + normalize(skuParent)
                + ";pskuCode=" + normalize(pskuCode);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}

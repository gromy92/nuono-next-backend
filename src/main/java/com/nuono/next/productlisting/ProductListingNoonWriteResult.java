package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingNoonWriteResult {

    private boolean success;
    private String failureCategory;
    private String failureCode;
    private String failureMessage;
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

    public List<ProductListingNoonWriteStepResult> getSteps() {
        return steps;
    }

    public void setSteps(List<ProductListingNoonWriteStepResult> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }
}

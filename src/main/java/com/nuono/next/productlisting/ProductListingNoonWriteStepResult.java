package com.nuono.next.productlisting;

public class ProductListingNoonWriteStepResult {

    private String stepKey;
    private String status;
    private String externalReference;
    private String failureCode;
    private String failureMessage;
    private Long recoveryId;
    private Boolean writeMayHaveOccurred;
    private transient RuntimeException originalFailure;

    public String getStepKey() {
        return stepKey;
    }

    public void setStepKey(String stepKey) {
        this.stepKey = stepKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
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
        return recoveryId;
    }

    public void setRecoveryId(Long recoveryId) {
        this.recoveryId = recoveryId;
    }

    public Boolean getWriteMayHaveOccurred() {
        return writeMayHaveOccurred;
    }

    public void setWriteMayHaveOccurred(Boolean writeMayHaveOccurred) {
        this.writeMayHaveOccurred = writeMayHaveOccurred;
    }

    RuntimeException originalFailure() {
        return originalFailure;
    }

    void preserveOriginalFailure(RuntimeException originalFailure) {
        this.originalFailure = originalFailure;
    }
}

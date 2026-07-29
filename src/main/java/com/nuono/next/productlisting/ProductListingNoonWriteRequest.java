package com.nuono.next.productlisting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProductListingNoonWriteRequest {

    private Long ownerUserId;
    private String storeCode;
    private Long draftId;
    private Long dryRunTaskId;
    private Long realRunTaskId;
    private Long submittedBy;
    private ProductListingDraftCommand draft;
    private List<ProductListingValidationIssue> validationIssues = new ArrayList<>();
    private ProductListingRealRunCommand confirmation;
    @JsonIgnore
    private transient Runnable executionLeaseHeartbeat;
    @JsonIgnore
    private transient Consumer<ProductListingNoonWriteResult>
            noonResultCheckpoint;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public Long getDryRunTaskId() {
        return dryRunTaskId;
    }

    public void setDryRunTaskId(Long dryRunTaskId) {
        this.dryRunTaskId = dryRunTaskId;
    }

    public Long getRealRunTaskId() {
        return realRunTaskId;
    }

    public void setRealRunTaskId(Long realRunTaskId) {
        this.realRunTaskId = realRunTaskId;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
    }

    public ProductListingDraftCommand getDraft() {
        return draft;
    }

    public void setDraft(ProductListingDraftCommand draft) {
        this.draft = draft;
    }

    public List<ProductListingValidationIssue> getValidationIssues() {
        return validationIssues;
    }

    public void setValidationIssues(List<ProductListingValidationIssue> validationIssues) {
        this.validationIssues = validationIssues == null ? new ArrayList<>() : validationIssues;
    }

    public ProductListingRealRunCommand getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(ProductListingRealRunCommand confirmation) {
        this.confirmation = confirmation;
    }

    @JsonIgnore
    public void setExecutionLeaseHeartbeat(Runnable executionLeaseHeartbeat) {
        this.executionLeaseHeartbeat = executionLeaseHeartbeat;
    }

    @JsonIgnore
    public void setNoonResultCheckpoint(
            Consumer<ProductListingNoonWriteResult> noonResultCheckpoint
    ) {
        this.noonResultCheckpoint = noonResultCheckpoint;
    }

    @JsonIgnore
    public void checkpointNoonResultOrThrow(
            ProductListingNoonWriteResult result
    ) {
        if (noonResultCheckpoint == null) {
            return;
        }
        try {
            noonResultCheckpoint.accept(result);
        } catch (ExecutionLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExecutionLeaseLostException(
                    "Product listing Noon result checkpoint failed.",
                    exception
            );
        }
    }

    @JsonIgnore
    public void heartbeatOrThrow() {
        if (executionLeaseHeartbeat == null) {
            return;
        }
        try {
            executionLeaseHeartbeat.run();
        } catch (ExecutionLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExecutionLeaseLostException(
                    "Product listing execution lease lost.",
                    exception
            );
        }
    }

    static boolean isExecutionLeaseLost(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ExecutionLeaseLostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class ExecutionLeaseLostException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private ExecutionLeaseLostException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}

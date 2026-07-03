package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

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
}

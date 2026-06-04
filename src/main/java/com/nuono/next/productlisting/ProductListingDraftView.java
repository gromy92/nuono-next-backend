package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingDraftView {

    private Long draftId;
    private String draftNo;
    private Long ownerUserId;
    private String storeCode;
    private String status;
    private ProductListingDraftCommand draft;
    private List<ProductListingValidationIssue> validationIssues = new ArrayList<>();

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public String getDraftNo() {
        return draftNo;
    }

    public void setDraftNo(String draftNo) {
        this.draftNo = draftNo;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}

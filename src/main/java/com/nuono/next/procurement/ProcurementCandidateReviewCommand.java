package com.nuono.next.procurement;

public class ProcurementCandidateReviewCommand {

    private Long ownerUserId;

    private String orderNo;

    private Long demandItemId;

    private Long candidateId;

    private String manualReviewNote;

    private String inquirySummary;

    private String nextAction;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getDemandItemId() {
        return demandItemId;
    }

    public void setDemandItemId(Long demandItemId) {
        this.demandItemId = demandItemId;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public String getManualReviewNote() {
        return manualReviewNote;
    }

    public void setManualReviewNote(String manualReviewNote) {
        this.manualReviewNote = manualReviewNote;
    }

    public String getInquirySummary() {
        return inquirySummary;
    }

    public void setInquirySummary(String inquirySummary) {
        this.inquirySummary = inquirySummary;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }
}

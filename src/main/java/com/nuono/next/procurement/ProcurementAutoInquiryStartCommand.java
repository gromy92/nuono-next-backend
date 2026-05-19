package com.nuono.next.procurement;

public class ProcurementAutoInquiryStartCommand {

    private Long ownerUserId;

    private Long demandItemId;

    private Long candidateId;

    private Long operatorUserId;

    private String orderNo;

    private Boolean triggerDispatch;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Boolean getTriggerDispatch() {
        return triggerDispatch;
    }

    public void setTriggerDispatch(Boolean triggerDispatch) {
        this.triggerDispatch = triggerDispatch;
    }
}

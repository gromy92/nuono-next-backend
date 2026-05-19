package com.nuono.next.procurement;

public class ProcurementDecisionCommand {

    private Long ownerUserId;

    private String orderNo;

    private Long demandItemId;

    private Long candidateId;

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
}

package com.nuono.next.preorderprofit;

public class PreOrderProfitPurchaseOrderLinkView {
    private Long itemId;
    private Long purchaseOrderId;
    private Long candidateId;
    private boolean alreadyLinked;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public boolean isAlreadyLinked() {
        return alreadyLinked;
    }

    public void setAlreadyLinked(boolean alreadyLinked) {
        this.alreadyLinked = alreadyLinked;
    }
}

package com.nuono.next.preorderprofit;

public class PreOrderProfitCompetitorView extends PreOrderProfitCompetitorCommand {
    private Long id;
    private Long candidateId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
}

package com.nuono.next.procurement.aliorder.datapull;

/** The single durable cleanup marker allowed for one DP-10 task. */
public class Ali1688Dp10StageCleanupMarker {
    private Long generationNo;
    private Ali1688Dp10StageCleanupReason reason;
    private Long fenceEpoch;

    public Long getGenerationNo() { return generationNo; }
    public void setGenerationNo(Long generationNo) { this.generationNo = generationNo; }
    public Ali1688Dp10StageCleanupReason getReason() { return reason; }
    public void setReason(Ali1688Dp10StageCleanupReason reason) { this.reason = reason; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
}

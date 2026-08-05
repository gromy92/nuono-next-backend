package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;

/** Epoch/pass identity attached to one bounded member-stage insert. */
public class Dp08MemberStageItem extends Dp08MemberSetItem {
    private OperationCode operationCode;
    private Long epochNo;
    private Integer scanPass;
    private String scopeKey;

    static Dp08MemberStageItem from(
            OperationCode operation,
            long epochNo,
            int scanPass,
            String scopeKey,
            Dp08MemberSetItem source
    ) {
        Dp08MemberStageItem item=new Dp08MemberStageItem();
        item.operationCode=operation;item.epochNo=epochNo;item.scanPass=scanPass;item.scopeKey=scopeKey;
        item.setMemberKey(source.getMemberKey());item.setMemberKind(source.getMemberKind());
        item.setWatchProductId(source.getWatchProductId());item.setCompetitorProductId(source.getCompetitorProductId());
        item.setNoonProductCode(source.getNoonProductCode());item.setSourceUpdatedAtUtc(source.getSourceUpdatedAtUtc());
        return item;
    }
    public OperationCode getOperationCode(){return operationCode;} public void setOperationCode(OperationCode v){operationCode=v;}
    public Long getEpochNo(){return epochNo;} public void setEpochNo(Long v){epochNo=v;}
    public Integer getScanPass(){return scanPass;} public void setScanPass(Integer v){scanPass=v;}
    public String getScopeKey(){return scopeKey;} public void setScopeKey(String v){scopeKey=v;}
}

package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Restart-safe header for one logical scope whose nested members span source steps. */
public class Dp08MemberStageHead {
    private OperationCode operationCode;
    private Long epochNo;
    private Integer scanPass;
    private String scopeKey;
    private String sourceCursor;
    private Long memberCount;
    private String memberOrderedSha256;
    private String basePayload;
    private LocalDateTime effectiveFromUtc;
    private String stageState;
    private String memberSetId;
    private Long version;

    public OperationCode getOperationCode(){return operationCode;} public void setOperationCode(OperationCode v){operationCode=v;}
    public Long getEpochNo(){return epochNo;} public void setEpochNo(Long v){epochNo=v;}
    public Integer getScanPass(){return scanPass;} public void setScanPass(Integer v){scanPass=v;}
    public String getScopeKey(){return scopeKey;} public void setScopeKey(String v){scopeKey=v;}
    public String getSourceCursor(){return sourceCursor;} public void setSourceCursor(String v){sourceCursor=v;}
    public Long getMemberCount(){return memberCount;} public void setMemberCount(Long v){memberCount=v;}
    public String getMemberOrderedSha256(){return memberOrderedSha256;} public void setMemberOrderedSha256(String v){memberOrderedSha256=v;}
    public String getBasePayload(){return basePayload;} public void setBasePayload(String v){basePayload=v;}
    public LocalDateTime getEffectiveFromUtc(){return effectiveFromUtc;} public void setEffectiveFromUtc(LocalDateTime v){effectiveFromUtc=v;}
    public String getStageState(){return stageState;} public void setStageState(String v){stageState=v;}
    public String getMemberSetId(){return memberSetId;} public void setMemberSetId(String v){memberSetId=v;}
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
}

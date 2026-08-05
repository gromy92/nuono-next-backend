package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Persistent content-addressed member-set header. */
public class Dp08MemberSetRecord {
    private String memberSetId;
    private OperationCode operationCode;
    private String scopeKey;
    private Long memberCount;
    private String memberOrderedSha256;
    private String handlePayloadType;
    private String handlePayloadSha256;
    private String handlePayload;
    private LocalDateTime effectiveFromUtc;
    private String setState;
    private String copyCursor;
    private Long copiedMemberCount;
    private Long version;
    public String getMemberSetId(){return memberSetId;} public void setMemberSetId(String v){memberSetId=v;}
    public OperationCode getOperationCode(){return operationCode;} public void setOperationCode(OperationCode v){operationCode=v;}
    public String getScopeKey(){return scopeKey;} public void setScopeKey(String v){scopeKey=v;}
    public Long getMemberCount(){return memberCount;} public void setMemberCount(Long v){memberCount=v;}
    public String getMemberOrderedSha256(){return memberOrderedSha256;} public void setMemberOrderedSha256(String v){memberOrderedSha256=v;}
    public String getHandlePayloadType(){return handlePayloadType;} public void setHandlePayloadType(String v){handlePayloadType=v;}
    public String getHandlePayloadSha256(){return handlePayloadSha256;} public void setHandlePayloadSha256(String v){handlePayloadSha256=v;}
    public String getHandlePayload(){return handlePayload;} public void setHandlePayload(String v){handlePayload=v;}
    public LocalDateTime getEffectiveFromUtc(){return effectiveFromUtc;} public void setEffectiveFromUtc(LocalDateTime v){effectiveFromUtc=v;}
    public String getSetState(){return setState;} public void setSetState(String v){setState=v;}
    public String getCopyCursor(){return copyCursor;} public void setCopyCursor(String v){copyCursor=v;}
    public Long getCopiedMemberCount(){return copiedMemberCount;} public void setCopiedMemberCount(Long v){copiedMemberCount=v;}
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
}

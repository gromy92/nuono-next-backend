package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;

/** Transactional cursors for evidence evaluation and member-batched fact application. */
public class Dp08TaskMemberProgress {
    private Long taskId;
    private OperationCode operationCode;
    private String memberSetId;
    private String evidenceCursor;
    private Long evidenceMemberCount;
    private Boolean evidenceComplete;
    private Boolean exactSearchRequired;
    private String applyCursor;
    private Long appliedMemberCount;
    private Boolean applyComplete;
    private Long searchRunId;
    private Long keywordRunId;
    private Integer rankFactCount;
    private Long version;
    public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;}
    public OperationCode getOperationCode(){return operationCode;} public void setOperationCode(OperationCode v){operationCode=v;}
    public String getMemberSetId(){return memberSetId;} public void setMemberSetId(String v){memberSetId=v;}
    public String getEvidenceCursor(){return evidenceCursor;} public void setEvidenceCursor(String v){evidenceCursor=v;}
    public Long getEvidenceMemberCount(){return evidenceMemberCount;} public void setEvidenceMemberCount(Long v){evidenceMemberCount=v;}
    public Boolean getEvidenceComplete(){return evidenceComplete;} public void setEvidenceComplete(Boolean v){evidenceComplete=v;}
    public Boolean getExactSearchRequired(){return exactSearchRequired;} public void setExactSearchRequired(Boolean v){exactSearchRequired=v;}
    public String getApplyCursor(){return applyCursor;} public void setApplyCursor(String v){applyCursor=v;}
    public Long getAppliedMemberCount(){return appliedMemberCount;} public void setAppliedMemberCount(Long v){appliedMemberCount=v;}
    public Boolean getApplyComplete(){return applyComplete;} public void setApplyComplete(Boolean v){applyComplete=v;}
    public Long getSearchRunId(){return searchRunId;} public void setSearchRunId(Long v){searchRunId=v;}
    public Long getKeywordRunId(){return keywordRunId;} public void setKeywordRunId(Long v){keywordRunId=v;}
    public Integer getRankFactCount(){return rankFactCount;} public void setRankFactCount(Integer v){rankFactCount=v;}
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
}

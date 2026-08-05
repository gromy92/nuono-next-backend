package com.nuono.next.procurement.aliorder.datapull;

import java.time.LocalDateTime;

/** MyBatis row for one exact list position. */
public class Ali1688Dp10StageItemRow {
    private Long taskId;
    private Long generationNo;
    private Integer scanPass;
    private String partitionName;
    private Integer pageNo;
    private Integer itemOrdinal;
    private String providerOrderNo;
    private LocalDateTime providerModifiedAt;
    private String state;
    private String validationCode;
    private String listContentFingerprint;
    private String contentFingerprint;
    private String payload;
    private String verificationState;
    private String applyState;
    private Integer applyItemCursor;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getGenerationNo() { return generationNo; }
    public void setGenerationNo(Long value) { generationNo = value; }
    public Integer getScanPass() { return scanPass; }
    public void setScanPass(Integer value) { scanPass = value; }
    public String getPartitionName() { return partitionName; }
    public void setPartitionName(String value) { partitionName = value; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer value) { pageNo = value; }
    public Integer getItemOrdinal() { return itemOrdinal; }
    public void setItemOrdinal(Integer value) { itemOrdinal = value; }
    public String getProviderOrderNo() { return providerOrderNo; }
    public void setProviderOrderNo(String value) { providerOrderNo = value; }
    public LocalDateTime getProviderModifiedAt() { return providerModifiedAt; }
    public void setProviderModifiedAt(LocalDateTime value) { providerModifiedAt = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getValidationCode() { return validationCode; }
    public void setValidationCode(String value) { validationCode = value; }
    public String getListContentFingerprint() { return listContentFingerprint; }
    public void setListContentFingerprint(String value) { listContentFingerprint = value; }
    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String value) { contentFingerprint = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public String getVerificationState() { return verificationState; }
    public void setVerificationState(String value) { verificationState = value; }
    public String getApplyState() { return applyState; }
    public void setApplyState(String value) { applyState = value; }
    public Integer getApplyItemCursor() { return applyItemCursor; }
    public void setApplyItemCursor(Integer value) { applyItemCursor = value; }
}

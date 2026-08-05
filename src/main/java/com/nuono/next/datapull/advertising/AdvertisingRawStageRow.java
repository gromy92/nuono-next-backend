package com.nuono.next.datapull.advertising;

/** One raw stage row plus its page cardinality, selected in strict source order. */
public final class AdvertisingRawStageRow {
    private Long taskId;
    private Integer pageNo;
    private Integer itemOrdinal;
    private Integer pageItemCount;
    private String stableIdentity;
    private String contentFingerprint;
    private String payload;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer value) { pageNo = value; }
    public Integer getItemOrdinal() { return itemOrdinal; }
    public void setItemOrdinal(Integer value) { itemOrdinal = value; }
    public Integer getPageItemCount() { return pageItemCount; }
    public void setPageItemCount(Integer value) { pageItemCount = value; }
    public String getStableIdentity() { return stableIdentity; }
    public void setStableIdentity(String value) { stableIdentity = value; }
    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String value) { contentFingerprint = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
}

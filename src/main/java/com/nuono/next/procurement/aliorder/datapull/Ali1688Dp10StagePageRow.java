package com.nuono.next.procurement.aliorder.datapull;

/** MyBatis row for one exact DP-10 partition page. */
public class Ali1688Dp10StagePageRow {
    private Long taskId;
    private Long generationNo;
    private Integer scanPass;
    private String partitionName;
    private Integer pageNo;
    private Long activeFenceEpoch;
    private Integer pageSize;
    private Long totalRecord;
    private Integer expectedPages;
    private Integer rawRowCount;
    private String state;
    private String pageFingerprint;

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
    public Long getActiveFenceEpoch() { return activeFenceEpoch; }
    public void setActiveFenceEpoch(Long value) { activeFenceEpoch = value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { pageSize = value; }
    public Long getTotalRecord() { return totalRecord; }
    public void setTotalRecord(Long value) { totalRecord = value; }
    public Integer getExpectedPages() { return expectedPages; }
    public void setExpectedPages(Integer value) { expectedPages = value; }
    public Integer getRawRowCount() { return rawRowCount; }
    public void setRawRowCount(Integer value) { rawRowCount = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getPageFingerprint() { return pageFingerprint; }
    public void setPageFingerprint(String value) { pageFingerprint = value; }
}

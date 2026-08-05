package com.nuono.next.datapull.report;

/** Bounded aggregate used to reject missing, extra or non-contiguous artifact chunks. */
public final class ReportArtifactChunkAggregate {
    private long chunkCount;
    private long contentLength;
    private Integer maximumChunkNo;

    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long value) { chunkCount = value; }
    public long getContentLength() { return contentLength; }
    public void setContentLength(long value) { contentLength = value; }
    public Integer getMaximumChunkNo() { return maximumChunkNo; }
    public void setMaximumChunkNo(Integer value) { maximumChunkNo = value; }
}

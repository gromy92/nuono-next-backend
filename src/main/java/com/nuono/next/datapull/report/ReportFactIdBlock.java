package com.nuono.next.datapull.report;

/** One constant-time reservation for a set-based fact insert. */
public class ReportFactIdBlock {
    private final String sequenceName;
    private final long initialValue;
    private final long blockSize;
    private Long lastId;

    public ReportFactIdBlock(String sequenceName, long initialValue, long blockSize) {
        if (blockSize <= 0L) {
            throw new IllegalArgumentException("fact ID blockSize must be positive");
        }
        this.sequenceName = sequenceName;
        this.initialValue = initialValue;
        this.blockSize = blockSize;
    }

    public long firstId() {
        if (lastId == null || lastId < blockSize) {
            throw new IllegalStateException("fact ID block was not reserved");
        }
        return lastId - blockSize + 1L;
    }

    public String getSequenceName() { return sequenceName; }
    public long getInitialValue() { return initialValue; }
    public long getBlockSize() { return blockSize; }
    public Long getLastId() { return lastId; }
    public void setLastId(Long lastId) { this.lastId = lastId; }
}

package com.nuono.next.datapull.advertising;

/** MySQL LAST_INSERT_ID carrier for one O(1) contiguous ID block reservation. */
public final class AdvertisingIdBlockCommand {
    private final String sequenceName;
    private final long initialValue;
    private final long blockSize;
    private Long allocatedEnd;

    public AdvertisingIdBlockCommand(String sequenceName, long initialValue, long blockSize) {
        this.sequenceName = AdvertisingAdvertiser.requireIdentity(sequenceName, "sequenceName");
        if (initialValue < 0L || blockSize <= 0L) {
            throw new IllegalArgumentException("advertising ID block values are invalid");
        }
        this.initialValue = initialValue;
        this.blockSize = blockSize;
    }

    public String getSequenceName() { return sequenceName; }
    public long getInitialValue() { return initialValue; }
    public long getBlockSize() { return blockSize; }
    public Long getAllocatedEnd() { return allocatedEnd; }
    public void setAllocatedEnd(Long value) { allocatedEnd = value; }

    public long allocatedStart() {
        if (allocatedEnd == null || allocatedEnd < blockSize) {
            throw new IllegalStateException("advertising ID block was not allocated");
        }
        return Math.addExact(Math.subtractExact(allocatedEnd, blockSize), 1L);
    }
}

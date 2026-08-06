package com.nuono.next.datapull.persistence;

/** MyBatis command for one fixed-call contiguous task-id allocation. */
public final class DataPullTaskIdBlock {
    private final int size;
    private Long lastAllocatedId;

    public DataPullTaskIdBlock(int size) {
        if (size < 1 || size > 64) throw new IllegalArgumentException("task id block is invalid");
        this.size = size;
    }

    public int getSize() { return size; }
    public Long getLastAllocatedId() { return lastAllocatedId; }
    public void setLastAllocatedId(Long value) { lastAllocatedId = value; }
    public long firstId() {
        if (lastAllocatedId == null || lastAllocatedId < size) {
            throw new IllegalStateException("task id block was not allocated");
        }
        return lastAllocatedId - size + 1L;
    }
}

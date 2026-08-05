package com.nuono.next.officialwarehouse.datapull;

/** One transactional, contiguous ID reservation for a bounded DP-07-A row chunk. */
public final class InventorySnapshotIdBlock {
    private static final String SEQUENCE = "official_warehouse_inventory_snapshot_line";
    private static final long INITIAL_VALUE = 622000L;

    private final int blockSize;
    private Long lastId;

    public InventorySnapshotIdBlock(int blockSize) {
        if (blockSize < 1 || blockSize > 20) {
            throw new IllegalArgumentException("inventory ID block size is invalid");
        }
        this.blockSize = blockSize;
    }

    public long firstId() {
        if (lastId == null || lastId < blockSize) {
            throw new IllegalStateException("inventory ID block was not reserved");
        }
        return Math.addExact(Math.subtractExact(lastId, blockSize), 1L);
    }

    public String getSequenceName() { return SEQUENCE; }
    public long getInitialValue() { return INITIAL_VALUE; }
    public int getBlockSize() { return blockSize; }
    public Long getLastId() { return lastId; }
    public void setLastId(Long lastId) { this.lastId = lastId; }
}

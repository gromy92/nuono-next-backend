package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;

/** Exact staged order locator used by the bounded DETAIL step. */
public final class Ali1688Dp10PendingItem {
    private final long generationNo;
    private final int scanPass;
    private final Ali1688HistoricalOrderProvider.Partition partition;
    private final int pageNo;
    private final int itemOrdinal;

    Ali1688Dp10PendingItem(
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            int itemOrdinal
    ) {
        if (generationNo < 1 || scanPass != 2 || partition == null
                || pageNo < 1 || itemOrdinal < 0) {
            throw new IllegalArgumentException("invalid DP-10 pending item locator");
        }
        this.generationNo = generationNo;
        this.scanPass = scanPass;
        this.partition = partition;
        this.pageNo = pageNo;
        this.itemOrdinal = itemOrdinal;
    }

    public long getGenerationNo() { return generationNo; }
    public int getScanPass() { return scanPass; }
    public Ali1688HistoricalOrderProvider.Partition getPartition() { return partition; }
    public int getPageNo() { return pageNo; }
    public int getItemOrdinal() { return itemOrdinal; }
}

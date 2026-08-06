package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;

/** One locked order locator for an at-most-20 item/logistics fact segment. */
public final class Ali1688Dp10ApplySlice {
    private final long generationNo;
    private final String partition;
    private final int pageNo;
    private final int itemOrdinal;
    private final int itemCursor;
    private final Ali1688HistoricalOrderProvider.OrderSnapshot order;

    public Ali1688Dp10ApplySlice(
            long generationNo,
            String partition,
            int pageNo,
            int itemOrdinal,
            int itemCursor,
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        this.generationNo = generationNo;
        this.partition = partition;
        this.pageNo = pageNo;
        this.itemOrdinal = itemOrdinal;
        this.itemCursor = itemCursor;
        this.order = order;
    }

    public long getGenerationNo() { return generationNo; }
    public String getPartition() { return partition; }
    public int getPageNo() { return pageNo; }
    public int getItemOrdinal() { return itemOrdinal; }
    public int getItemCursor() { return itemCursor; }
    public Ali1688HistoricalOrderProvider.OrderSnapshot getOrder() { return order; }
}

package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.List;
import java.util.OptionalInt;

/** Revalidated durable raw page; facts may consume accepted rows only after both lists close. */
public final class Ali1688Dp10StagedPage {
    public enum State { LISTED, READY, VERIFYING, VERIFIED, APPLIED }

    private final long generationNo;
    private final int scanPass;
    private final Ali1688HistoricalOrderProvider.Partition partition;
    private final int pageNo;
    private final int pageSize;
    private final long totalRecord;
    private final int expectedPages;
    private final State state;
    private final List<Ali1688Dp10StagedOrder> orders;

    Ali1688Dp10StagedPage(
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            int pageSize,
            long totalRecord,
            int expectedPages,
            State state,
            List<Ali1688Dp10StagedOrder> orders
    ) {
        this.generationNo = generationNo;
        this.scanPass = scanPass;
        this.partition = partition;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.totalRecord = totalRecord;
        this.expectedPages = expectedPages;
        this.state = state;
        this.orders = List.copyOf(orders);
    }

    public OptionalInt nextPendingOrdinal() {
        for (Ali1688Dp10StagedOrder order : orders) {
            if (order.getState() == Ali1688Dp10ItemState.PENDING_DETAIL) {
                return OptionalInt.of(order.getOrdinal());
            }
        }
        return OptionalInt.empty();
    }

    public Ali1688Dp10StagedOrder orderAt(int ordinal) {
        return orders.stream().filter(order -> order.getOrdinal() == ordinal)
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("DP-10 staged item ordinal is missing"));
    }

    public long getGenerationNo() { return generationNo; }
    public int getScanPass() { return scanPass; }
    public Ali1688HistoricalOrderProvider.Partition getPartition() { return partition; }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public long getTotalRecord() { return totalRecord; }
    public int getExpectedPages() { return expectedPages; }
    public int getRawRowCount() { return orders.size(); }
    public State getState() { return state; }
    public boolean isApplied() { return state == State.APPLIED; }
    public boolean isReady() { return state == State.READY || state == State.VERIFIED; }
    public List<Ali1688Dp10StagedOrder> getOrders() { return orders; }
}

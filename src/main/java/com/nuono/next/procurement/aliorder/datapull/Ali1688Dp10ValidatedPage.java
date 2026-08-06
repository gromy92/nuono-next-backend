package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.List;

/** Proven raw page with the exact official partition/page/total envelope. */
final class Ali1688Dp10ValidatedPage {
    private final Ali1688HistoricalOrderProvider.Partition partition;
    private final int pageNo;
    private final int pageSize;
    private final long totalRecord;
    private final int expectedPages;
    private final List<Ali1688Dp10ListEntry> entries;

    Ali1688Dp10ValidatedPage(
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            int pageSize,
            long totalRecord,
            int expectedPages,
            List<Ali1688Dp10ListEntry> entries
    ) {
        this.partition = partition;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.totalRecord = totalRecord;
        this.expectedPages = expectedPages;
        this.entries = List.copyOf(entries);
    }

    Ali1688HistoricalOrderProvider.Partition getPartition() { return partition; }
    int getPageNo() { return pageNo; }
    int getPageSize() { return pageSize; }
    long getTotalRecord() { return totalRecord; }
    int getExpectedPages() { return expectedPages; }
    int getRawRowCount() { return entries.size(); }
    boolean isPartitionEnd() { return pageNo == expectedPages; }
    List<Ali1688Dp10ListEntry> getEntries() { return entries; }
}

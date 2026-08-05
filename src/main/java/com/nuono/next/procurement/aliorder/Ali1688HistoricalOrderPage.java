package com.nuono.next.procurement.aliorder;

import java.time.Duration;
import java.util.List;

/** Typed list envelope with explicit container and pagination proof flags. */
public class Ali1688HistoricalOrderPage {
    private final List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders;
    private String nextCursor;
    private boolean hasMore;
    private int progressPercent = 100;
    private String failureCode;
    private String failureMessage;
    private boolean retryableFailure;
    private boolean endOfStream = true;
    private Duration retryAfter;
    private boolean containerProven;
    private boolean paginationProven;
    private int pageNo;
    private int pageSize;
    private long totalRecord = -1L;
    private int expectedPages = -1;

    protected Ali1688HistoricalOrderPage(
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders
    ) {
        this.orders = orders == null ? List.of() : orders;
    }

    public List<Ali1688HistoricalOrderProvider.OrderSnapshot> getOrders() { return orders; }
    public String getNextCursor() { return nextCursor; }
    public void setNextCursor(String value) { nextCursor = value; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean value) { hasMore = value; endOfStream = !value; }
    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int value) { progressPercent = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { failureCode = value; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String value) { failureMessage = value; }
    public boolean isRetryableFailure() { return retryableFailure; }
    public void setRetryableFailure(boolean value) { retryableFailure = value; }
    public boolean hasFailure() { return failureCode != null && !failureCode.isBlank(); }
    public boolean isEndOfStream() { return endOfStream; }
    public void setEndOfStream(boolean value) { endOfStream = value; hasMore = !value; }
    public Duration getRetryAfter() { return retryAfter; }
    public void setRetryAfter(Duration value) {
        retryAfter = value == null || value.isNegative() ? null : value;
    }
    public boolean isContainerProven() { return containerProven; }
    public void setContainerProven(boolean value) { containerProven = value; }
    public boolean isPaginationProven() { return paginationProven; }
    public void setPaginationProven(boolean value) { paginationProven = value; }
    public int getPageNo() { return pageNo; }
    public void setPageNo(int value) { pageNo = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public long getTotalRecord() { return totalRecord; }
    public void setTotalRecord(long value) { totalRecord = value; }
    public int getExpectedPages() { return expectedPages; }
    public void setExpectedPages(int value) { expectedPages = value; }
}

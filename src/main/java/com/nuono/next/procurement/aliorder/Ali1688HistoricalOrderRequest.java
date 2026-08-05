package com.nuono.next.procurement.aliorder;

import java.time.Instant;

/** Immutable official list request shared by legacy and DP-10 provider interfaces. */
public final class Ali1688HistoricalOrderRequest {
    private final Ali1688HistoricalOrderAuthorizationRow authorization;
    private final Ali1688HistoricalOrderProvider.SyncMode mode;
    private final String providerCursor;
    private final Instant modifiedFrom;
    private final Ali1688HistoricalOrderProvider.Partition partition;
    private final int pageNo;
    private final int pageSize;
    private final Instant modifiedTo;
    private final boolean fixedWindow;

    private Ali1688HistoricalOrderRequest(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String providerCursor
    ) {
        this.authorization = authorization;
        this.mode = Ali1688HistoricalOrderProvider.SyncMode.FULL;
        this.providerCursor = providerCursor;
        this.modifiedFrom = null;
        this.partition = Ali1688HistoricalOrderProvider.Partition.CURRENT;
        this.pageNo = parsePage(providerCursor);
        this.pageSize = 20;
        this.modifiedTo = null;
        this.fixedWindow = false;
    }

    private Ali1688HistoricalOrderRequest(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.SyncMode mode,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            int pageSize,
            Instant modifiedFrom,
            Instant modifiedTo
    ) {
        if (authorization == null || mode == null || partition == null
                || pageNo < 1 || pageSize < 1 || modifiedTo == null
                || mode == Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL
                        && modifiedFrom == null
                || modifiedFrom != null && modifiedTo.isBefore(modifiedFrom)) {
            throw new IllegalArgumentException("invalid fixed 1688 order-list window");
        }
        this.authorization = authorization;
        this.mode = mode;
        this.providerCursor = String.valueOf(pageNo);
        this.modifiedFrom = modifiedFrom;
        this.partition = partition;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.modifiedTo = modifiedTo;
        this.fixedWindow = true;
    }

    public Ali1688HistoricalOrderAuthorizationRow getAuthorization() { return authorization; }
    public Ali1688HistoricalOrderProvider.SyncMode getMode() { return mode; }
    public String getProviderCursor() { return providerCursor; }
    public Instant getModifiedFrom() { return modifiedFrom; }
    public Ali1688HistoricalOrderProvider.Partition getPartition() { return partition; }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public Instant getModifiedTo() { return modifiedTo; }
    public boolean isFixedWindow() { return fixedWindow; }
    private int parsePage(String value) {
        if (value == null || value.isBlank()) return 1;
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public static Ali1688HistoricalOrderRequest full(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String providerCursor
    ) {
        return new Ali1688HistoricalOrderRequest(authorization, providerCursor);
    }

    public static Ali1688HistoricalOrderRequest window(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.SyncMode mode,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            int pageSize,
            Instant modifiedFrom,
            Instant modifiedTo
    ) {
        return new Ali1688HistoricalOrderRequest(
                authorization,
                mode,
                partition,
                pageNo,
                pageSize,
                modifiedFrom,
                modifiedTo
        );
    }
}

package com.nuono.next.procurement.aliorder.datapull;

/** DP-10-only list-page bound; legacy/manual 1688 flows keep their own contracts. */
final class Ali1688Dp10ListPageContract {
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private Ali1688Dp10ListPageContract() {}

    static int requireSupported(int pageSize) {
        if (!isSupported(pageSize)) {
            throw new IllegalArgumentException("DP-10 provider page size must be between 1 and 100");
        }
        return pageSize;
    }

    static boolean isSupported(int pageSize) {
        return pageSize >= 1 && pageSize <= MAX_PAGE_SIZE;
    }
}

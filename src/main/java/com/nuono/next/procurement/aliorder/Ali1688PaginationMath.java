package com.nuono.next.procurement.aliorder;

/** Checked pagination arithmetic for the official 1688 Long total contract. */
public final class Ali1688PaginationMath {
    private Ali1688PaginationMath() {
    }

    public static int expectedPages(long totalRecord, int pageSize) {
        if (totalRecord < 0L || pageSize < 1) {
            throw new IllegalArgumentException("invalid 1688 pagination inputs");
        }
        long pages = totalRecord / pageSize;
        if (totalRecord % pageSize != 0L) pages = Math.addExact(pages, 1L);
        return Math.toIntExact(Math.max(1L, pages));
    }

    public static int expectedRowsOnPage(
            long totalRecord,
            int pageNo,
            int pageSize,
            int expectedPages
    ) {
        if (pageNo < 1 || pageNo > expectedPages || pageSize < 1 || expectedPages < 1) {
            throw new IllegalArgumentException("invalid 1688 page position");
        }
        if (pageNo < expectedPages) return pageSize;
        long precedingRows = Math.multiplyExact((long) expectedPages - 1L, pageSize);
        return Math.toIntExact(Math.subtractExact(totalRecord, precedingRows));
    }
}

package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;

/** Exact contract for alibaba.trade.getBuyerOrderList-1. */
final class Ali1688OpenApiListContract {
    private final Ali1688OpenApiJson json;

    Ali1688OpenApiListContract(Ali1688OpenApiJson json) {
        this.json = json;
    }

    JsonNode orderContainer(JsonNode root, boolean fixedWindow) {
        if (root == null || root.isNull()) return null;
        JsonNode result = root.get("result");
        if (result != null && result.isArray()) return result;
        if (!fixedWindow) {
            JsonNode unwrapped = json.unwrapResult(root);
            if (unwrapped.isArray()) return unwrapped;
            for (String name : new String[]{"orders", "orderList", "items"}) {
                JsonNode candidate = unwrapped.get(name);
                if (candidate != null && candidate.isArray()) return candidate;
            }
        }
        return null;
    }

    Pagination provePagination(JsonNode root, int pageNo, int pageSize) {
        Long total = json.longInteger(root, "totalRecord");
        if (total == null || total < 0L || pageNo < 1 || pageSize < 1) {
            return Pagination.unknown();
        }
        try {
            int expectedPages = Ali1688PaginationMath.expectedPages(total, pageSize);
            return Pagination.proven(total, expectedPages, pageNo < expectedPages);
        } catch (ArithmeticException unrepresentablePageCount) {
            return Pagination.unknown();
        }
    }

    static final class Pagination {
        private final boolean proven;
        private final long totalRecord;
        private final int expectedPages;
        private final boolean hasMore;

        private Pagination(boolean proven, long totalRecord, int expectedPages, boolean hasMore) {
            this.proven = proven;
            this.totalRecord = totalRecord;
            this.expectedPages = expectedPages;
            this.hasMore = hasMore;
        }

        static Pagination proven(long total, int pages, boolean hasMore) {
            return new Pagination(true, total, pages, hasMore);
        }

        static Pagination unknown() { return new Pagination(false, -1, -1, false); }
        boolean isProven() { return proven; }
        long totalRecord() { return totalRecord; }
        int expectedPages() { return expectedPages; }
        boolean hasMore() { return hasMore; }
    }
}

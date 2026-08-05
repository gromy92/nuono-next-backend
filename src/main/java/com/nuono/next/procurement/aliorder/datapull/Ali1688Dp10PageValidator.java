package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688PaginationMath;
import java.util.ArrayList;
import java.util.List;

/** Proves one exact page against the fixed page/total contract; ordering is never assumed. */
final class Ali1688Dp10PageValidator {
    private final Ali1688Dp10OrderValidator orderValidator = new Ali1688Dp10OrderValidator();

    Ali1688Dp10ValidatedPage validate(
            Ali1688HistoricalOrderProvider.Page page,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        if (page == null || page.hasFailure() || !page.isContainerProven()
                || !page.isPaginationProven() || page.getOrders() == null) {
            throw invalid("DP10_PAGE_CONTAINER_UNPROVEN");
        }
        if (page.getOrders().stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("DP10_PAGE_ROW_UNMAPPED");
        }
        if (page.getPageNo() != checkpoint.getPageNo()
                || page.getPageSize() != checkpoint.getPageSize()) {
            throw invalid("DP10_PAGE_REQUEST_ECHO_INVALID");
        }
        long total = page.getTotalRecord();
        int pageSize = page.getPageSize();
        if (total < 0L || !Ali1688Dp10ListPageContract.isSupported(pageSize)) {
            throw invalid("DP10_PAGE_TOTAL_INVALID");
        }
        int pages;
        try {
            pages = Ali1688PaginationMath.expectedPages(total, pageSize);
        } catch (ArithmeticException unrepresentablePageCount) {
            throw invalid("DP10_PAGE_COUNT_UNREPRESENTABLE");
        }
        if (page.getExpectedPages() != pages) {
            throw invalid("DP10_PAGE_COUNT_PROOF_INVALID");
        }
        if (checkpoint.getExpectedTotal() != null
                && (!checkpoint.getExpectedTotal().equals(total)
                || !checkpoint.getExpectedPages().equals(pages))) {
            throw invalid("DP10_PARTITION_TOTAL_DRIFT");
        }
        if (page.getPageNo() > pages) {
            throw invalid("DP10_PARTITION_TOTAL_DRIFT");
        }
        int expectedRows = Ali1688PaginationMath.expectedRowsOnPage(
                total, page.getPageNo(), pageSize, pages);
        if (page.getOrders().size() != expectedRows) {
            throw invalid("DP10_PAGE_RAW_ROW_COUNT_INVALID");
        }
        boolean hasMore = page.getPageNo() < pages;
        if (page.isHasMore() != hasMore || page.isEndOfStream() == hasMore) {
            throw invalid("DP10_PAGE_END_STATE_INVALID");
        }
        List<Ali1688Dp10ListEntry> entries = new ArrayList<>();
        int ordinal = 0;
        for (Ali1688HistoricalOrderProvider.OrderSnapshot order : page.getOrders()) {
            String rawFingerprint = Ali1688Dp10RawOrderFingerprint.fingerprint(order);
            entries.add(orderValidator.classifyListOrder(
                    ordinal++, order, rawFingerprint));
        }
        return new Ali1688Dp10ValidatedPage(
                checkpoint.getPartition(),
                page.getPageNo(),
                pageSize,
                total,
                pages,
                entries
        );
    }

    private Ali1688Dp10PageContractException invalid(String code) {
        return new Ali1688Dp10PageContractException(code);
    }
}

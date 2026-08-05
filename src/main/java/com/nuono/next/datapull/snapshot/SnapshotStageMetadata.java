package com.nuono.next.datapull.snapshot;

import java.util.List;
import java.util.Objects;

final class SnapshotStageMetadata {
    private SnapshotStageMetadata() {
    }

    static Decision merge(
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageCandidate<?> page,
            Integer maxStagedPage
    ) {
        String baseError = validateAggregate(aggregate);
        if (baseError != null) {
            return Decision.reject(baseError);
        }
        String pageError = validateEnvelope(
                page.getPageNo(),
                page.getNextPage(),
                page.getLastPage(),
                page.getTotalPages()
        );
        if (pageError != null) {
            return Decision.reject(pageError);
        }
        Integer declared = aggregate.getDeclaredTotalPages();
        if (declared != null
                && page.getTotalPages() != null
                && !declared.equals(page.getTotalPages())) {
            return Decision.reject("SNAPSHOT_TOTAL_PAGES_DRIFT");
        }
        Integer nextDeclared = declared == null ? page.getTotalPages() : declared;
        Integer knownLast = aggregate.getKnownLastPage();
        knownLast = mergeLastPage(knownLast, page.getTotalPages());
        if (knownLast == null && (aggregate.getKnownLastPage() != null
                || page.getTotalPages() != null)) {
            return Decision.reject("SNAPSHOT_LAST_PAGE_DRIFT");
        }
        if (Boolean.TRUE.equals(page.getLastPage())) {
            Integer merged = mergeLastPage(knownLast, page.getPageNo());
            if (merged == null) {
                return Decision.reject("SNAPSHOT_LAST_PAGE_DRIFT");
            }
            knownLast = merged;
        }
        String boundaryError = validateKnownBoundary(
                page.getPageNo(),
                page.getNextPage(),
                page.getLastPage(),
                knownLast,
                maxStagedPage
        );
        if (boundaryError != null) {
            return Decision.reject(boundaryError);
        }
        return route(nextDeclared, knownLast, page.getPageNo(), page.getNextPage(), page.getLastPage());
    }

    static Decision routeExisting(
            SnapshotStageAggregateRow aggregate,
            SnapshotStagePageRow page
    ) {
        String aggregateError = validateAggregate(aggregate);
        if (aggregateError != null) {
            return Decision.reject(aggregateError);
        }
        String envelopeError = validateEnvelope(
                page.getPageNo(),
                page.getNextPage(),
                page.getLastPage(),
                page.getTotalPages()
        );
        if (envelopeError != null) {
            return Decision.reject(envelopeError);
        }
        if (page.getTotalPages() != null
                && !Objects.equals(page.getTotalPages(), aggregate.getDeclaredTotalPages())) {
            return Decision.reject("SNAPSHOT_TOTAL_PAGES_DRIFT");
        }
        String boundaryError = validateKnownBoundary(
                page.getPageNo(),
                page.getNextPage(),
                page.getLastPage(),
                aggregate.getKnownLastPage(),
                null
        );
        if (boundaryError != null) {
            return Decision.reject(boundaryError);
        }
        return route(
                aggregate.getDeclaredTotalPages(),
                aggregate.getKnownLastPage(),
                page.getPageNo(),
                page.getNextPage(),
                page.getLastPage()
        );
    }

    static String validateProof(
            SnapshotStageAggregateRow aggregate,
            List<SnapshotStagePageRow> pages
    ) {
        String aggregateError = validateAggregate(aggregate);
        if (aggregateError != null) {
            return aggregateError;
        }
        Integer knownLast = aggregate.getKnownLastPage();
        if (knownLast == null) {
            return "SNAPSHOT_LAST_PAGE_UNKNOWN";
        }
        long expectedPage = 1L;
        for (SnapshotStagePageRow page : pages) {
            if (page == null || page.getPageNo() == null || page.getItemCount() == null
                    || page.getItemCount() < 0) {
                return "SNAPSHOT_STAGE_STATE_INVALID";
            }
            if (page.getPageNo() > knownLast) {
                return "SNAPSHOT_PAGE_AFTER_LAST";
            }
            if (page.getPageNo() != expectedPage) {
                return "SNAPSHOT_MISSING_PAGE";
            }
            String envelopeError = validateEnvelope(
                    page.getPageNo(),
                    page.getNextPage(),
                    page.getLastPage(),
                    page.getTotalPages()
            );
            if (envelopeError != null) {
                return envelopeError;
            }
            if (page.getTotalPages() != null
                    && !Objects.equals(page.getTotalPages(), aggregate.getDeclaredTotalPages())) {
                return "SNAPSHOT_TOTAL_PAGES_DRIFT";
            }
            String boundaryError = validateKnownBoundary(
                    page.getPageNo(),
                    page.getNextPage(),
                    page.getLastPage(),
                    knownLast,
                    null
            );
            if (boundaryError != null) {
                return boundaryError;
            }
            expectedPage++;
        }
        return expectedPage == (long) knownLast + 1L ? null : "SNAPSHOT_MISSING_PAGE";
    }

    private static String validateAggregate(SnapshotStageAggregateRow aggregate) {
        if (aggregate == null || aggregate.getTaskId() == null
                || aggregate.getActiveFenceEpoch() == null
                || aggregate.getTaskId() < 1L || aggregate.getActiveFenceEpoch() < 1L
                || invalidPositive(aggregate.getDeclaredTotalPages())
                || invalidPositive(aggregate.getKnownLastPage())) {
            return "SNAPSHOT_STAGE_STATE_INVALID";
        }
        if (aggregate.getDeclaredTotalPages() != null
                && aggregate.getKnownLastPage() != null
                && !aggregate.getDeclaredTotalPages().equals(aggregate.getKnownLastPage())) {
            return "SNAPSHOT_LAST_PAGE_DRIFT";
        }
        return null;
    }

    private static String validateEnvelope(
            Integer pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages
    ) {
        if (pageNo == null || pageNo < 1 || invalidPositive(nextPage) || invalidPositive(totalPages)) {
            return "SNAPSHOT_PAGE_METADATA_INVALID";
        }
        if (nextPage != null && nextPage != pageNo + 1) {
            return "SNAPSHOT_NON_CONTIGUOUS_NEXT_PAGE";
        }
        if (totalPages != null && totalPages < pageNo) {
            return "SNAPSHOT_TOTAL_PAGES_BEFORE_PAGE";
        }
        if (Boolean.TRUE.equals(lastPage) && nextPage != null) {
            return "SNAPSHOT_LAST_PAGE_HAS_NEXT";
        }
        if (Boolean.TRUE.equals(lastPage) && totalPages != null && !totalPages.equals(pageNo)) {
            return "SNAPSHOT_LAST_TOTAL_CONFLICT";
        }
        if (Boolean.FALSE.equals(lastPage) && totalPages != null && totalPages.equals(pageNo)) {
            return "SNAPSHOT_NOT_LAST_TOTAL_CONFLICT";
        }
        return null;
    }

    private static String validateKnownBoundary(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer knownLast,
            Integer maxStagedPage
    ) {
        if (knownLast == null) {
            return null;
        }
        if (Boolean.TRUE.equals(lastPage) && pageNo != knownLast) {
            return "SNAPSHOT_LAST_PAGE_DRIFT";
        }
        if (pageNo > knownLast) {
            return "SNAPSHOT_PAGE_AFTER_LAST";
        }
        if (Boolean.FALSE.equals(lastPage) && pageNo == knownLast) {
            return "SNAPSHOT_NOT_LAST_FLAG_ON_LAST";
        }
        if (nextPage != null && nextPage > knownLast) {
            return "SNAPSHOT_NEXT_PAGE_AFTER_LAST";
        }
        if (maxStagedPage != null && maxStagedPage > knownLast) {
            return "SNAPSHOT_LAST_BEFORE_STAGED_PAGE";
        }
        return null;
    }

    private static Decision route(
            Integer declaredTotalPages,
            Integer knownLastPage,
            int pageNo,
            Integer nextPage,
            Boolean lastPage
    ) {
        if (knownLastPage != null) {
            return Decision.accept(
                    declaredTotalPages,
                    knownLastPage,
                    pageNo == knownLastPage ? null : pageNo + 1
            );
        }
        if (nextPage != null) {
            return Decision.accept(declaredTotalPages, null, nextPage);
        }
        if (Boolean.FALSE.equals(lastPage)) {
            return Decision.accept(declaredTotalPages, null, pageNo + 1);
        }
        return Decision.reject("SNAPSHOT_LAST_PAGE_UNKNOWN");
    }

    private static Integer mergeLastPage(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || current.equals(candidate) ? candidate : null;
    }

    private static boolean invalidPositive(Integer value) {
        return value != null && value < 1;
    }

    static final class Decision {
        private final String rejectionCode;
        private final Integer declaredTotalPages;
        private final Integer knownLastPage;
        private final Integer nextPage;

        private Decision(
                String rejectionCode,
                Integer declaredTotalPages,
                Integer knownLastPage,
                Integer nextPage
        ) {
            this.rejectionCode = rejectionCode;
            this.declaredTotalPages = declaredTotalPages;
            this.knownLastPage = knownLastPage;
            this.nextPage = nextPage;
        }
        static Decision reject(String code) {
            return new Decision(code, null, null, null);
        }
        static Decision accept(Integer declared, Integer knownLast, Integer nextPage) {
            return new Decision(null, declared, knownLast, nextPage);
        }
        boolean isAccepted() {
            return rejectionCode == null;
        }
        String getRejectionCode() {
            return rejectionCode;
        }
        Integer getDeclaredTotalPages() {
            return declaredTotalPages;
        }
        Integer getKnownLastPage() {
            return knownLastPage;
        }
        Integer getNextPage() {
            return nextPage;
        }
    }
}

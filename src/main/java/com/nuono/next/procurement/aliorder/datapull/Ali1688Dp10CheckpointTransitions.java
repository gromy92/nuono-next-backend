package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688PaginationMath;
import java.time.Instant;
import java.util.Objects;

/** Internal transition/validation Implementation behind the checkpoint Interface. */
final class Ali1688Dp10CheckpointTransitions {
    private Ali1688Dp10CheckpointTransitions() {}

    static Ali1688Dp10Checkpoint bindContract(
            Ali1688Dp10Checkpoint value,
            long total,
            int pages
    ) {
        Ali1688Dp10Checkpoint next = copy(value);
        if (next.expectedTotal == null) {
            next.expectedTotal = total;
            next.expectedPages = pages;
        } else if (!next.expectedTotal.equals(total) || !next.expectedPages.equals(pages)) {
            throw invalid("DP10_PARTITION_TOTAL_DRIFT");
        }
        validate(next);
        return next;
    }

    static Ali1688Dp10Checkpoint afterPage(
            Ali1688Dp10Checkpoint value,
            Ali1688Dp10StagedPage page
    ) {
        if (page.getGenerationNo() != value.generationNo
                || page.getScanPass() != value.scanPass
                || page.getPartition() != value.partition || page.getPageNo() != value.pageNo
                || value.expectedTotal == null
                || !value.expectedTotal.equals(page.getTotalRecord())
                || !value.expectedPages.equals(page.getExpectedPages())) {
            throw invalid("DP10_STAGED_PAGE_CONTRACT_MISMATCH");
        }
        long raw = Math.addExact(value.stagedRawRowCount, page.getRawRowCount());
        Ali1688Dp10Checkpoint next = copy(value);
        if (value.pageNo < value.expectedPages) {
            next.pageNo++;
            next.stagedRawRowCount = raw;
        } else {
            if (raw != value.expectedTotal) throw invalid("DP10_PARTITION_RAW_COUNT_MISMATCH");
            captureOrCompare(next);
            if (value.partition == Ali1688HistoricalOrderProvider.Partition.CURRENT) {
                startPartition(next, Ali1688HistoricalOrderProvider.Partition.HISTORY);
            } else if (value.scanPass == 1) {
                next.scanPass = 2;
                startPartition(next, Ali1688HistoricalOrderProvider.Partition.CURRENT);
            } else {
                next.scansClosed = true;
                next.stagedRawRowCount = raw;
            }
        }
        validate(next);
        return next;
    }

    static Ali1688Dp10Checkpoint afterSealBatch(
            Ali1688Dp10Checkpoint value,
            Ali1688HistoricalOrderProvider.Partition sealed,
            Ali1688Dp10SealBatch batch
    ) {
        if (!value.scansClosed || value.sealedPartitions > 1
                || sealed != value.nextSealPartition() || batch == null || !batch.isMatching()) {
            throw new IllegalStateException("invalid DP-10 seal transition");
        }
        long compared;
        try {
            compared = Math.addExact(
                    value.sealComparedRawRows, batch.getMatchedRawRows());
        } catch (ArithmeticException overflow) {
            throw invalid("DP10_SEAL_COUNT_OVERFLOW");
        }
        long expected = sealExpectedTotal(value);
        if (compared > expected) throw invalid("DP10_MULTIPASS_MULTISET_DRIFT");
        Ali1688Dp10Checkpoint next = copy(value);
        if (batch.isExhausted()) {
            if (compared != expected) throw invalid("DP10_MULTIPASS_MULTISET_DRIFT");
            next.sealedPartitions++;
            next.sealAfterFingerprint = null;
            next.sealComparedRawRows = 0L;
        } else {
            String cursor = batch.getLastFingerprint();
            if (batch.getMatchedRawRows() <= 0L || !validFingerprint(cursor)
                    || value.sealAfterFingerprint != null
                    && cursor.compareTo(value.sealAfterFingerprint) <= 0) {
                throw invalid("DP10_SEAL_BATCH_CURSOR_INVALID");
            }
            next.sealAfterFingerprint = cursor;
            next.sealComparedRawRows = compared;
        }
        validate(next);
        return next;
    }

    static Ali1688Dp10Checkpoint restartGeneration(Ali1688Dp10Checkpoint value) {
        Ali1688Dp10Checkpoint next = copy(value);
        next.generationNo = Math.addExact(value.generationNo, 1L);
        next.scanPass = 1;
        next.partition = Ali1688HistoricalOrderProvider.Partition.CURRENT;
        next.pageNo = 1;
        next.expectedTotal = null;
        next.expectedPages = null;
        next.stagedRawRowCount = 0L;
        next.passOneCurrentTotal = null;
        next.passOneCurrentPages = null;
        next.passOneHistoryTotal = null;
        next.passOneHistoryPages = null;
        next.scansClosed = false;
        next.sealedPartitions = 0;
        next.sealAfterFingerprint = null;
        next.sealComparedRawRows = 0L;
        next.detailPartition = null;
        next.detailPageNo = null;
        next.detailItemOrdinal = null;
        validate(next);
        return next;
    }

    static void validate(Ali1688Dp10Checkpoint value) {
        Instant start = value.windowStart();
        Instant end = value.windowEnd();
        boolean detailNull = value.detailPartition == null && value.detailPageNo == null
                && value.detailItemOrdinal == null;
        boolean detailPresent = value.detailPartition != null && value.detailPageNo != null
                && value.detailPageNo > 0 && value.detailItemOrdinal != null
                && value.detailItemOrdinal >= 0;
        if (value.schemaVersion != Ali1688Dp10Checkpoint.SCHEMA_VERSION || value.mode == null
                || value.partition == null || end == null
                || start != null && end.isBefore(start) || value.generationNo < 1
                || value.scanPass < 1 || value.scanPass > 2 || value.pageNo < 1
                || value.pageSize < 1
                || value.pageSize > Ali1688Dp10ListPageContract.MAX_PAGE_SIZE
                || value.expectedProgressVersion < 0
                || value.stagedRawRowCount < 0 || value.sealedPartitions < 0
                || value.sealedPartitions > 2 || value.sealedPartitions > 0 && !value.scansClosed
                || value.sealComparedRawRows < 0L
                || !(detailNull || detailPresent)) {
            throw new IllegalArgumentException("invalid DP-10 checkpoint state");
        }
        if (value.mode == Ali1688HistoricalOrderProvider.SyncMode.FULL && start != null
                || value.mode == Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL
                && start == null) {
            throw new IllegalArgumentException("DP-10 start boundary does not match sync mode");
        }
        validateContracts(value);
        validateSeal(value, detailNull);
    }

    static Ali1688Dp10Checkpoint copy(Ali1688Dp10Checkpoint value) {
        Ali1688Dp10Checkpoint copy = new Ali1688Dp10Checkpoint();
        copy.schemaVersion = value.schemaVersion;
        copy.mode = value.mode;
        copy.windowStartUtc = value.windowStartUtc;
        copy.windowEndUtc = value.windowEndUtc;
        copy.generationNo = value.generationNo;
        copy.scanPass = value.scanPass;
        copy.partition = value.partition;
        copy.pageNo = value.pageNo;
        copy.pageSize = value.pageSize;
        copy.expectedTotal = value.expectedTotal;
        copy.expectedPages = value.expectedPages;
        copy.stagedRawRowCount = value.stagedRawRowCount;
        copy.passOneCurrentTotal = value.passOneCurrentTotal;
        copy.passOneCurrentPages = value.passOneCurrentPages;
        copy.passOneHistoryTotal = value.passOneHistoryTotal;
        copy.passOneHistoryPages = value.passOneHistoryPages;
        copy.scansClosed = value.scansClosed;
        copy.sealedPartitions = value.sealedPartitions;
        copy.sealAfterFingerprint = value.sealAfterFingerprint;
        copy.sealComparedRawRows = value.sealComparedRawRows;
        copy.detailPartition = value.detailPartition;
        copy.detailPageNo = value.detailPageNo;
        copy.detailItemOrdinal = value.detailItemOrdinal;
        copy.expectedProgressVersion = value.expectedProgressVersion;
        return copy;
    }

    private static void captureOrCompare(Ali1688Dp10Checkpoint value) {
        if (value.scanPass == 1) {
            if (value.partition == Ali1688HistoricalOrderProvider.Partition.CURRENT) {
                value.passOneCurrentTotal = value.expectedTotal;
                value.passOneCurrentPages = value.expectedPages;
            } else {
                value.passOneHistoryTotal = value.expectedTotal;
                value.passOneHistoryPages = value.expectedPages;
            }
            return;
        }
        Long total = value.partition == Ali1688HistoricalOrderProvider.Partition.CURRENT
                ? value.passOneCurrentTotal : value.passOneHistoryTotal;
        Integer pages = value.partition == Ali1688HistoricalOrderProvider.Partition.CURRENT
                ? value.passOneCurrentPages : value.passOneHistoryPages;
        if (!Objects.equals(total, value.expectedTotal)
                || !Objects.equals(pages, value.expectedPages)) {
            throw invalid("DP10_MULTIPASS_TOTAL_DRIFT");
        }
    }

    private static void startPartition(
            Ali1688Dp10Checkpoint value,
            Ali1688HistoricalOrderProvider.Partition partition
    ) {
        value.partition = partition;
        value.pageNo = 1;
        value.expectedTotal = null;
        value.expectedPages = null;
        value.stagedRawRowCount = 0L;
    }

    private static void validateContracts(Ali1688Dp10Checkpoint value) {
        if ((value.expectedTotal == null) != (value.expectedPages == null)
                || (value.passOneCurrentTotal == null) != (value.passOneCurrentPages == null)
                || (value.passOneHistoryTotal == null) != (value.passOneHistoryPages == null)) {
            throw new IllegalArgumentException("partial DP-10 page contract");
        }
        validateContract(value.expectedTotal, value.expectedPages, value.pageSize);
        validateContract(value.passOneCurrentTotal, value.passOneCurrentPages, value.pageSize);
        validateContract(value.passOneHistoryTotal, value.passOneHistoryPages, value.pageSize);
        if (value.scanPass == 2
                && (value.passOneCurrentTotal == null || value.passOneHistoryTotal == null)) {
            throw new IllegalArgumentException("DP-10 second pass lacks first-pass contract");
        }
        if (value.expectedTotal != null && (value.pageNo > value.expectedPages
                || value.stagedRawRowCount > value.expectedTotal)) {
            throw new IllegalArgumentException("invalid DP-10 page contract");
        }
    }

    private static void validateContract(Long total, Integer pages, int size) {
        if (total == null) return;
        if (total < 0 || !Objects.equals(pages, expectedPages(total, size))) {
            throw new IllegalArgumentException("invalid DP-10 page contract");
        }
    }

    private static void validateSeal(Ali1688Dp10Checkpoint value, boolean detailNull) {
        boolean cursorNull = value.sealAfterFingerprint == null;
        if (!cursorNull && !validFingerprint(value.sealAfterFingerprint)) {
            throw new IllegalArgumentException("invalid DP-10 seal fingerprint cursor");
        }
        if (!value.scansClosed && (value.sealedPartitions != 0 || !cursorNull
                || value.sealComparedRawRows != 0L)
                || value.sealedPartitions == 2 && (!cursorNull
                || value.sealComparedRawRows != 0L)
                || cursorNull != (value.sealComparedRawRows == 0L)
                || value.sealedPartitions < 2 && !detailNull) {
            throw new IllegalArgumentException("invalid DP-10 seal state");
        }
        if (value.scansClosed && value.sealedPartitions < 2
                && value.sealComparedRawRows > sealExpectedTotal(value)) {
            throw new IllegalArgumentException("DP-10 seal count exceeds partition total");
        }
    }

    private static long sealExpectedTotal(Ali1688Dp10Checkpoint value) {
        Long total = value.sealedPartitions == 0
                ? value.passOneCurrentTotal : value.passOneHistoryTotal;
        if (total == null) throw new IllegalArgumentException("DP-10 seal contract is missing");
        return total;
    }

    private static boolean validFingerprint(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (!(item >= '0' && item <= '9') && !(item >= 'a' && item <= 'f')) return false;
        }
        return true;
    }

    private static int expectedPages(long total, int size) {
        try { return Ali1688PaginationMath.expectedPages(total, size); }
        catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    "DP-10 page count exceeds the supported page locator", invalid);
        }
    }

    private static Ali1688Dp10PageContractException invalid(String code) {
        return new Ali1688Dp10PageContractException(code);
    }
}

package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** V5 fixed-window, generation-scoped, two-pass checkpoint with a bounded seal cursor. */
public final class Ali1688Dp10Checkpoint {
    static final int SCHEMA_VERSION = 5;
    private static final Duration SAFETY_OVERLAP = Duration.ofHours(24);

    int schemaVersion;
    Ali1688HistoricalOrderProvider.SyncMode mode;
    String windowStartUtc;
    String windowEndUtc;
    long generationNo;
    int scanPass;
    Ali1688HistoricalOrderProvider.Partition partition;
    int pageNo;
    int pageSize;
    Long expectedTotal;
    Integer expectedPages;
    long stagedRawRowCount;
    Long passOneCurrentTotal;
    Integer passOneCurrentPages;
    Long passOneHistoryTotal;
    Integer passOneHistoryPages;
    boolean scansClosed;
    int sealedPartitions;
    String sealAfterFingerprint;
    long sealComparedRawRows;
    Ali1688HistoricalOrderProvider.Partition detailPartition;
    Integer detailPageNo;
    Integer detailItemOrdinal;
    long expectedProgressVersion;

    public Ali1688Dp10Checkpoint() {
        // Jackson constructor.
    }

    static Ali1688Dp10Checkpoint initial(
            DataPullScopeProgress progress,
            LocalDateTime nowUtc,
            int pageSize
    ) {
        DataPullScopeProgress value = Objects.requireNonNull(progress, "progress");
        value.validate();
        if (nowUtc == null) throw new IllegalArgumentException("DP-10 fixed window requires time");
        Ali1688Dp10ListPageContract.requireSupported(pageSize);
        Instant highWater = value.getOfficialModifiedHighWaterUtc() == null
                ? null : value.getOfficialModifiedHighWaterUtc().toInstant(ZoneOffset.UTC);
        Ali1688Dp10Checkpoint checkpoint = new Ali1688Dp10Checkpoint();
        checkpoint.schemaVersion = SCHEMA_VERSION;
        checkpoint.mode = value.isInitialFullCompleted()
                ? Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL
                : Ali1688HistoricalOrderProvider.SyncMode.FULL;
        Instant start = windowStart(checkpoint.mode, highWater);
        checkpoint.windowStartUtc = start == null ? null : start.toString();
        checkpoint.windowEndUtc = nowUtc.toInstant(ZoneOffset.UTC).toString();
        checkpoint.generationNo = 1L;
        checkpoint.scanPass = 1;
        checkpoint.partition = Ali1688HistoricalOrderProvider.Partition.CURRENT;
        checkpoint.pageNo = 1;
        checkpoint.pageSize = pageSize;
        checkpoint.expectedProgressVersion = value.getVersion();
        checkpoint.validate();
        return checkpoint;
    }

    Ali1688Dp10Checkpoint bindContract(long total, int pages) {
        return Ali1688Dp10CheckpointTransitions.bindContract(this, total, pages);
    }

    Ali1688Dp10Checkpoint afterPage(Ali1688Dp10StagedPage page) {
        return Ali1688Dp10CheckpointTransitions.afterPage(this, page);
    }

    Ali1688Dp10Checkpoint afterSealBatch(
            Ali1688HistoricalOrderProvider.Partition sealed,
            Ali1688Dp10SealBatch batch
    ) {
        return Ali1688Dp10CheckpointTransitions.afterSealBatch(this, sealed, batch);
    }

    Ali1688Dp10Checkpoint restartGeneration() {
        return Ali1688Dp10CheckpointTransitions.restartGeneration(this);
    }

    Ali1688Dp10Checkpoint atDetail(Ali1688Dp10PendingItem item) {
        Ali1688Dp10Checkpoint next = copy();
        next.detailPartition = item == null ? null : item.getPartition();
        next.detailPageNo = item == null ? null : item.getPageNo();
        next.detailItemOrdinal = item == null ? null : item.getItemOrdinal();
        next.validate();
        return next;
    }

    Ali1688Dp10Checkpoint withExpectedProgressVersion(long version) {
        Ali1688Dp10Checkpoint next = copy();
        next.expectedProgressVersion = version;
        next.validate();
        return next;
    }

    public boolean isScansClosed() { return scansClosed; }
    boolean isSealed() { return sealedPartitions == 2; }

    Ali1688HistoricalOrderProvider.Partition nextSealPartition() {
        if (!scansClosed || sealedPartitions >= 2) return null;
        return sealedPartitions == 0
                ? Ali1688HistoricalOrderProvider.Partition.CURRENT
                : Ali1688HistoricalOrderProvider.Partition.HISTORY;
    }

    void validate() { Ali1688Dp10CheckpointTransitions.validate(this); }
    Instant windowStart() { return parse(windowStartUtc); }
    Instant windowEnd() { return parse(windowEndUtc); }
    Ali1688Dp10Checkpoint copy() { return Ali1688Dp10CheckpointTransitions.copy(this); }

    private static Instant windowStart(
            Ali1688HistoricalOrderProvider.SyncMode mode,
            Instant highWater
    ) {
        if (mode == Ali1688HistoricalOrderProvider.SyncMode.FULL) return null;
        if (highWater == null) return Instant.EPOCH;
        Instant boundary = Instant.EPOCH.plus(SAFETY_OVERLAP);
        return highWater.isBefore(boundary) ? Instant.EPOCH : highWater.minus(SAFETY_OVERLAP);
    }

    private static Instant parse(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid DP-10 checkpoint timestamp", invalid);
        }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int value) { schemaVersion = value; }
    public Ali1688HistoricalOrderProvider.SyncMode getMode() { return mode; }
    public void setMode(Ali1688HistoricalOrderProvider.SyncMode value) { mode = value; }
    public String getWindowStartUtc() { return windowStartUtc; }
    public void setWindowStartUtc(String value) { windowStartUtc = value; }
    public String getWindowEndUtc() { return windowEndUtc; }
    public void setWindowEndUtc(String value) { windowEndUtc = value; }
    public long getGenerationNo() { return generationNo; }
    public void setGenerationNo(long value) { generationNo = value; }
    public int getScanPass() { return scanPass; }
    public void setScanPass(int value) { scanPass = value; }
    public Ali1688HistoricalOrderProvider.Partition getPartition() { return partition; }
    public void setPartition(Ali1688HistoricalOrderProvider.Partition value) { partition = value; }
    public int getPageNo() { return pageNo; }
    public void setPageNo(int value) { pageNo = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public Long getExpectedTotal() { return expectedTotal; }
    public void setExpectedTotal(Long value) { expectedTotal = value; }
    public Integer getExpectedPages() { return expectedPages; }
    public void setExpectedPages(Integer value) { expectedPages = value; }
    public long getStagedRawRowCount() { return stagedRawRowCount; }
    public void setStagedRawRowCount(long value) { stagedRawRowCount = value; }
    public Long getPassOneCurrentTotal() { return passOneCurrentTotal; }
    public void setPassOneCurrentTotal(Long value) { passOneCurrentTotal = value; }
    public Integer getPassOneCurrentPages() { return passOneCurrentPages; }
    public void setPassOneCurrentPages(Integer value) { passOneCurrentPages = value; }
    public Long getPassOneHistoryTotal() { return passOneHistoryTotal; }
    public void setPassOneHistoryTotal(Long value) { passOneHistoryTotal = value; }
    public Integer getPassOneHistoryPages() { return passOneHistoryPages; }
    public void setPassOneHistoryPages(Integer value) { passOneHistoryPages = value; }
    public void setScansClosed(boolean value) { scansClosed = value; }
    public int getSealedPartitions() { return sealedPartitions; }
    public void setSealedPartitions(int value) { sealedPartitions = value; }
    public String getSealAfterFingerprint() { return sealAfterFingerprint; }
    public void setSealAfterFingerprint(String value) { sealAfterFingerprint = value; }
    public long getSealComparedRawRows() { return sealComparedRawRows; }
    public void setSealComparedRawRows(long value) { sealComparedRawRows = value; }
    public Ali1688HistoricalOrderProvider.Partition getDetailPartition() { return detailPartition; }
    public void setDetailPartition(Ali1688HistoricalOrderProvider.Partition value) { detailPartition = value; }
    public Integer getDetailPageNo() { return detailPageNo; }
    public void setDetailPageNo(Integer value) { detailPageNo = value; }
    public Integer getDetailItemOrdinal() { return detailItemOrdinal; }
    public void setDetailItemOrdinal(Integer value) { detailItemOrdinal = value; }
    public long getExpectedProgressVersion() { return expectedProgressVersion; }
    public void setExpectedProgressVersion(long value) { expectedProgressVersion = value; }
}

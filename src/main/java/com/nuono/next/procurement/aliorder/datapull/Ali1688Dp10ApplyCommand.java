package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/** Final all-partition fact/progress commit after LIST and DETAIL are closed. */
public final class Ali1688Dp10ApplyCommand {
    private final DataPullTask task;
    private final Ali1688HistoricalOrderAuthorizationRow authorization;
    private final long generationNo;
    private final long currentExpectedTotal;
    private final int currentExpectedPages;
    private final long historyExpectedTotal;
    private final int historyExpectedPages;
    private final long expectedProgressVersion;
    private final Instant windowEnd;
    private final LocalDateTime nowUtc;

    public Ali1688Dp10ApplyCommand(
            DataPullTask task,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            long generationNo,
            long currentExpectedTotal,
            int currentExpectedPages,
            long historyExpectedTotal,
            int historyExpectedPages,
            long expectedProgressVersion,
            Instant windowEnd,
            LocalDateTime nowUtc
    ) {
        this.task = Objects.requireNonNull(task, "task");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        if (generationNo < 1 || currentExpectedTotal < 0 || currentExpectedPages < 1
                || historyExpectedTotal < 0 || historyExpectedPages < 1
                || expectedProgressVersion < 0L || windowEnd == null || nowUtc == null) {
            throw new IllegalArgumentException("invalid DP-10 final apply command");
        }
        this.generationNo = generationNo;
        this.currentExpectedTotal = currentExpectedTotal;
        this.currentExpectedPages = currentExpectedPages;
        this.historyExpectedTotal = historyExpectedTotal;
        this.historyExpectedPages = historyExpectedPages;
        this.expectedProgressVersion = expectedProgressVersion;
        this.windowEnd = windowEnd;
        this.nowUtc = nowUtc;
    }

    public DataPullTask getTask() { return task; }
    public Ali1688HistoricalOrderAuthorizationRow getAuthorization() { return authorization; }
    public long getGenerationNo() { return generationNo; }
    public long getCurrentExpectedTotal() { return currentExpectedTotal; }
    public int getCurrentExpectedPages() { return currentExpectedPages; }
    public long getHistoryExpectedTotal() { return historyExpectedTotal; }
    public int getHistoryExpectedPages() { return historyExpectedPages; }
    public long getExpectedProgressVersion() { return expectedProgressVersion; }
    public Instant getWindowEnd() { return windowEnd; }
    public LocalDateTime getNowUtc() { return nowUtc; }
}

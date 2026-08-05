package com.nuono.next.procurement.aliorder.datapull;

/** Result of comparing at most one fixed-size fingerprint-count range. */
public final class Ali1688Dp10SealBatch {
    private final boolean matching;
    private final boolean exhausted;
    private final String lastFingerprint;
    private final long matchedRawRows;
    private final int countRowsRead;

    private Ali1688Dp10SealBatch(
            boolean matching,
            boolean exhausted,
            String lastFingerprint,
            long matchedRawRows,
            int countRowsRead
    ) {
        if (matchedRawRows < 0L || countRowsRead < 0) {
            throw new IllegalArgumentException("invalid DP-10 seal batch counts");
        }
        this.matching = matching;
        this.exhausted = exhausted;
        this.lastFingerprint = lastFingerprint;
        this.matchedRawRows = matchedRawRows;
        this.countRowsRead = countRowsRead;
    }

    public static Ali1688Dp10SealBatch matching(
            boolean exhausted,
            String lastFingerprint,
            long matchedRawRows,
            int countRowsRead
    ) {
        return new Ali1688Dp10SealBatch(
                true, exhausted, lastFingerprint, matchedRawRows, countRowsRead);
    }

    public static Ali1688Dp10SealBatch drift(int countRowsRead) {
        return new Ali1688Dp10SealBatch(false, false, null, 0L, countRowsRead);
    }

    public boolean isMatching() { return matching; }
    public boolean isExhausted() { return exhausted; }
    public String getLastFingerprint() { return lastFingerprint; }
    public long getMatchedRawRows() { return matchedRawRows; }
    public int getCountRowsRead() { return countRowsRead; }
}

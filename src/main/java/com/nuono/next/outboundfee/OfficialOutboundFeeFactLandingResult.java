package com.nuono.next.outboundfee;

public class OfficialOutboundFeeFactLandingResult {

    private int insertedCount;
    private int unchangedCount;
    private int supersededCount;
    private int skippedCount;
    private int conflictCount;

    void inserted() {
        insertedCount++;
    }

    void unchanged() {
        unchangedCount++;
    }

    void superseded() {
        supersededCount++;
    }

    void superseded(int count) {
        supersededCount += count;
    }

    void skipped() {
        skippedCount++;
    }

    void conflict() {
        conflictCount++;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public int getSupersededCount() {
        return supersededCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }
}

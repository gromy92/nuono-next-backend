package com.nuono.next.logisticsquote;

public class LogisticsQuoteFactLandingResult {

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

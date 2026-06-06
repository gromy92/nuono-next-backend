package com.nuono.next.competitoranalysis;

public class CompetitorKeywordRefreshResult {
    private final boolean success;
    private final int candidateUpsertedCount;
    private final int rankFactWrittenCount;
    private final String errorCode;
    private final String errorMessage;

    private CompetitorKeywordRefreshResult(
            boolean success,
            int candidateUpsertedCount,
            int rankFactWrittenCount,
            String errorCode,
            String errorMessage
    ) {
        this.success = success;
        this.candidateUpsertedCount = candidateUpsertedCount;
        this.rankFactWrittenCount = rankFactWrittenCount;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static CompetitorKeywordRefreshResult success(int candidateUpsertedCount, int rankFactWrittenCount) {
        return new CompetitorKeywordRefreshResult(true, candidateUpsertedCount, rankFactWrittenCount, null, null);
    }

    public static CompetitorKeywordRefreshResult failure(String errorCode, String errorMessage) {
        return new CompetitorKeywordRefreshResult(false, 0, 0, errorCode, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public int getCandidateUpsertedCount() { return candidateUpsertedCount; }
    public int getRankFactWrittenCount() { return rankFactWrittenCount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}

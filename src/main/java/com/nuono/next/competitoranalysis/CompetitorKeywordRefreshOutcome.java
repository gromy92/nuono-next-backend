package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;

public class CompetitorKeywordRefreshOutcome {
    private boolean success;
    private String providerStatus;
    private Integer resultCount;
    private Integer candidateUpsertedCount;
    private Integer rankFactWrittenCount;
    private String sourceUrl;
    private String parserVersion;
    private Integer providerHttpStatus;
    private String responseHash;
    private LocalDateTime capturedAt;
    private String errorCode;
    private String errorMessage;

    public static CompetitorKeywordRefreshOutcome success(Integer resultCount) {
        CompetitorKeywordRefreshOutcome outcome = new CompetitorKeywordRefreshOutcome();
        outcome.success = true;
        outcome.providerStatus = "SUCCESS";
        outcome.resultCount = resultCount == null ? 0 : resultCount;
        outcome.candidateUpsertedCount = 0;
        outcome.rankFactWrittenCount = 0;
        return outcome;
    }

    public static CompetitorKeywordRefreshOutcome failure(String errorCode, String errorMessage) {
        CompetitorKeywordRefreshOutcome outcome = new CompetitorKeywordRefreshOutcome();
        outcome.success = false;
        outcome.providerStatus = "FAILED";
        outcome.resultCount = 0;
        outcome.candidateUpsertedCount = 0;
        outcome.rankFactWrittenCount = 0;
        outcome.errorCode = errorCode;
        outcome.errorMessage = errorMessage;
        return outcome;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
    public Integer getCandidateUpsertedCount() { return candidateUpsertedCount; }
    public void setCandidateUpsertedCount(Integer candidateUpsertedCount) { this.candidateUpsertedCount = candidateUpsertedCount; }
    public Integer getRankFactWrittenCount() { return rankFactWrittenCount; }
    public void setRankFactWrittenCount(Integer rankFactWrittenCount) { this.rankFactWrittenCount = rankFactWrittenCount; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

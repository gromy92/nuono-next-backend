package com.nuono.next.competitoranalysis;

final class CompetitorRefreshCompletion {
    private String runStatus;
    private int keywordSuccess;
    private int keywordFailed;
    private int candidateUpsertedCount;
    private int rankFactWrittenCount;
    private String runErrorCode;
    private String runErrorMessage;
    private Long actorUserId;
    private String taskErrorCode;
    private String taskResultJson;
    private String taskMessage;

    static CompetitorRefreshCompletion create(
            String status,
            int success,
            int failed,
            int candidateCount,
            int rankFactCount,
            String errorCode,
            String errorMessage,
            Long actorUserId,
            String taskErrorCode,
            String taskResultJson,
            String taskMessage
    ) {
        CompetitorRefreshCompletion value = new CompetitorRefreshCompletion();
        value.runStatus = status;
        value.keywordSuccess = success;
        value.keywordFailed = failed;
        value.candidateUpsertedCount = candidateCount;
        value.rankFactWrittenCount = rankFactCount;
        value.runErrorCode = errorCode;
        value.runErrorMessage = errorMessage;
        value.actorUserId = actorUserId;
        value.taskErrorCode = taskErrorCode;
        value.taskResultJson = taskResultJson;
        value.taskMessage = taskMessage;
        return value;
    }

    String getRunStatus() { return runStatus; }
    void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    int getKeywordSuccess() { return keywordSuccess; }
    void setKeywordSuccess(int value) { keywordSuccess = value; }
    int getKeywordFailed() { return keywordFailed; }
    void setKeywordFailed(int value) { keywordFailed = value; }
    int getCandidateUpsertedCount() { return candidateUpsertedCount; }
    void setCandidateUpsertedCount(int value) { candidateUpsertedCount = value; }
    int getRankFactWrittenCount() { return rankFactWrittenCount; }
    void setRankFactWrittenCount(int value) { rankFactWrittenCount = value; }
    String getRunErrorCode() { return runErrorCode; }
    void setRunErrorCode(String value) { runErrorCode = value; }
    String getRunErrorMessage() { return runErrorMessage; }
    void setRunErrorMessage(String value) { runErrorMessage = value; }
    Long getActorUserId() { return actorUserId; }
    void setActorUserId(Long value) { actorUserId = value; }
    String getTaskErrorCode() { return taskErrorCode; }
    void setTaskErrorCode(String value) { taskErrorCode = value; }
    String getTaskResultJson() { return taskResultJson; }
    void setTaskResultJson(String value) { taskResultJson = value; }
    String getTaskMessage() { return taskMessage; }
    void setTaskMessage(String value) { taskMessage = value; }
}

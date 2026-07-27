package com.nuono.next.competitoranalysis;

final class CompetitorRefreshRunResultSupport {
    private CompetitorRefreshRunResultSupport() {
    }

    static String status(
            int keywordSuccess,
            int keywordFailed,
            CompetitorProductDetailRefreshResult detailResult
    ) {
        int detailSuccess = detailResult == null || detailResult.getSucceededCount() == 0 ? 0 : 1;
        int detailFailed = detailResult == null || detailResult.getFailedCount() == 0 ? 0 : 1;
        int success = keywordSuccess + detailSuccess;
        int failed = keywordFailed + detailFailed;
        if (failed <= 0) {
            return "SUCCEEDED";
        }
        return success > 0 ? "PARTIAL_FAILED" : "FAILED";
    }

    static String resultJson(
            CompetitorRefreshExecutionMode executionMode,
            String status,
            int keywordSuccess,
            int keywordFailed,
            CompetitorProductDetailRefreshResult detailResult,
            int keywordRetried,
            int keywordRetryRecovered
    ) {
        CompetitorRefreshExecutionMode mode = executionMode == null
                ? CompetitorRefreshExecutionMode.FULL_MANUAL
                : executionMode;
        CompetitorProductDetailRefreshResult detail = detailResult == null
                ? CompetitorProductDetailRefreshResult.empty()
                : detailResult;
        return "{"
                + "\"status\":\"" + status + "\""
                + ",\"triggerMode\":\"" + json(mode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(mode.taskKey()) + "\""
                + ",\"rankRefresh\":" + mode.runsRank()
                + ",\"detailRefresh\":" + mode.runsDetail()
                + ",\"keywordSuccess\":" + keywordSuccess
                + ",\"keywordFailed\":" + keywordFailed
                + ",\"detailAttempted\":" + detail.getAttemptedCount()
                + ",\"detailSuccess\":" + detail.getSucceededCount()
                + ",\"detailFailed\":" + detail.getFailedCount()
                + ",\"keywordRetried\":" + keywordRetried
                + ",\"keywordRetryRecovered\":" + keywordRetryRecovered
                + "}";
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package com.nuono.next.filemanagement.parse;

import java.util.Locale;

class FileParseWorkflowStatePolicy {

    static final String STALE_RETRY_FAILURE_CODE = "PARSE_STALE_RETRYING";
    static final String STALE_TIMEOUT_FAILURE_CODE = "PARSE_STALE_TIMEOUT";

    String stepStatus(String taskStatus, String activeStatus, boolean completed) {
        if (completed) {
            return "succeeded";
        }
        if (!hasText(taskStatus)) {
            return "pending";
        }
        if ("failed".equals(taskStatus)) {
            return "failed";
        }
        return activeStatus.equals(taskStatus) ? "running" : "pending";
    }

    boolean shouldScheduleRetry(Integer previousAttemptCount, String failureCode, String failureMessage, int maxAttempts) {
        if (!isRetryableAiFailure(failureCode, failureMessage)) {
            return false;
        }
        int previousAttempts = previousAttemptCount == null ? 0 : previousAttemptCount;
        int currentAttempt = previousAttempts + 1;
        return currentAttempt < Math.max(1, maxAttempts);
    }

    StaleRecoveryDecision staleRecoveryDecision(Integer parseAttemptCount, int maxAttempts, int staleTimeoutSeconds) {
        int attemptCount = parseAttemptCount == null ? 0 : parseAttemptCount;
        int normalizedMaxAttempts = Math.max(1, maxAttempts);
        String timeoutMessage = "解析任务超过 "
                + staleTimeoutSeconds
                + " 秒没有完成，系统已判定为超时。";
        if (attemptCount >= normalizedMaxAttempts) {
            return new StaleRecoveryDecision(
                    true,
                    STALE_TIMEOUT_FAILURE_CODE,
                    timeoutMessage + "已达到最大自动重试次数 " + normalizedMaxAttempts + " 次，请人工检查文件或 AI 服务。"
            );
        }
        return new StaleRecoveryDecision(
                false,
                STALE_RETRY_FAILURE_CODE,
                timeoutMessage + "系统正在自动重试第 " + (attemptCount + 1) + " 次。"
        );
    }

    RetryableFailureRecoveryDecision retryableFailureDecision(
            Integer parseAttemptCount,
            int maxAttempts,
            String failureMessage
    ) {
        int attemptCount = parseAttemptCount == null ? 0 : parseAttemptCount;
        int normalizedMaxAttempts = Math.max(1, maxAttempts);
        if (attemptCount >= normalizedMaxAttempts) {
            return new RetryableFailureRecoveryDecision(
                    true,
                    "PARSE_AUTO_RETRY_EXHAUSTED",
                    "解析任务自动重试已达到最大次数 "
                            + normalizedMaxAttempts
                            + " 次。最后一次失败原因："
                            + trimFailureMessage(failureMessage)
            );
        }
        return new RetryableFailureRecoveryDecision(false, null, null);
    }

    int retryDelaySeconds(int configuredRetryDelaySeconds) {
        return Math.max(10, Math.min(configuredRetryDelaySeconds, 600));
    }

    private boolean isRetryableAiFailure(String failureCode, String failureMessage) {
        if (!hasText(failureCode)) {
            return false;
        }
        String normalizedMessage = nullToEmpty(failureMessage).toLowerCase(Locale.ROOT);
        if ("OPENAI_HTTP_429".equals(failureCode)
                && (normalizedMessage.contains("usage_limit_reached")
                || normalizedMessage.contains("usage limit has been reached"))) {
            return false;
        }
        return "OPENAI_REQUEST_TIMEOUT".equals(failureCode)
                || "OPENAI_HTTP_429".equals(failureCode)
                || "OPENAI_HTTP_500".equals(failureCode)
                || "OPENAI_HTTP_502".equals(failureCode)
                || "OPENAI_HTTP_503".equals(failureCode)
                || "OPENAI_HTTP_504".equals(failureCode);
    }

    private String trimFailureMessage(String message) {
        String normalized = nullToEmpty(message).trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static class StaleRecoveryDecision {

        private final boolean finalFailure;
        private final String failureCode;
        private final String failureMessage;

        private StaleRecoveryDecision(boolean finalFailure, String failureCode, String failureMessage) {
            this.finalFailure = finalFailure;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
        }

        boolean isFinalFailure() {
            return finalFailure;
        }

        String getFailureCode() {
            return failureCode;
        }

        String getFailureMessage() {
            return failureMessage;
        }
    }

    static class RetryableFailureRecoveryDecision {

        private final boolean finalFailure;
        private final String failureCode;
        private final String failureMessage;

        private RetryableFailureRecoveryDecision(boolean finalFailure, String failureCode, String failureMessage) {
            this.finalFailure = finalFailure;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
        }

        boolean isFinalFailure() {
            return finalFailure;
        }

        String getFailureCode() {
            return failureCode;
        }

        String getFailureMessage() {
            return failureMessage;
        }
    }
}

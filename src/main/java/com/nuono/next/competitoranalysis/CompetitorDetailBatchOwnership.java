package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import org.springframework.util.StringUtils;

final class CompetitorDetailBatchOwnership {
    private CompetitorDetailBatchOwnership() {
    }

    static long chainRoot(String payloadJson, Long fallbackRunId) {
        try {
            Long root = CompetitorDetailRetryPayload.fromJson(payloadJson)
                    .getRootRunId();
            return root == null ? requiredRunId(fallbackRunId) : root;
        } catch (CompetitorDetailRetryPayloadException exception) {
            return requiredRunId(fallbackRunId);
        }
    }

    static String batchKey(String payloadJson) {
        OperationalTask task = task(payloadJson);
        try {
            return CompetitorRefreshRecoveryPayload.batchKey(task);
        } catch (CompetitorRefreshRecoveryPayloadException exception) {
            return null;
        }
    }

    static Key strictCandidate(
            CompetitorDetailTakeoverCandidateRow candidate
    ) {
        if (candidate == null || candidate.getRunId() == null) {
            return null;
        }
        try {
            CompetitorDetailRetryPayload payload =
                    CompetitorDetailRetryPayload.fromJson(
                            candidate.getPayloadJson()
                    );
            String batchKey = CompetitorRefreshRecoveryPayload.batchKey(
                    task(candidate.getPayloadJson())
            );
            if (!StringUtils.hasText(batchKey)) {
                return null;
            }
            Long rootRunId = payload.getRootRunId();
            return new Key(
                    rootRunId == null
                            ? requiredRunId(candidate.getRunId())
                            : rootRunId,
                    batchKey
            );
        } catch (CompetitorDetailRetryPayloadException
                | CompetitorRefreshRecoveryPayloadException exception) {
            return null;
        }
    }

    private static OperationalTask task(String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setPayloadJson(payloadJson);
        return task;
    }

    private static long requiredRunId(Long runId) {
        if (runId == null || runId <= 0) {
            throw new IllegalStateException(
                    "Competitor detail ownership candidate has no valid run id."
            );
        }
        return runId;
    }

    static final class Key {
        final long rootRunId;
        final String batchKey;

        private Key(long rootRunId, String batchKey) {
            this.rootRunId = rootRunId;
            this.batchKey = batchKey;
        }
    }
}

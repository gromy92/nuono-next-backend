package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryRequestLedger {
    static final String UNKNOWN_OUTCOME = "DETAIL_REQUEST_OUTCOME_UNKNOWN";
    private static final String UNKNOWN_MESSAGE =
            "上次竞品详情请求已发出，但结果未确认，已按失败消耗本次尝试。";

    private CompetitorDetailRetryRequestLedger() {
    }

    static CompetitorDetailRetryPayload begin(
            CompetitorDetailRetryPayload payload,
            CompetitorProductDetailTarget target,
            Long runId
    ) {
        CompetitorDetailRetryState current = requirePending(payload, target);
        if (current.isRequestInFlight()) {
            throw invalid("Detail request already has a durable reservation.");
        }
        CompetitorDetailRetryPayload next = payload.copy();
        next.setRetryStates(replace(
                payload.getRetryStates(),
                target,
                current.withRequestInFlight(true)
        ));
        int count = payload.getDetailRequestAttemptCount();
        if (count == Integer.MAX_VALUE) {
            throw invalid("Detail request attempt counter is exhausted.");
        }
        next.setDetailRequestAttemptCount(count + 1);
        next.setRootRunId(firstNonNull(next.getRootRunId(), runId));
        next.setRetryOfRunId(runId);
        return next;
    }

    static CompetitorDetailRetryPayload failure(
            CompetitorDetailRetryPayload payload,
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage,
            boolean requested,
            Long runId,
            LocalDateTime failedAt,
            CompetitorDetailRetryPolicy policy
    ) {
        CompetitorDetailRetryState current = requirePending(payload, target);
        if (requested && !current.isRequestInFlight()) {
            throw invalid("Detail request failure has no durable reservation.");
        }
        CompetitorDetailRetryPayload next = payload.copy();
        List<CompetitorDetailRetryState> states = without(
                payload.getRetryStates(), target
        );
        Optional<CompetitorDetailRetryState> planned = policy.planTargetRetry(
                current.withRequestInFlight(false),
                target,
                errorCode,
                errorMessage,
                false,
                failedAt,
                null,
                next.getMaxRetryAttempts()
        );
        if (planned.isPresent()) {
            states.add(planned.get());
        } else {
            recordTerminal(next, errorCode, errorMessage);
        }
        next.setRetryStates(states);
        next.setLastErrorCode(errorCode);
        next.setMessage(errorMessage);
        next.setRootRunId(firstNonNull(next.getRootRunId(), runId));
        next.setRetryOfRunId(runId);
        return next;
    }

    static CompetitorDetailRetryPayload recoverInFlight(
            CompetitorDetailRetryPayload payload,
            LocalDateTime recoveredAt,
            CompetitorDetailRetryPolicy policy
    ) {
        if (!hasInFlight(payload.getRetryStates())) {
            return null;
        }
        CompetitorDetailRetryPayload next = payload.copy();
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        for (CompetitorDetailRetryState current : payload.getRetryStates()) {
            if (!current.isRequestInFlight()) {
                states.add(current);
                continue;
            }
            Optional<CompetitorDetailRetryState> planned = policy.planTargetRetry(
                    current.withRequestInFlight(false),
                    current.getTarget(),
                    UNKNOWN_OUTCOME,
                    UNKNOWN_MESSAGE,
                    false,
                    recoveredAt,
                    null,
                    next.getMaxRetryAttempts()
            );
            if (planned.isPresent()) {
                states.add(planned.get());
            } else {
                recordTerminal(next, UNKNOWN_OUTCOME, UNKNOWN_MESSAGE);
            }
        }
        next.setRetryStates(states);
        next.setLastErrorCode(UNKNOWN_OUTCOME);
        next.setMessage(UNKNOWN_MESSAGE);
        return next;
    }

    private static CompetitorDetailRetryState requirePending(
            CompetitorDetailRetryPayload payload,
            CompetitorProductDetailTarget target
    ) {
        CompetitorDetailRetryState state =
                payload == null ? null : payload.state(target);
        if (state == null) {
            throw invalid("Detail retry target is not pending.");
        }
        return state;
    }

    private static List<CompetitorDetailRetryState> replace(
            List<CompetitorDetailRetryState> states,
            CompetitorProductDetailTarget target,
            CompetitorDetailRetryState replacement
    ) {
        List<CompetitorDetailRetryState> values = new ArrayList<>();
        for (CompetitorDetailRetryState state : states) {
            values.add(target.identityKey().equals(state.identityKey())
                    ? replacement
                    : state);
        }
        return values;
    }

    private static List<CompetitorDetailRetryState> without(
            List<CompetitorDetailRetryState> states,
            CompetitorProductDetailTarget target
    ) {
        List<CompetitorDetailRetryState> values = new ArrayList<>();
        for (CompetitorDetailRetryState state : states) {
            if (!target.identityKey().equals(state.identityKey())) {
                values.add(state);
            }
        }
        return values;
    }

    private static boolean hasInFlight(List<CompetitorDetailRetryState> states) {
        for (CompetitorDetailRetryState state : states) {
            if (state.isRequestInFlight()) {
                return true;
            }
        }
        return false;
    }

    private static void recordTerminal(
            CompetitorDetailRetryPayload payload,
            String errorCode,
            String errorMessage
    ) {
        payload.setDetailTerminalFailedCount(
                payload.getDetailTerminalFailedCount() + 1
        );
        payload.setDetailTerminalErrorCode(firstNonBlank(
                payload.getDetailTerminalErrorCode(), errorCode
        ));
        payload.setDetailTerminalErrorMessage(firstNonBlank(
                payload.getDetailTerminalErrorMessage(), errorMessage
        ));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static CompetitorDetailRetryPayloadException invalid(String message) {
        return new CompetitorDetailRetryPayloadException(message);
    }
}

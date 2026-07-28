package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CompetitorDetailRetryStateJson {
    static final String STATE_FIELD = "detailRetryStates";
    static final String LEGACY_TARGET_FIELD = "failedDetailTargets";

    private CompetitorDetailRetryStateJson() {
    }

    static List<CompetitorDetailRetryState> readStates(JsonNode value, int maximumAttempts) {
        requireNonEmptyArray(value, STATE_FIELD);
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                throw invalid("Invalid detail retry state.");
            }
            CompetitorProductDetailTarget target =
                    CompetitorDetailRetryJsonSupport.target(item);
            if (!identities.add(target.identityKey())) {
                throw invalid("Duplicate detail retry target identity.");
            }
            int attempt = CompetitorDetailRetryJsonSupport.requiredInt(
                    item,
                    "retryAttempt",
                    0,
                    maximumAttempts
            );
            states.add(new CompetitorDetailRetryState(
                    target,
                    attempt,
                    CompetitorDetailRetryJsonSupport.requiredDateTime(
                            item,
                            "retryNotBefore"
                    ),
                    CompetitorDetailRetryJsonSupport.optionalText(item, "errorCode"),
                    CompetitorDetailRetryJsonSupport.optionalText(item, "errorMessage"),
                    CompetitorDetailRetryJsonSupport.optionalBoolean(
                            item, "requestInFlight", false
                    )
            ));
        }
        requireAtMostOneInFlight(states);
        return states;
    }

    static List<CompetitorProductDetailTarget> readTargets(JsonNode value) {
        requireNonEmptyArray(value, LEGACY_TARGET_FIELD);
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        for (JsonNode item : value) {
            CompetitorProductDetailTarget target =
                    CompetitorDetailRetryJsonSupport.target(item);
            if (!identities.add(target.identityKey())) {
                throw invalid("Duplicate failed detail target identity.");
            }
            targets.add(target);
        }
        return targets;
    }

    static List<CompetitorDetailRetryState> migrateLegacy(
            List<CompetitorProductDetailTarget> targets,
            int attempt,
            LocalDateTime notBefore,
            String errorCode,
            String errorMessage
    ) {
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        for (CompetitorProductDetailTarget target : targets) {
            states.add(new CompetitorDetailRetryState(
                    target,
                    attempt,
                    notBefore,
                    errorCode,
                    errorMessage
            ));
        }
        return states;
    }

    static void writeArrays(ObjectNode payload, List<CompetitorDetailRetryState> states) {
        ArrayNode stateArray = payload.putArray(STATE_FIELD);
        ArrayNode targetArray = payload.putArray(LEGACY_TARGET_FIELD);
        for (CompetitorDetailRetryState state : validated(states)) {
            ObjectNode stateNode =
                    CompetitorDetailRetryJsonSupport.targetNode(state.getTarget());
            stateNode.put("retryAttempt", state.getRetryAttempt());
            CompetitorDetailRetryJsonSupport.putDateTime(
                    stateNode,
                    "retryNotBefore",
                    state.getRetryNotBefore()
            );
            CompetitorDetailRetryJsonSupport.putText(
                    stateNode,
                    "errorCode",
                    state.getErrorCode()
            );
            CompetitorDetailRetryJsonSupport.putText(
                    stateNode,
                    "errorMessage",
                    state.getErrorMessage()
            );
            stateNode.put("requestInFlight", state.isRequestInFlight());
            stateArray.add(stateNode);
            targetArray.add(
                    CompetitorDetailRetryJsonSupport.targetNode(state.getTarget())
            );
        }
    }

    static List<CompetitorDetailRetryState> unique(
            List<CompetitorDetailRetryState> values
    ) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        for (CompetitorDetailRetryState state : values) {
            validateState(state);
            if (!identities.add(state.identityKey())) {
                throw invalid("Duplicate detail retry target identity.");
            }
            states.add(state.copy());
        }
        requireAtMostOneInFlight(states);
        return states;
    }

    static String stateCanonical(List<CompetitorDetailRetryState> states) {
        StringBuilder value = new StringBuilder();
        for (CompetitorDetailRetryState state : validated(states)) {
            value.append(CompetitorDetailRetryJsonSupport.part(
                    CompetitorDetailRetryJsonSupport.targetCanonical(state.getTarget())
            ));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getRetryAttempt()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getRetryNotBefore()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getErrorCode()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getErrorMessage()));
        }
        return value.toString();
    }

    static String sealedStateCanonical(List<CompetitorDetailRetryState> states) {
        StringBuilder value = new StringBuilder();
        for (CompetitorDetailRetryState state : validated(states)) {
            value.append(CompetitorDetailRetryJsonSupport.part(
                    CompetitorDetailRetryJsonSupport.targetCanonical(state.getTarget())
            ));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getRetryAttempt()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getRetryNotBefore()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getErrorCode()));
            value.append(CompetitorDetailRetryJsonSupport.part(state.getErrorMessage()));
            value.append(CompetitorDetailRetryJsonSupport.part(
                    state.isRequestInFlight()
            ));
        }
        return value.toString();
    }

    static boolean hasRequestReservation(JsonNode value) {
        if (value == null || !value.isArray()) {
            return false;
        }
        for (JsonNode item : value) {
            if (item != null && item.has("requestInFlight")) {
                return true;
            }
        }
        return false;
    }

    static String legacyCanonical(
            List<CompetitorProductDetailTarget> targets,
            int attempt,
            LocalDateTime notBefore
    ) {
        StringBuilder value = new StringBuilder();
        value.append(CompetitorDetailRetryJsonSupport.part(attempt));
        value.append(CompetitorDetailRetryJsonSupport.part(notBefore));
        for (CompetitorProductDetailTarget target : targets) {
            value.append(CompetitorDetailRetryJsonSupport.part(
                    CompetitorDetailRetryJsonSupport.targetCanonical(target)
            ));
        }
        return value.toString();
    }

    static List<CompetitorProductDetailTarget> targets(
            List<CompetitorDetailRetryState> states
    ) {
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorDetailRetryState state : validated(states)) {
            targets.add(state.getTarget());
        }
        return targets;
    }

    static int maximumAttempt(List<CompetitorDetailRetryState> states) {
        int maximum = 0;
        for (CompetitorDetailRetryState state : validated(states)) {
            maximum = Math.max(maximum, state.getRetryAttempt());
        }
        return maximum;
    }

    static LocalDateTime earliestWake(List<CompetitorDetailRetryState> states) {
        LocalDateTime earliest = null;
        for (CompetitorDetailRetryState state : validated(states)) {
            if (earliest == null || state.getRetryNotBefore().isBefore(earliest)) {
                earliest = state.getRetryNotBefore();
            }
        }
        return earliest;
    }

    static LocalDateTime latestWake(List<CompetitorDetailRetryState> states) {
        LocalDateTime latest = null;
        for (CompetitorDetailRetryState state : validated(states)) {
            if (latest == null || state.getRetryNotBefore().isAfter(latest)) {
                latest = state.getRetryNotBefore();
            }
        }
        return latest;
    }

    private static List<CompetitorDetailRetryState> validated(
            List<CompetitorDetailRetryState> states
    ) {
        if (states == null || states.isEmpty()) {
            throw invalid("Detail retry states must not be empty.");
        }
        for (CompetitorDetailRetryState state : states) {
            validateState(state);
        }
        return states;
    }

    private static void validateState(CompetitorDetailRetryState state) {
        if (state == null || state.getRetryNotBefore() == null) {
            throw invalid("Detail retry state requires retryNotBefore.");
        }
        if (state.getRetryAttempt() < 0
                || state.getRetryAttempt() > CompetitorDetailRetryPolicy.MAX_RETRY_ATTEMPTS) {
            throw invalid("Detail retry state attempt is outside the allowed range.");
        }
        CompetitorDetailRetryJsonSupport.validateTarget(state.getTarget());
    }

    private static void requireAtMostOneInFlight(
            List<CompetitorDetailRetryState> states
    ) {
        int inFlight = 0;
        for (CompetitorDetailRetryState state : states) {
            if (state.isRequestInFlight() && ++inFlight > 1) {
                throw invalid("Only one detail request may be in flight.");
            }
        }
    }

    private static void requireNonEmptyArray(JsonNode value, String field) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw invalid(field + " must be a non-empty JSON array.");
        }
    }

    private static CompetitorDetailRetryPayloadException invalid(String message) {
        return CompetitorDetailRetryJsonSupport.invalid(message);
    }
}

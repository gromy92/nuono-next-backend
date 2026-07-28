package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryStateJson {
    private static final String STATE_FIELD = "detailRetryStates";
    private static final String LEGACY_TARGET_FIELD = "failedDetailTargets";

    private CompetitorDetailRetryStateJson() {
    }

    static List<CompetitorDetailRetryState> read(
            ObjectNode payload,
            int legacyAttempt,
            LocalDateTime legacyNotBefore,
            String legacyErrorCode,
            String legacyErrorMessage
    ) {
        JsonNode states = payload.get(STATE_FIELD);
        if (states != null && !states.isNull()) {
            return readStates(states);
        }
        List<CompetitorDetailRetryState> migrated = new ArrayList<>();
        for (CompetitorProductDetailTarget target : readTargets(payload.get(LEGACY_TARGET_FIELD))) {
            migrated.add(new CompetitorDetailRetryState(
                    target,
                    legacyAttempt,
                    legacyNotBefore,
                    legacyErrorCode,
                    legacyErrorMessage
            ));
        }
        return migrated;
    }

    static void write(ObjectNode payload, List<CompetitorDetailRetryState> states) {
        ArrayNode stateArray = payload.putArray(STATE_FIELD);
        ArrayNode targetArray = payload.putArray(LEGACY_TARGET_FIELD);
        for (CompetitorDetailRetryState state : unique(states)) {
            if (state == null || state.getTarget() == null) {
                continue;
            }
            ObjectNode stateNode = targetNode(state.getTarget());
            stateNode.put("retryAttempt", state.getRetryAttempt());
            putDateTime(stateNode, "retryNotBefore", state.getRetryNotBefore());
            putText(stateNode, "errorCode", state.getErrorCode());
            putText(stateNode, "errorMessage", state.getErrorMessage());
            stateArray.add(stateNode);
            targetArray.add(targetNode(state.getTarget()));
        }
    }

    static List<CompetitorDetailRetryState> unique(
            List<CompetitorDetailRetryState> values
    ) {
        Map<String, CompetitorDetailRetryState> unique = new LinkedHashMap<>();
        if (values != null) {
            for (CompetitorDetailRetryState state : values) {
                if (state != null && state.getTarget() != null) {
                    unique.putIfAbsent(state.identityKey(), state.copy());
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    static LocalDateTime dateTime(JsonNode value) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw new CompetitorDetailRetryPayloadException(
                    "Invalid retryNotBefore value.",
                    exception
            );
        }
    }

    private static List<CompetitorDetailRetryState> readStates(JsonNode value) {
        requireArray(value, STATE_FIELD);
        List<CompetitorDetailRetryState> states = new ArrayList<>();
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                throw invalid("Invalid detail retry state.");
            }
            states.add(new CompetitorDetailRetryState(
                    target(item),
                    nonNegativeInt(item.get("retryAttempt"), 0),
                    dateTime(item.get("retryNotBefore")),
                    text(item.get("errorCode")),
                    text(item.get("errorMessage"))
            ));
        }
        return unique(states);
    }

    private static List<CompetitorProductDetailTarget> readTargets(JsonNode value) {
        if (value == null || value.isNull()) {
            return new ArrayList<>();
        }
        requireArray(value, LEGACY_TARGET_FIELD);
        Map<String, CompetitorProductDetailTarget> unique = new LinkedHashMap<>();
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                throw invalid("Invalid failed detail target.");
            }
            CompetitorProductDetailTarget target = target(item);
            unique.putIfAbsent(target.identityKey(), target);
        }
        return new ArrayList<>(unique.values());
    }

    private static CompetitorProductDetailTarget target(JsonNode item) {
        CompetitorProductDetailTarget target = new CompetitorProductDetailTarget();
        target.setSubjectType(text(item.get("subjectType")));
        target.setCompetitorProductId(nullableLong(item.get("competitorProductId")));
        target.setNoonProductCode(text(item.get("noonProductCode")));
        target.setCanonicalUrl(text(item.get("canonicalUrl")));
        return target;
    }

    private static ObjectNode targetNode(CompetitorProductDetailTarget target) {
        ObjectNode value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        putText(value, "subjectType", target.getSubjectType());
        putLong(value, "competitorProductId", target.getCompetitorProductId());
        putText(value, "noonProductCode", target.getNoonProductCode());
        putText(value, "canonicalUrl", target.getCanonicalUrl());
        return value;
    }

    private static void requireArray(JsonNode value, String field) {
        if (value == null || !value.isArray()) {
            throw invalid(field + " must be a JSON array.");
        }
    }

    private static CompetitorDetailRetryPayloadException invalid(String message) {
        return new CompetitorDetailRetryPayloadException(message);
    }

    private static int nonNegativeInt(JsonNode value, int fallback) {
        return value == null || value.isNull() ? fallback : Math.max(0, value.asInt(fallback));
    }

    private static Long nullableLong(JsonNode value) {
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : normalize(value.asText());
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void putDateTime(ObjectNode target, String name, LocalDateTime value) {
        if (value != null) {
            target.put(name, value.toString());
        }
    }

    private static void putLong(ObjectNode target, String name, Long value) {
        if (value != null) {
            target.put(name, value);
        }
    }

    private static void putText(ObjectNode target, String name, String value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}

package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

final class CompetitorDetailRetryJsonSupport {
    private CompetitorDetailRetryJsonSupport() {
    }

    static int optionalInt(
            ObjectNode payload,
            String field,
            int fallback,
            int minimum,
            int maximum
    ) {
        JsonNode value = payload.get(field);
        if (value == null) {
            return fallback;
        }
        return integer(value, field, minimum, maximum);
    }

    static int requiredInt(JsonNode payload, String field, int minimum, int maximum) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull()) {
            throw invalid(field + " is required.");
        }
        return integer(value, field, minimum, maximum);
    }

    static Long optionalPositiveLong(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return positiveLong(value, field);
    }

    static Long optionalPositiveLong(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return positiveLong(value, field);
    }

    static String optionalText(ObjectNode payload, String field) {
        return optionalText((JsonNode) payload, field);
    }

    static String optionalText(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(field + " must be a JSON string.");
        }
        return normalize(value.textValue());
    }

    static String requiredText(JsonNode payload, String field) {
        String value = optionalText(payload, field);
        if (!StringUtils.hasText(value)) {
            throw invalid(field + " is required.");
        }
        return value;
    }

    static LocalDateTime optionalDateTime(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return dateTime(value, field);
    }

    static LocalDateTime requiredDateTime(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull()) {
            throw invalid(field + " is required.");
        }
        return dateTime(value, field);
    }

    static CompetitorProductDetailTarget target(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw invalid("Detail retry target must be a JSON object.");
        }
        String subjectType = requiredText(item, "subjectType").toUpperCase(java.util.Locale.ROOT);
        String code = NoonProductCodeSupport.normalize(requiredText(item, "noonProductCode"));
        if (!NoonProductCodeSupport.extractFirst(code).filter(code::equals).isPresent()) {
            throw invalid("Invalid Noon product code in detail retry target.");
        }
        Long competitorProductId = optionalPositiveLong(item, "competitorProductId");
        if (CompetitorProductDetailTarget.SELF.equals(subjectType)) {
            if (competitorProductId != null) {
                throw invalid("SELF detail retry target cannot have competitorProductId.");
            }
        } else if (CompetitorProductDetailTarget.COMPETITOR.equals(subjectType)) {
            if (competitorProductId == null) {
                throw invalid("COMPETITOR detail retry target requires competitorProductId.");
            }
        } else {
            throw invalid("Invalid detail retry subjectType.");
        }
        CompetitorProductDetailTarget target = new CompetitorProductDetailTarget();
        target.setSubjectType(subjectType);
        target.setCompetitorProductId(competitorProductId);
        target.setNoonProductCode(code);
        target.setCanonicalUrl(optionalText(item, "canonicalUrl"));
        return target;
    }

    static ObjectNode targetNode(CompetitorProductDetailTarget target) {
        validateTarget(target);
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        putText(value, "subjectType", target.getSubjectType());
        putLong(value, "competitorProductId", target.getCompetitorProductId());
        putText(value, "noonProductCode", target.getNoonProductCode());
        putText(value, "canonicalUrl", target.getCanonicalUrl());
        return value;
    }

    static void validateTarget(CompetitorProductDetailTarget target) {
        if (target == null) {
            throw invalid("Detail retry target is required.");
        }
        target(targetNodeUnchecked(target));
    }

    static String targetCanonical(CompetitorProductDetailTarget target) {
        validateTarget(target);
        return part(target.getSubjectType())
                + part(target.getCompetitorProductId())
                + part(target.getNoonProductCode())
                + part(target.getCanonicalUrl());
    }

    static String part(Object value) {
        String text = value == null
                ? ""
                : value instanceof LocalDateTime
                        ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value)
                        : String.valueOf(value);
        return text.length() + ":" + text;
    }

    static void putDateTime(ObjectNode target, String field, LocalDateTime value) {
        if (value == null) {
            target.remove(field);
        } else {
            target.put(field, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value));
        }
    }

    static void putLong(ObjectNode target, String field, Long value) {
        if (value == null) {
            target.remove(field);
        } else {
            target.put(field, value);
        }
    }

    static void putText(ObjectNode target, String field, String value) {
        if (value == null) {
            target.remove(field);
        } else {
            target.put(field, value);
        }
    }

    static CompetitorDetailRetryPayloadException invalid(String message) {
        return new CompetitorDetailRetryPayloadException(message);
    }

    private static int integer(JsonNode value, String field, int minimum, int maximum) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(field + " must be an integer.");
        }
        int parsed = value.intValue();
        if (parsed < minimum || parsed > maximum) {
            throw invalid(field + " is outside the allowed range.");
        }
        return parsed;
    }

    private static long positiveLong(JsonNode value, String field) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0L) {
            throw invalid(field + " must be a positive integer.");
        }
        return value.longValue();
    }

    private static LocalDateTime dateTime(JsonNode value, String field) {
        if (!value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw invalid(field + " must be a local date-time string.");
        }
        try {
            return LocalDateTime.parse(value.textValue().trim());
        } catch (DateTimeParseException exception) {
            throw new CompetitorDetailRetryPayloadException(
                    "Invalid " + field + " value.",
                    exception
            );
        }
    }

    private static ObjectNode targetNodeUnchecked(CompetitorProductDetailTarget target) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        putText(value, "subjectType", target.getSubjectType());
        putLong(value, "competitorProductId", target.getCompetitorProductId());
        putText(value, "noonProductCode", target.getNoonProductCode());
        putText(value, "canonicalUrl", target.getCanonicalUrl());
        return value;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

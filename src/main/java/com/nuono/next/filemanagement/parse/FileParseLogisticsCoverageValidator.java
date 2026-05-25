package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

class FileParseLogisticsCoverageValidator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern RELATIVE_CHANGE = Pattern.compile("(上调|下调|增加|减少|降低|提升).{0,20}\\d+(?:\\.\\d+)?\\s*%");

    private final ObjectMapper objectMapper = new ObjectMapper();

    List<CoverageIssue> validate(
            FileParseTargetPlanRow targetPlan,
            List<FileParseStructuredItem> items,
            List<SourceRowEvidence> sourceRows
    ) {
        if (!isLogisticsPlan(targetPlan)) {
            return List.of();
        }
        List<FileParseStructuredItem> safeItems = items == null ? List.of() : items;
        List<SourceRowEvidence> safeSourceRows = sourceRows == null ? List.of() : sourceRows;
        List<CoverageIssue> issues = new ArrayList<>();
        Set<Long> referencedSourceRows = safeItems.stream()
                .flatMap(item -> item.getSourceRowIds().stream())
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (SourceRowEvidence sourceRow : safeSourceRows) {
            if (!isManualSourceRow(sourceRow) || referencedSourceRows.contains(sourceRow.getId())) {
                continue;
            }
            if (RELATIVE_CHANGE.matcher(nullToEmpty(sourceRow.getRawText())).find()) {
                issues.add(new CoverageIssue(
                        "manual_relative_change_unresolved",
                        "hard_error",
                        sourceRow.getId(),
                        "人工补充文本包含相对变化，但没有链接到可安全计算的物流输出行。",
                        detailsJson(Map.of(
                                "sourceRowId", sourceRow.getId(),
                                "sourceType", sourceRow.getSourceType()
                        ))
                ));
            } else {
                issues.add(new CoverageIssue(
                        "manual_supplement_unlinked",
                        "warning",
                        sourceRow.getId(),
                        "人工补充文本没有链接到物流输出行，请确认是否被解析结果覆盖。",
                        detailsJson(Map.of(
                                "sourceRowId", sourceRow.getId(),
                                "sourceType", sourceRow.getSourceType()
                        ))
                ));
            }
        }

        Set<String> serviceLineKeys = serviceLineCoverageKeys(safeItems);
        for (ExpectedServiceLine expected : expectedServiceLines(safeSourceRows)) {
            String key = expected.country + "|" + expected.transportMode;
            if (!serviceLineKeys.contains(key)) {
                issues.add(new CoverageIssue(
                        "logistics_section_missing",
                        "hard_error",
                        expected.sourceRowId,
                        "源内容提到了物流服务段，但解析结果缺少对应服务线路。",
                        detailsJson(Map.of(
                                "sourceRowId", expected.sourceRowId,
                                "country", expected.country,
                                "transportMode", expected.transportMode
                        ))
                ));
            }
        }
        return issues;
    }

    private Set<String> serviceLineCoverageKeys(List<FileParseStructuredItem> items) {
        Set<String> keys = new LinkedHashSet<>();
        for (FileParseStructuredItem item : items) {
            if (!FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
            String country = normalizeCountry(text(payload.get("country")));
            String transportMode = normalizeTransport(text(payload.get("transportMode")));
            if (StringUtils.hasText(country) && StringUtils.hasText(transportMode)) {
                keys.add(country + "|" + transportMode);
            }
        }
        return keys;
    }

    private List<ExpectedServiceLine> expectedServiceLines(List<SourceRowEvidence> sourceRows) {
        Map<String, ExpectedServiceLine> expectedByKey = new LinkedHashMap<>();
        for (SourceRowEvidence row : sourceRows) {
            String text = lower(row.getRawText());
            String country = expectedCountry(text);
            String transportMode = expectedTransport(text);
            if (!StringUtils.hasText(country) || !StringUtils.hasText(transportMode)) {
                continue;
            }
            String key = country + "|" + transportMode;
            expectedByKey.putIfAbsent(key, new ExpectedServiceLine(row.getId(), country, transportMode));
        }
        return new ArrayList<>(expectedByKey.values());
    }

    private String expectedCountry(String text) {
        if (text.contains("ksa") || text.contains("saudi") || text.contains("沙特")) {
            return "KSA";
        }
        if (text.contains("uae") || text.contains("emirates") || text.contains("阿联酋")) {
            return "UAE";
        }
        return "";
    }

    private String expectedTransport(String text) {
        if (text.contains("空运大货") || text.contains("cargo air") || text.contains("air cargo")) {
            return "cargo_air";
        }
        if (text.contains("海运") || text.contains(" sea")) {
            return "sea";
        }
        if (text.contains("快递") || text.contains("express")) {
            return "express";
        }
        if (text.contains("空运") || text.contains(" air")) {
            return "air";
        }
        return "";
    }

    private boolean isManualSourceRow(SourceRowEvidence row) {
        return row != null && "manual_text_block".equals(row.getSourceType());
    }

    private boolean isLogisticsPlan(FileParseTargetPlanRow targetPlan) {
        if (targetPlan == null) {
            return false;
        }
        return startsWithIgnoreCase(targetPlan.getCode(), "logistics")
                || startsWithIgnoreCase(targetPlan.getDocumentType(), "logistics");
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private String normalizeCountry(String value) {
        String upper = nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(upper) || "SAUDI".equals(upper)) {
            return "KSA";
        }
        if ("AE".equals(upper)) {
            return "UAE";
        }
        return upper;
    }

    private String normalizeTransport(String value) {
        return nullToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            return new LinkedHashMap<>();
        }
    }

    private String detailsJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private String lower(String value) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static class SourceRowEvidence {
        private final Long id;
        private final String sourceType;
        private final String rawText;

        SourceRowEvidence(Long id, String sourceType, String rawText) {
            this.id = id;
            this.sourceType = sourceType;
            this.rawText = rawText;
        }

        Long getId() {
            return id;
        }

        String getSourceType() {
            return sourceType;
        }

        String getRawText() {
            return rawText;
        }
    }

    static class CoverageIssue {
        private final String code;
        private final String severity;
        private final Long sourceRowId;
        private final String message;
        private final String detailsJson;

        CoverageIssue(String code, String severity, Long sourceRowId, String message, String detailsJson) {
            this.code = code;
            this.severity = severity;
            this.sourceRowId = sourceRowId;
            this.message = message;
            this.detailsJson = detailsJson;
        }

        String getCode() {
            return code;
        }

        String getSeverity() {
            return severity;
        }

        Long getSourceRowId() {
            return sourceRowId;
        }

        String getMessage() {
            return message;
        }

        String getDetailsJson() {
            return detailsJson;
        }
    }

    private static class ExpectedServiceLine {
        private final Long sourceRowId;
        private final String country;
        private final String transportMode;

        private ExpectedServiceLine(Long sourceRowId, String country, String transportMode) {
            this.sourceRowId = sourceRowId;
            this.country = country;
            this.transportMode = transportMode;
        }
    }
}

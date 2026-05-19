package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseResultDiffService {

    private static final int DELETE_INFERENCE_MIN_BASE_COUNT = 20;
    private static final int DELETE_INFERENCE_MIN_MATCH_PERCENT = 80;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final FileManagementParseMapper fileManagementParseMapper;
    private final ObjectMapper objectMapper;

    public FileParseResultDiffService(
            FileManagementParseMapper fileManagementParseMapper,
            ObjectMapper objectMapper
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.objectMapper = objectMapper;
    }

    public FileParseStructuredAiResult applyDiff(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards,
            FileParseStructuredAiResult structuredResult
    ) {
        structuredResult.setItems(new ArrayList<>(structuredResult.getItems()));
        List<FileParseVersionItemRow> baseItems = task.getBaseVersionId() == null
                ? List.of()
                : fileManagementParseMapper.selectVersionItems(task.getBaseVersionId());
        Map<String, FileParseVersionItemRow> baseByKey = baseItems.stream()
                .collect(Collectors.toMap(this::resultKey, Function.identity(), (left, right) -> left));
        Map<String, FileParseItemStandardRow> standardByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
        Set<String> seenResultKeys = new LinkedHashSet<>();
        Set<String> seenBaseKeys = new LinkedHashSet<>();
        for (FileParseStructuredItem item : structuredResult.getItems()) {
            String itemKey = resultKey(item);
            FileParseVersionItemRow baseItem = baseByKey.get(itemKey);
            seenResultKeys.add(itemKey);
            if (baseItem != null) {
                seenBaseKeys.add(resultKey(baseItem));
            }
            applyItemDiff(item, standardByType.get(item.getItemType()), baseItem);
        }

        int sortNo = structuredResult.getItems().stream()
                .map(FileParseStructuredItem::getSortNo)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0);
        boolean suppressDeleteSuspected = shouldSuppressDeleteSuspected(baseItems.size(), seenBaseKeys.size());
        int suppressedDeleteSuspected = 0;
        for (FileParseVersionItemRow baseItem : baseItems) {
            if (!seenResultKeys.contains(resultKey(baseItem))) {
                if (suppressDeleteSuspected) {
                    suppressedDeleteSuspected++;
                    continue;
                }
                structuredResult.getItems().add(toDeleteSuspectedItem(baseItem, ++sortNo));
            }
        }
        structuredResult.setSummaryJson(writeJson(resultSummary(
                structuredResult.getItems(),
                baseItems.size(),
                seenBaseKeys.size(),
                suppressedDeleteSuspected,
                suppressDeleteSuspected
        )));
        structuredResult.setValidationSummaryJson(writeJson(validationSummary(structuredResult.getItems())));
        return structuredResult;
    }

    private void applyItemDiff(
            FileParseStructuredItem item,
            FileParseItemStandardRow standard,
            FileParseVersionItemRow baseItem
    ) {
        Map<String, Object> currentPayload = FileParseCommissionPayloadNormalizer.normalize(
                item.getItemType(),
                readMap(item.getNormalizedPayloadJson())
        );
        item.setEffectivePayloadJson(writeJson(currentPayload));
        item.setEffectiveValidationStatus(item.getValidationStatus());
        item.setEffectivePayloadHash(sha256(writeJson(currentPayload)));
        item.setChangedFieldKeysJson(writeJson(List.of()));
        if (baseItem == null) {
            item.setChangeType("added");
            return;
        }

        Map<String, Object> oldPayload = FileParseCommissionPayloadNormalizer.normalize(
                baseItem.getItemType(),
                readMap(baseItem.getVersionPayloadJson())
        );
        List<String> changedFieldKeys = changedFieldKeys(standard, oldPayload, currentPayload);
        item.setOldPayloadJson(writeJson(oldPayload));
        item.setChangedFieldKeysJson(writeJson(changedFieldKeys));
        if (changedFieldKeys.isEmpty() && isDuplicateConflict(item)) {
            item.setValidationStatus("pass");
            item.setReviewStatus("confirmed");
            item.setValidationErrorJson(writeJson(Map.of()));
            item.setEffectiveValidationStatus("pass");
            item.setChangeType("unchanged");
        } else if (changedFieldKeys.isEmpty() && !"hard_error".equals(item.getValidationStatus())) {
            item.setChangeType("unchanged");
            item.setReviewStatus("confirmed");
        } else if (changedFieldKeys.isEmpty()) {
            item.setChangeType("unchanged");
        } else {
            item.setChangeType("changed");
        }
    }

    private List<String> changedFieldKeys(
            FileParseItemStandardRow standard,
            Map<String, Object> oldPayload,
            Map<String, Object> currentPayload
    ) {
        Set<String> fields = compareFields(standard, oldPayload, currentPayload);
        List<String> changed = new ArrayList<>();
        for (String field : fields) {
            if (!sameValue(oldPayload.get(field), currentPayload.get(field))) {
                changed.add(field);
            }
        }
        return changed;
    }

    private boolean isDuplicateConflict(FileParseStructuredItem item) {
        if (!"hard_error".equals(item.getValidationStatus())) {
            return false;
        }
        Map<String, Object> validationError = readMap(item.getValidationErrorJson());
        return validationError.containsKey("duplicateItemCount");
    }

    private Set<String> compareFields(
            FileParseItemStandardRow standard,
            Map<String, Object> oldPayload,
            Map<String, Object> currentPayload
    ) {
        Map<String, Object> diffRule = standard == null ? new LinkedHashMap<>() : readMap(standard.getDiffRuleJson());
        Object configuredFields = diffRule.get("compareFields");
        Set<String> fields = new LinkedHashSet<>();
        if (configuredFields instanceof List) {
            for (Object value : (List<?>) configuredFields) {
                if (value instanceof String && StringUtils.hasText((String) value)) {
                    fields.add(((String) value).trim());
                }
            }
        }
        if (fields.isEmpty()) {
            fields.addAll(oldPayload.keySet());
            fields.addAll(currentPayload.keySet());
        }
        return fields;
    }

    private FileParseStructuredItem toDeleteSuspectedItem(FileParseVersionItemRow baseItem, int sortNo) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(baseItem.getItemType());
        item.setNaturalKey(baseItem.getNaturalKey());
        item.setNaturalKeyHash(baseItem.getNaturalKeyHash());
        item.setChangeType("delete_suspected");
        item.setReviewStatus("pending");
        item.setValidationStatus("warning");
        item.setNormalizedPayloadJson("{}");
        item.setOldPayloadJson(baseItem.getVersionPayloadJson());
        item.setChangedFieldKeysJson(writeJson(List.of("__deleted__")));
        item.setEffectivePayloadJson("{}");
        item.setEffectiveValidationStatus("warning");
        item.setEffectivePayloadHash(sha256("{}"));
        item.setEvidenceJson(writeJson(Map.of("source", "version_diff")));
        item.setValidationErrorJson(writeJson(Map.of("message", "当前解析结果未包含该历史版本行，需要人工确认是否停用。")));
        item.setSortNo(sortNo);
        return item;
    }

    private String resultKey(FileParseStructuredItem item) {
        return FileParseNaturalKeySupport.matchKey(
                item.getItemType(),
                FileParseCommissionPayloadNormalizer.normalize(item.getItemType(), readMap(item.getNormalizedPayloadJson())),
                item.getNaturalKeyHash()
        );
    }

    private String resultKey(FileParseVersionItemRow item) {
        return FileParseNaturalKeySupport.matchKey(
                item.getItemType(),
                FileParseCommissionPayloadNormalizer.normalize(item.getItemType(), readMap(item.getVersionPayloadJson())),
                item.getNaturalKeyHash()
        );
    }

    private boolean shouldSuppressDeleteSuspected(int baseItemCount, int matchedBaseItemCount) {
        if (baseItemCount < DELETE_INFERENCE_MIN_BASE_COUNT) {
            return false;
        }
        return matchedBaseItemCount * 100 < baseItemCount * DELETE_INFERENCE_MIN_MATCH_PERCENT;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).trim().equals(String.valueOf(right).trim());
    }

    private Map<String, Object> resultSummary(
            List<FileParseStructuredItem> items,
            int baseItemCount,
            int matchedBaseItemCount,
            int suppressedDeleteSuspected,
            boolean deleteInferenceSuppressed
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("itemCount", items.size());
        summary.put("added", items.stream().filter(item -> "added".equals(item.getChangeType())).count());
        summary.put("changed", items.stream().filter(item -> "changed".equals(item.getChangeType())).count());
        summary.put("unchanged", items.stream().filter(item -> "unchanged".equals(item.getChangeType())).count());
        summary.put("deleteSuspected", items.stream().filter(item -> "delete_suspected".equals(item.getChangeType())).count());
        summary.put("baseItemCount", baseItemCount);
        summary.put("matchedBaseItemCount", matchedBaseItemCount);
        summary.put("deleteInferenceSuppressed", deleteInferenceSuppressed);
        summary.put("suppressedDeleteSuspected", suppressedDeleteSuspected);
        return summary;
    }

    private Map<String, Object> validationSummary(List<FileParseStructuredItem> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("pass", items.stream().filter(item -> "pass".equals(item.getValidationStatus())).count());
        summary.put("warning", items.stream().filter(item -> "warning".equals(item.getValidationStatus())).count());
        summary.put("hardError", items.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count());
        return summary;
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            throw new FileParseAiParseException("RESULT_JSON_INVALID", "解析结果 JSON 解析失败。");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException error) {
            throw new FileParseAiParseException("RESULT_JSON_WRITE_FAILED", "解析结果 JSON 序列化失败。");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

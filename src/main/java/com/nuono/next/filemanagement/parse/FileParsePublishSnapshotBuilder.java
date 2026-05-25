package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

class FileParsePublishSnapshotBuilder {

    private final FileParseResultItemViewAssembler viewAssembler;

    FileParsePublishSnapshotBuilder(FileParseResultItemViewAssembler viewAssembler) {
        this.viewAssembler = viewAssembler;
    }

    List<FileParsePublishSnapshotItem> buildSnapshot(
            List<FileParseVersionItemRow> baseItems,
            List<FileParseResultItemRow> resultItems,
            List<FileParseItemStandardRow> itemStandards
    ) {
        Map<String, FileParsePublishSnapshotItem> snapshotByKey = new LinkedHashMap<>();
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
        for (FileParseVersionItemRow baseItem : baseItems) {
            Map<String, Object> payload = normalizePayload(baseItem.getItemType(), viewAssembler.readMap(baseItem.getVersionPayloadJson()));
            FileParseItemStandardRow standard = standardsByType.get(baseItem.getItemType());
            validatePayload(standard, payload, baseItem.getNaturalKey());
            FileParsePublishSnapshotItem item = FileParsePublishSnapshotItem.fromBase(baseItem, viewAssembler.writeJson(payload));
            snapshotByKey.put(snapshotKey(baseItem.getItemType(), payload, baseItem.getNaturalKeyHash()), item);
        }

        int nextSortNo = snapshotByKey.values().stream()
                .map(FileParsePublishSnapshotItem::getSortNo)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0);
        for (FileParseResultItemRow resultItem : resultItems) {
            String reviewStatus = resultItem.getReviewStatus();
            if ("rejected".equals(reviewStatus)) {
                continue;
            }
            String key = resultKey(resultItem);
            if ("keep_old".equals(reviewStatus)) {
                if (!snapshotByKey.containsKey(key)) {
                    throw new IllegalArgumentException("保留旧值结果行缺少当前生效版本旧值：" + resultItem.getNaturalKey());
                }
                continue;
            }
            if (!"confirmed".equals(reviewStatus)) {
                throw new IllegalArgumentException("存在未确认的解析结果行：" + resultItem.getNaturalKey());
            }
            if ("hard_error".equals(currentValidationStatus(resultItem))) {
                throw new IllegalArgumentException("存在硬错误结果行，不能发布：" + resultItem.getNaturalKey());
            }
            if ("delete_suspected".equals(resultItem.getChangeType())) {
                snapshotByKey.remove(key);
                continue;
            }

            Map<String, Object> payload = normalizePayload(resultItem.getItemType(), viewAssembler.currentPayload(resultItem));
            FileParseItemStandardRow standard = standardsByType.get(resultItem.getItemType());
            validatePayload(standard, payload, resultItem.getNaturalKey());
            FileParsePublishSnapshotItem snapshotItem = FileParsePublishSnapshotItem.fromResult(
                    resultItem,
                    viewAssembler.writeJson(payload),
                    resultItem.getSortNo() == null ? ++nextSortNo : resultItem.getSortNo()
            );
            snapshotByKey.put(key, snapshotItem);
        }
        List<FileParsePublishSnapshotItem> snapshotItems = new ArrayList<>(snapshotByKey.values());
        validateSnapshot(snapshotItems, itemStandards);
        return snapshotItems;
    }

    void validateBaseVersion(FileParseTaskRow task, FileParseActiveVersionRow activeVersion) {
        Long baseVersionId = task.getBaseVersionId();
        Long activeVersionId = activeVersion.getVersionId();
        if (baseVersionId == null && activeVersionId == null) {
            return;
        }
        if (baseVersionId == null || activeVersionId == null || !baseVersionId.equals(activeVersionId)) {
            throw new IllegalStateException("当前生效版本已变化，请重新解析或重新对比后再发布。");
        }
    }

    private void validateSnapshot(
            List<FileParsePublishSnapshotItem> snapshotItems,
            List<FileParseItemStandardRow> itemStandards
    ) {
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
        for (FileParsePublishSnapshotItem item : snapshotItems) {
            FileParseItemStandardRow standard = standardsByType.get(item.getItemType());
            validatePayload(standard, viewAssembler.readMap(item.getPayloadJson()), item.getNaturalKey());
        }
    }

    private void validatePayload(FileParseItemStandardRow standard, Map<String, Object> payload, String naturalKey) {
        if (standard == null) {
            throw new IllegalArgumentException("发布快照存在未定义结果类型：" + naturalKey);
        }
        Map<String, Object> validationRule = viewAssembler.readMap(standard.getValidationRuleJson());
        Object requiredValue = validationRule.get("required");
        if (!(requiredValue instanceof List)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (Object value : (List<?>) requiredValue) {
            if (value instanceof String && !StringUtils.hasText(text(payload.get(value)))) {
                missing.add((String) value);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("发布快照字段缺失：" + naturalKey + " 缺少 " + String.join("、", missing));
        }
    }

    private String resultKey(FileParseResultItemRow item) {
        Map<String, Object> payload;
        if ("delete_suspected".equals(item.getChangeType()) || "keep_old".equals(item.getReviewStatus())) {
            payload = viewAssembler.readMap(item.getOldPayloadJson());
        } else {
            payload = viewAssembler.currentPayload(item);
        }
        return snapshotKey(item.getItemType(), payload, item.getNaturalKeyHash());
    }

    private String snapshotKey(String itemType, Map<String, Object> payload, String fallbackNaturalKeyHash) {
        return FileParseNaturalKeySupport.matchKey(
                itemType,
                normalizePayload(itemType, payload),
                fallbackNaturalKeyHash
        );
    }

    private Map<String, Object> normalizePayload(String itemType, Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                itemType,
                FileParseCommissionPayloadNormalizer.normalize(itemType, payload)
        );
        return FileParseOutboundFeePayloadNormalizer.normalize(itemType, normalized);
    }

    private String currentValidationStatus(FileParseResultItemRow row) {
        if (StringUtils.hasText(row.getEffectiveValidationStatus())) {
            return row.getEffectiveValidationStatus();
        }
        return row.getValidationStatus();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}

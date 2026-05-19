package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParsePublishService {

    private static final DateTimeFormatter VERSION_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String GLOBAL_SCOPE_TYPE = "global";
    private static final String GLOBAL_SCOPE_KEY = "global:*";

    private final FileManagementParseMapper fileManagementParseMapper;
    private final FileParseResultItemViewAssembler viewAssembler;

    public FileParsePublishService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseResultItemViewAssembler viewAssembler
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.viewAssembler = viewAssembler;
    }

    @Transactional
    public FileParsePublishView publish(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            FileParsePublishCommand command,
            String idempotencyKey,
            Long operatorUserId
    ) {
        validateExpectedResult(task, command);
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(task, command);
        FileParsePublishAuditRow auditRow = fileManagementParseMapper.selectPublishAuditByIdempotency(
                task.getId(),
                normalizedIdempotencyKey
        );
        if (auditRow != null) {
            if (!requestHash.equals(auditRow.getPayloadHash())) {
                throw new IllegalStateException("幂等键已用于不同发布请求。");
            }
            FileParseVersionSummaryRow version = fileManagementParseMapper.selectVersion(auditRow.getVersionId());
            if (version == null) {
                throw new IllegalStateException("发布幂等记录关联版本不存在。");
            }
            return toPublishView(version);
        }

        requirePublishableTask(task);
        FileParseActiveVersionRow activeVersion = requireActiveVersion(task);
        validateBaseVersion(task, activeVersion);
        if (fileManagementParseMapper.countBlockingResultItems(task.getCurrentResultId()) > 0) {
            throw new IllegalArgumentException("仍存在待处理或硬错误的解析结果，不能发布。");
        }
        if (fileManagementParseMapper.countOpenHardValidationIssues(task.getId()) > 0) {
            throw new IllegalArgumentException("仍存在硬错误级校验问题，不能发布。");
        }

        List<FileParseVersionItemRow> baseItems = activeVersion.getVersionId() == null
                ? List.of()
                : fileManagementParseMapper.selectVersionItems(activeVersion.getVersionId());
        List<FileParseResultItemRow> resultItems = fileManagementParseMapper.selectResultItemsForPublish(task.getCurrentResultId());
        List<SnapshotItem> snapshotItems = buildSnapshot(baseItems, resultItems, itemStandards);
        validateSnapshot(snapshotItems, itemStandards);

        Long versionId = fileManagementParseMapper.nextVersionId();
        String versionNo = buildVersionNo(targetPlan, versionId);
        LocalDateTime publishedAt = LocalDateTime.now();
        String summaryJson = viewAssembler.writeJson(summary(task, snapshotItems, command));
        int inserted = fileManagementParseMapper.insertVersion(
                versionId,
                versionNo,
                targetPlan.getId(),
                task.getId(),
                task.getCurrentResultId(),
                task.getStandardVersionId(),
                task.getBaseVersionId(),
                GLOBAL_SCOPE_TYPE,
                GLOBAL_SCOPE_KEY,
                publishedAt,
                summaryJson,
                operatorUserId
        );
        if (inserted != 1) {
            throw new IllegalStateException("发布版本写入失败。");
        }

        for (SnapshotItem item : snapshotItems) {
            Long versionItemId = fileManagementParseMapper.nextVersionItemId();
            int itemInserted = fileManagementParseMapper.insertVersionItem(
                    versionItemId,
                    versionId,
                    targetPlan.getId(),
                    item.itemType,
                    item.naturalKey,
                    item.naturalKeyHash,
                    item.payloadJson,
                    item.sourceResultItemId,
                    GLOBAL_SCOPE_TYPE,
                    GLOBAL_SCOPE_KEY,
                    item.sortNo,
                    operatorUserId
            );
            if (itemInserted != 1) {
                throw new IllegalStateException("发布版本快照写入失败。");
            }
        }

        fileManagementParseMapper.markVersionsHistory(targetPlan.getId(), GLOBAL_SCOPE_TYPE, GLOBAL_SCOPE_KEY, versionId, operatorUserId);
        int activeUpdated = fileManagementParseMapper.updateActiveVersion(
                targetPlan.getId(),
                GLOBAL_SCOPE_TYPE,
                GLOBAL_SCOPE_KEY,
                versionId,
                versionNo,
                operatorUserId
        );
        if (activeUpdated != 1) {
            throw new IllegalStateException("当前生效版本更新失败。");
        }
        int taskUpdated = fileManagementParseMapper.markTaskPublished(task.getId(), task.getCurrentResultId(), operatorUserId);
        if (taskUpdated != 1) {
            throw new IllegalStateException("解析文档发布状态更新失败。");
        }

        Long auditId = fileManagementParseMapper.nextAuditLogId();
        fileManagementParseMapper.insertPublishAudit(
                auditId,
                task.getId(),
                targetPlan.getId(),
                versionId,
                trimOptional(command.getRemark(), 1000),
                normalizedIdempotencyKey,
                requestHash,
                operatorUserId
        );

        FileParsePublishView view = new FileParsePublishView();
        view.setVersionId(versionId);
        view.setVersionNo(versionNo);
        view.setStatus("active");
        view.setPublishedAt(publishedAt);
        return view;
    }

    private List<SnapshotItem> buildSnapshot(
            List<FileParseVersionItemRow> baseItems,
            List<FileParseResultItemRow> resultItems,
            List<FileParseItemStandardRow> itemStandards
    ) {
        Map<String, SnapshotItem> snapshotByKey = new LinkedHashMap<>();
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
        for (FileParseVersionItemRow baseItem : baseItems) {
            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    baseItem.getItemType(),
                    viewAssembler.readMap(baseItem.getVersionPayloadJson())
            );
            FileParseItemStandardRow standard = standardsByType.get(baseItem.getItemType());
            validatePayload(standard, payload, baseItem.getNaturalKey());
            SnapshotItem item = SnapshotItem.fromBase(baseItem, viewAssembler.writeJson(payload));
            snapshotByKey.put(snapshotKey(baseItem.getItemType(), payload, baseItem.getNaturalKeyHash()), item);
        }

        int nextSortNo = snapshotByKey.values().stream()
                .map(item -> item.sortNo)
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

            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    resultItem.getItemType(),
                    viewAssembler.currentPayload(resultItem)
            );
            FileParseItemStandardRow standard = standardsByType.get(resultItem.getItemType());
            validatePayload(standard, payload, resultItem.getNaturalKey());
            SnapshotItem snapshotItem = SnapshotItem.fromResult(
                    resultItem,
                    viewAssembler.writeJson(payload),
                    resultItem.getSortNo() == null ? ++nextSortNo : resultItem.getSortNo()
            );
            snapshotByKey.put(key, snapshotItem);
        }
        return new ArrayList<>(snapshotByKey.values());
    }

    private void validateSnapshot(List<SnapshotItem> snapshotItems, List<FileParseItemStandardRow> itemStandards) {
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
        for (SnapshotItem item : snapshotItems) {
            FileParseItemStandardRow standard = standardsByType.get(item.itemType);
            validatePayload(standard, viewAssembler.readMap(item.payloadJson), item.naturalKey);
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

    private FileParseActiveVersionRow requireActiveVersion(FileParseTaskRow task) {
        FileParseActiveVersionRow activeVersion = fileManagementParseMapper.selectActiveVersionForUpdate(
                task.getTargetPlanId(),
                GLOBAL_SCOPE_TYPE,
                GLOBAL_SCOPE_KEY
        );
        if (activeVersion == null) {
            throw new IllegalStateException("当前生效版本指针不存在，不能发布。");
        }
        return activeVersion;
    }

    private void validateBaseVersion(FileParseTaskRow task, FileParseActiveVersionRow activeVersion) {
        Long baseVersionId = task.getBaseVersionId();
        Long activeVersionId = activeVersion.getVersionId();
        if (baseVersionId == null && activeVersionId == null) {
            return;
        }
        if (baseVersionId == null || activeVersionId == null || !baseVersionId.equals(activeVersionId)) {
            throw new IllegalStateException("当前生效版本已变化，请重新解析或重新对比后再发布。");
        }
    }

    private void requirePublishableTask(FileParseTaskRow task) {
        if (task == null || !"ready_to_publish".equals(task.getStatus())) {
            throw new IllegalArgumentException("当前解析文档状态不可发布：" + (task == null ? null : task.getStatus()));
        }
        if (task.getCurrentResultId() == null) {
            throw new IllegalArgumentException("当前解析文档还没有解析结果。");
        }
    }

    private void validateExpectedResult(FileParseTaskRow task, FileParsePublishCommand command) {
        if (command == null || command.getExpectedResultId() == null) {
            throw new IllegalArgumentException("expectedResultId 不能为空。");
        }
        if (!command.getExpectedResultId().equals(task.getCurrentResultId())) {
            throw new IllegalStateException("当前解析结果已变化，请刷新页面后重试。");
        }
    }

    private FileParsePublishView toPublishView(FileParseVersionSummaryRow version) {
        FileParsePublishView view = new FileParsePublishView();
        view.setVersionId(version.getId());
        view.setVersionNo(version.getVersionNo());
        view.setStatus(version.getVersionStatus());
        view.setPublishedAt(version.getPublishedAt());
        return view;
    }

    private Map<String, Object> summary(
            FileParseTaskRow task,
            List<SnapshotItem> snapshotItems,
            FileParsePublishCommand command
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceTaskId", task.getId());
        summary.put("sourceResultId", task.getCurrentResultId());
        summary.put("itemCount", snapshotItems.size());
        summary.put("remark", trimOptional(command.getRemark(), 1000));
        return summary;
    }

    private String buildVersionNo(FileParseTargetPlanRow targetPlan, Long versionId) {
        String prefix = StringUtils.hasText(targetPlan.getCode()) ? targetPlan.getCode() : "file-parse";
        String normalizedPrefix = prefix
                .trim()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedPrefix)) {
            normalizedPrefix = "FILE-PARSE";
        }
        return normalizedPrefix + "-" + LocalDate.now().format(VERSION_DATE_FORMATTER) + "-" + versionId;
    }

    private String requestHash(FileParseTaskRow task, FileParsePublishCommand command) {
        String payload = task.getId()
                + "|"
                + command.getExpectedResultId()
                + "|"
                + trimOptional(command.getConfirmMessage(), 300)
                + "|"
                + trimOptional(command.getRemark(), 1000);
        return sha256(payload);
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空。");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 180) {
            throw new IllegalArgumentException("Idempotency-Key 长度不能超过 180 个字符。");
        }
        return trimmed;
    }

    private String trimOptional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
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
                FileParseCommissionPayloadNormalizer.normalize(itemType, payload),
                fallbackNaturalKeyHash
        );
    }

    private String currentValidationStatus(FileParseResultItemRow row) {
        if (StringUtils.hasText(row.getEffectiveValidationStatus())) {
            return row.getEffectiveValidationStatus();
        }
        return row.getValidationStatus();
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static final class SnapshotItem {
        private final String itemType;
        private final String naturalKey;
        private final String naturalKeyHash;
        private final String payloadJson;
        private final Long sourceResultItemId;
        private final Integer sortNo;

        private SnapshotItem(
                String itemType,
                String naturalKey,
                String naturalKeyHash,
                String payloadJson,
                Long sourceResultItemId,
                Integer sortNo
        ) {
            this.itemType = itemType;
            this.naturalKey = naturalKey;
            this.naturalKeyHash = naturalKeyHash;
            this.payloadJson = payloadJson;
            this.sourceResultItemId = sourceResultItemId;
            this.sortNo = sortNo;
        }

        private static SnapshotItem fromBase(FileParseVersionItemRow row, String payloadJson) {
            return new SnapshotItem(
                    row.getItemType(),
                    row.getNaturalKey(),
                    row.getNaturalKeyHash(),
                    payloadJson,
                    null,
                    row.getSortNo()
            );
        }

        private static SnapshotItem fromResult(FileParseResultItemRow row, String payloadJson, Integer sortNo) {
            return new SnapshotItem(
                    row.getItemType(),
                    row.getNaturalKey(),
                    row.getNaturalKeyHash(),
                    payloadJson,
                    row.getId(),
                    sortNo
            );
        }

    }
}

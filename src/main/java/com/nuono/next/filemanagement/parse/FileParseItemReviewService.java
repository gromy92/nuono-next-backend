package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseItemReviewService {

    private final FileManagementParseMapper fileManagementParseMapper;
    private final FileParseResultItemViewAssembler viewAssembler;

    public FileParseItemReviewService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseResultItemViewAssembler viewAssembler
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.viewAssembler = viewAssembler;
    }

    public FileParseProcessingItemsView listProcessingItems(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards,
            String reviewStatus,
            String changeType,
            Integer page,
            Integer pageSize
    ) {
        requireCurrentResult(task);
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedReviewStatus = normalizeFilter(reviewStatus);
        String normalizedChangeType = normalizeFilter(changeType);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        int total = fileManagementParseMapper.countResultItems(
                task.getCurrentResultId(),
                normalizedReviewStatus,
                normalizedChangeType
        );
        List<FileParseResultItemRow> rows = fileManagementParseMapper.selectResultItems(
                task.getCurrentResultId(),
                normalizedReviewStatus,
                normalizedChangeType,
                normalizedPageSize,
                offset
        );
        FileParseProcessingItemsView view = new FileParseProcessingItemsView();
        view.setTaskId(task.getId());
        view.setResultId(task.getCurrentResultId());
        view.setPage(normalizedPage);
        view.setPageSize(normalizedPageSize);
        view.setTotal(total);
        view.setColumns(viewAssembler.buildColumns(itemStandards));
        view.setItems(rows.stream()
                .map(viewAssembler::toProcessingItemView)
                .collect(Collectors.toList()));
        return view;
    }

    public FileParseItemCompareView compareItem(
            FileParseTaskRow task,
            Long itemId
    ) {
        requireCurrentResult(task);
        FileParseResultItemRow row = requireResultItem(task, itemId);
        return viewAssembler.toCompareView(row);
    }

    @Transactional
    public FileParseProcessingItemView reviewItem(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards,
            Long itemId,
            String action,
            FileParseReviewCommand command,
            String idempotencyKey,
            Long operatorUserId
    ) {
        requireEditableTask(task);
        requireCurrentResult(task);
        FileParseResultItemRow row = requireResultItem(task, itemId);
        validateExpectedResult(task, command);
        String normalizedAction = normalizeAction(action);
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(normalizedAction, command);
        FileParseItemReviewRow existingReview = fileManagementParseMapper.selectReviewByIdempotency(
                task.getId(),
                row.getId(),
                normalizedIdempotencyKey
        );
        if (existingReview != null) {
            if (!requestHash.equals(existingReview.getRequestHash())) {
                throw new IllegalStateException("幂等键已用于不同处理请求。");
            }
            FileParseResultItemRow currentRow = requireResultItem(task, itemId);
            return viewAssembler.toProcessingItemView(currentRow);
        }

        FileParseItemStandardRow standard = standardByType(itemStandards).get(row.getItemType());
        FileParseReviewResolution resolution = resolveReview(row, standard, normalizedAction, command);
        Long reviewId = fileManagementParseMapper.nextReviewId();
        fileManagementParseMapper.clearCurrentReview(row.getId(), operatorUserId);
        int inserted = fileManagementParseMapper.insertItemReview(
                reviewId,
                row.getId(),
                row.getResultId(),
                row.getTaskId(),
                normalizedAction,
                resolution.reviewStatus,
                resolution.overridePayloadJson,
                resolution.effectivePayloadJson,
                resolution.validationStatus,
                resolution.validationMessage,
                resolution.note,
                command.getExpectedResultId(),
                normalizedIdempotencyKey,
                requestHash,
                operatorUserId
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析结果处理记录写入失败。");
        }
        int updated = fileManagementParseMapper.updateResultItemReviewCache(
                row.getId(),
                reviewId,
                resolution.reviewStatus,
                resolution.effectivePayloadJson,
                resolution.validationStatus,
                resolution.effectivePayloadHash,
                operatorUserId
        );
        if (updated != 1) {
            throw new IllegalStateException("解析结果处理状态更新失败。");
        }
        fileManagementParseMapper.updateTaskStatusAfterReviewFromItems(task.getId(), operatorUserId);

        FileParseResultItemRow updatedRow = requireResultItem(task, itemId);
        return viewAssembler.toProcessingItemView(updatedRow);
    }

    @Transactional
    public FileParseBatchReviewView acceptItems(
            FileParseTaskRow task,
            List<FileParseItemStandardRow> itemStandards,
            FileParseBatchReviewCommand command,
            String idempotencyKey,
            Long operatorUserId
    ) {
        requireEditableTask(task);
        requireCurrentResult(task);
        if (command == null || command.getExpectedResultId() == null) {
            throw new IllegalArgumentException("expectedResultId 不能为空。");
        }
        if (!command.getExpectedResultId().equals(task.getCurrentResultId())) {
            throw new IllegalStateException("当前解析结果已变化，请刷新页面后重试。");
        }
        Set<Long> itemIds = normalizeBatchItemIds(command.getItemIds());
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        FileParseReviewCommand itemCommand = new FileParseReviewCommand();
        itemCommand.setExpectedResultId(command.getExpectedResultId());
        itemCommand.setRemark(command.getRemark());

        List<FileParseProcessingItemView> items = new ArrayList<>();
        for (Long itemId : itemIds) {
            items.add(reviewItem(
                    task,
                    itemStandards,
                    itemId,
                    "accept",
                    itemCommand,
                    childIdempotencyKey(normalizedIdempotencyKey, itemId),
                    operatorUserId
            ));
        }

        FileParseBatchReviewView view = new FileParseBatchReviewView();
        view.setTaskId(task.getId());
        view.setResultId(task.getCurrentResultId());
        view.setTotalCount(itemIds.size());
        view.setSuccessCount(items.size());
        view.setItems(items);
        return view;
    }

    private FileParseReviewResolution resolveReview(
            FileParseResultItemRow row,
            FileParseItemStandardRow standard,
            String action,
            FileParseReviewCommand command
    ) {
        if ("accept".equals(action)) {
            if ("hard_error".equals(row.getValidationStatus())) {
                throw new IllegalArgumentException("硬错误结果行不能直接确认，请先编辑修正。");
            }
            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    row.getItemType(),
                    viewAssembler.readMap(row.getNormalizedPayloadJson())
            );
            return resolved(
                    "confirmed",
                    null,
                    viewAssembler.writeJson(payload),
                    "pass",
                    null,
                    trimNote(command)
            );
        }
        if ("edit".equals(action)) {
            if (command == null || command.getFields() == null || command.getFields().isEmpty()) {
                throw new IllegalArgumentException("编辑结果行必须提交字段。");
            }
            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    row.getItemType(),
                    viewAssembler.currentPayload(row)
            );
            payload.putAll(command.getFields());
            payload = FileParseCommissionPayloadNormalizer.normalize(row.getItemType(), payload);
            FileParseValidationResult validation = validatePayload(standard, payload);
            return resolved(
                    validation.isPass() ? "confirmed" : "needs_fix",
                    viewAssembler.writeJson(command.getFields()),
                    viewAssembler.writeJson(payload),
                    validation.status,
                    validation.message,
                    trimNote(command)
            );
        }
        if ("reject".equals(action)) {
            return resolved(
                    "rejected",
                    null,
                    viewAssembler.currentPayloadJson(row),
                    viewAssembler.currentValidationStatus(row),
                    null,
                    trimNote(command)
            );
        }
        if ("keep_old".equals(action)) {
            Map<String, Object> oldPayload = FileParseCommissionPayloadNormalizer.normalize(
                    row.getItemType(),
                    viewAssembler.readMap(row.getOldPayloadJson())
            );
            if (oldPayload.isEmpty()) {
                throw new IllegalArgumentException("新增项没有旧值，不能保留旧值。");
            }
            return resolved(
                    "keep_old",
                    null,
                    viewAssembler.writeJson(oldPayload),
                    "pass",
                    null,
                    trimNote(command)
            );
        }
        throw new IllegalArgumentException("不支持的处理动作：" + action);
    }

    private FileParseReviewResolution resolved(
            String reviewStatus,
            String overridePayloadJson,
            String effectivePayloadJson,
            String validationStatus,
            String validationMessage,
            String note
    ) {
        return new FileParseReviewResolution(
                reviewStatus,
                overridePayloadJson,
                effectivePayloadJson,
                validationStatus,
                validationMessage,
                sha256(effectivePayloadJson == null ? "" : effectivePayloadJson),
                note
        );
    }

    private FileParseValidationResult validatePayload(FileParseItemStandardRow standard, Map<String, Object> payload) {
        Map<String, Object> validationRule = standard == null ? Map.of() : viewAssembler.readMap(standard.getValidationRuleJson());
        Object requiredValue = validationRule.get("required");
        List<String> missing = new ArrayList<>();
        if (requiredValue instanceof List) {
            for (Object value : (List<?>) requiredValue) {
                if (value instanceof String && !StringUtils.hasText(text(payload.get(value)))) {
                    missing.add((String) value);
                }
            }
        }
        if (missing.isEmpty()) {
            return new FileParseValidationResult("pass", null);
        }
        return new FileParseValidationResult("hard_error", "缺少必填字段：" + String.join("、", missing));
    }

    private FileParseResultItemRow requireResultItem(FileParseTaskRow task, Long itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("结果行 ID 不能为空。");
        }
        FileParseResultItemRow row = fileManagementParseMapper.selectResultItem(task.getId(), itemId);
        if (row == null) {
            throw new IllegalArgumentException("解析结果行不存在或已删除。");
        }
        if (!task.getCurrentResultId().equals(row.getResultId())) {
            throw new IllegalArgumentException("结果行不属于当前解析结果。");
        }
        return row;
    }

    private void requireEditableTask(FileParseTaskRow task) {
        String status = task == null ? null : task.getStatus();
        if (!"review_required".equals(status) && !"ready_to_publish".equals(status)) {
            throw new IllegalArgumentException("当前解析文档状态不可处理：" + status);
        }
    }

    private void requireCurrentResult(FileParseTaskRow task) {
        if (task == null || task.getCurrentResultId() == null) {
            throw new IllegalArgumentException("当前解析文档还没有解析结果。");
        }
    }

    private void validateExpectedResult(FileParseTaskRow task, FileParseReviewCommand command) {
        if (command == null || command.getExpectedResultId() == null) {
            throw new IllegalArgumentException("expectedResultId 不能为空。");
        }
        if (!command.getExpectedResultId().equals(task.getCurrentResultId())) {
            throw new IllegalStateException("当前解析结果已变化，请刷新页面后重试。");
        }
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

    private Set<Long> normalizeBatchItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("请先选择需要确认的解析结果。");
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            if (itemId != null) {
                normalized.add(itemId);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请先选择需要确认的解析结果。");
        }
        if (normalized.size() > 200) {
            throw new IllegalArgumentException("单次批量确认最多支持 200 条。");
        }
        return normalized;
    }

    private String childIdempotencyKey(String rootKey, Long itemId) {
        String key = rootKey + "-" + itemId;
        if (key.length() <= 180) {
            return key;
        }
        return "batch-review-" + sha256(key);
    }

    private String normalizeAction(String action) {
        if ("accept".equals(action) || "edit".equals(action) || "reject".equals(action) || "keep_old".equals(action)) {
            return action;
        }
        throw new IllegalArgumentException("不支持的处理动作：" + action);
    }

    private String trimNote(FileParseReviewCommand command) {
        String note = command == null || !StringUtils.hasText(command.getRemark())
                ? command == null ? null : command.getReason()
                : command.getRemark();
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private String normalizeFilter(String value) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 100;
        }
        return Math.min(pageSize, 1000);
    }

    private Map<String, FileParseItemStandardRow> standardByType(List<FileParseItemStandardRow> itemStandards) {
        if (itemStandards == null) {
            return Map.of();
        }
        return itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, Function.identity(), (left, right) -> left));
    }

    private String requestHash(String action, FileParseReviewCommand command) {
        String payload = action
                + "|"
                + (command == null ? "" : command.getExpectedResultId())
                + "|"
                + (command == null ? "" : viewAssembler.writeJson(command.getFields()))
                + "|"
                + (command == null ? "" : trimNote(command));
        return sha256(payload);
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

}

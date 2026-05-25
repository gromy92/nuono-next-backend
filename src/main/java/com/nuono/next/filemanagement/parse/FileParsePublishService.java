package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import com.nuono.next.logisticsquote.LogisticsQuoteFactLandingResult;
import com.nuono.next.logisticsquote.LogisticsQuoteFactPublisher;
import com.nuono.next.logisticsquote.LogisticsQuoteFactSourceLineage;
import com.nuono.next.logisticsquote.LogisticsQuotePublishedItem;
import com.nuono.next.outboundfee.OfficialOutboundFeeFactLandingResult;
import com.nuono.next.outboundfee.OfficialOutboundFeeFactPublisher;
import com.nuono.next.outboundfee.OfficialOutboundFeePublishedItem;
import com.nuono.next.outboundfee.OfficialOutboundFeeSourceLineage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final FileParsePublishSnapshotBuilder snapshotBuilder;
    private final LogisticsQuoteFactPublisher logisticsQuoteFactPublisher;
    private final OfficialOutboundFeeFactPublisher officialOutboundFeeFactPublisher;

    @Autowired
    public FileParsePublishService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseResultItemViewAssembler viewAssembler,
            LogisticsQuoteFactPublisher logisticsQuoteFactPublisher,
            OfficialOutboundFeeFactPublisher officialOutboundFeeFactPublisher
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.viewAssembler = viewAssembler;
        this.snapshotBuilder = new FileParsePublishSnapshotBuilder(viewAssembler);
        this.logisticsQuoteFactPublisher = logisticsQuoteFactPublisher;
        this.officialOutboundFeeFactPublisher = officialOutboundFeeFactPublisher;
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
        FileParseActiveVersionRow activeVersion = resolveActiveVersion(task);
        snapshotBuilder.validateBaseVersion(task, activeVersion);
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
        List<FileParsePublishSnapshotItem> snapshotItems = snapshotBuilder.buildSnapshot(baseItems, resultItems, itemStandards);
        List<LogisticsQuotePublishedItem> logisticsItems = new ArrayList<>();
        List<OfficialOutboundFeePublishedItem> outboundFeeItems = new ArrayList<>();

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

        for (FileParsePublishSnapshotItem item : snapshotItems) {
            Long versionItemId = fileManagementParseMapper.nextVersionItemId();
            int itemInserted = fileManagementParseMapper.insertVersionItem(
                    versionItemId,
                    versionId,
                    targetPlan.getId(),
                    item.getItemType(),
                    item.getNaturalKey(),
                    item.getNaturalKeyHash(),
                    item.getPayloadJson(),
                    item.getSourceResultItemId(),
                    GLOBAL_SCOPE_TYPE,
                    GLOBAL_SCOPE_KEY,
                    item.getSortNo(),
                    operatorUserId
            );
            if (itemInserted != 1) {
                throw new IllegalStateException("发布版本快照写入失败。");
            }
            LogisticsQuotePublishedItem logisticsItem = toLogisticsPublishedItem(task, versionId, versionItemId, item);
            if (logisticsItem != null) {
                logisticsItems.add(logisticsItem);
            }
            OfficialOutboundFeePublishedItem outboundFeeItem = toOfficialOutboundFeePublishedItem(task, versionId, versionItemId, item);
            if (outboundFeeItem != null) {
                outboundFeeItems.add(outboundFeeItem);
            }
        }
        LogisticsQuoteFactLandingResult logisticsLandingResult = landLogisticsFacts(logisticsItems);
        OfficialOutboundFeeFactLandingResult outboundFeeLandingResult = landOfficialOutboundFeeFacts(outboundFeeItems);

        fileManagementParseMapper.markVersionsHistory(targetPlan.getId(), GLOBAL_SCOPE_TYPE, GLOBAL_SCOPE_KEY, versionId, operatorUserId);
        int activeUpdated = fileManagementParseMapper.updateActiveVersion(
                targetPlan.getId(),
                GLOBAL_SCOPE_TYPE,
                GLOBAL_SCOPE_KEY,
                versionId,
                versionNo,
                operatorUserId
        );
        if (activeUpdated == 0) {
            activeUpdated = fileManagementParseMapper.upsertActiveVersion(
                    fileManagementParseMapper.nextActiveVersionId(),
                    targetPlan.getId(),
                    GLOBAL_SCOPE_TYPE,
                    GLOBAL_SCOPE_KEY,
                    versionId,
                    versionNo,
                    operatorUserId
            );
        }
        if (activeUpdated < 1) {
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
        applyLogisticsLandingResult(view, logisticsLandingResult);
        applyOfficialOutboundFeeLandingResult(view, outboundFeeLandingResult);
        return view;
    }

    private LogisticsQuoteFactLandingResult landLogisticsFacts(List<LogisticsQuotePublishedItem> logisticsItems) {
        LogisticsQuoteFactLandingResult result = new LogisticsQuoteFactLandingResult();
        if (logisticsQuoteFactPublisher == null || logisticsItems.isEmpty()) {
            return result;
        }
        return logisticsQuoteFactPublisher.land(logisticsItems);
    }

    private OfficialOutboundFeeFactLandingResult landOfficialOutboundFeeFacts(List<OfficialOutboundFeePublishedItem> outboundFeeItems) {
        OfficialOutboundFeeFactLandingResult result = new OfficialOutboundFeeFactLandingResult();
        if (officialOutboundFeeFactPublisher == null || outboundFeeItems.isEmpty()) {
            return result;
        }
        return officialOutboundFeeFactPublisher.landFullSnapshot(outboundFeeItems);
    }

    private LogisticsQuotePublishedItem toLogisticsPublishedItem(
            FileParseTaskRow task,
            Long versionId,
            Long versionItemId,
            FileParsePublishSnapshotItem item
    ) {
        if (!FileParseLogisticsQuoteStandard.structuredItemTypeNames().contains(item.getItemType())) {
            return null;
        }
        return new LogisticsQuotePublishedItem(
                item.getItemType(),
                item.getNaturalKey(),
                viewAssembler.readMap(item.getPayloadJson()),
                new LogisticsQuoteFactSourceLineage(
                        "file_management",
                        task.getId(),
                        task.getCurrentResultId(),
                        versionId,
                        versionItemId,
                        task.getDocumentTitle(),
                        "version_item:" + versionItemId
                )
        );
    }

    private OfficialOutboundFeePublishedItem toOfficialOutboundFeePublishedItem(
            FileParseTaskRow task,
            Long versionId,
            Long versionItemId,
            FileParsePublishSnapshotItem item
    ) {
        if (!FileParseOfficialOutboundFeeStandard.structuredItemTypeNames().contains(item.getItemType())) {
            return null;
        }
        return new OfficialOutboundFeePublishedItem(
                item.getItemType(),
                item.getNaturalKey(),
                viewAssembler.readMap(item.getPayloadJson()),
                new OfficialOutboundFeeSourceLineage(
                        "file_management",
                        task.getId(),
                        task.getCurrentResultId(),
                        versionId,
                        versionItemId,
                        task.getDocumentTitle(),
                        "version_item:" + versionItemId
                )
        );
    }

    private void applyLogisticsLandingResult(FileParsePublishView view, LogisticsQuoteFactLandingResult result) {
        view.setLogisticsFactInsertedCount(result.getInsertedCount());
        view.setLogisticsFactUnchangedCount(result.getUnchangedCount());
        view.setLogisticsFactSupersededCount(result.getSupersededCount());
        view.setLogisticsFactSkippedCount(result.getSkippedCount());
        view.setLogisticsFactConflictCount(result.getConflictCount());
    }

    private void applyOfficialOutboundFeeLandingResult(FileParsePublishView view, OfficialOutboundFeeFactLandingResult result) {
        view.setOutboundFeeFactInsertedCount(result.getInsertedCount());
        view.setOutboundFeeFactUnchangedCount(result.getUnchangedCount());
        view.setOutboundFeeFactSupersededCount(result.getSupersededCount());
        view.setOutboundFeeFactSkippedCount(result.getSkippedCount());
        view.setOutboundFeeFactConflictCount(result.getConflictCount());
    }

    private FileParseActiveVersionRow resolveActiveVersion(FileParseTaskRow task) {
        FileParseActiveVersionRow activeVersion = fileManagementParseMapper.selectActiveVersionForUpdate(
                task.getTargetPlanId(),
                GLOBAL_SCOPE_TYPE,
                GLOBAL_SCOPE_KEY
        );
        if (activeVersion == null) {
            activeVersion = new FileParseActiveVersionRow();
            activeVersion.setTargetPlanId(task.getTargetPlanId());
            activeVersion.setDataScopeType(GLOBAL_SCOPE_TYPE);
            activeVersion.setDataScopeKey(GLOBAL_SCOPE_KEY);
        }
        return activeVersion;
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
            List<FileParsePublishSnapshotItem> snapshotItems,
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

package com.nuono.next.procurement;

import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_NO_REPLY_HANDOFF;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_PARTIAL_REPLY;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REMOVED_TERMINATED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REPLIED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.ITEM_REPLY_PARSE_FAILED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_INQUIRY_FINISHED;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_INQUIRY_RUNNING;
import static com.nuono.next.procurement.ProcurementCandidatePoolStatusPolicy.STATUS_POOL_PARTIAL_HANDOFF;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.AdvancePoolItemFollowUpCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.MarkPoolItemExceptionCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProcurementPoolInquiryService {

    private final ProcurementRequirementConfirmationMapper mapper;
    private final ProcurementMapper procurementMapper;
    private final LocalDbProcurementRequirementConfirmationService readService;
    private final ProcurementAutoInquiryEventLogger autoInquiryEventLogger;
    private final ProcurementCandidatePoolAuditService auditService;
    private final ProcurementCandidatePoolPermissionGuard permissionGuard;
    private final ProcurementPoolWriteSupport support;

    ProcurementPoolInquiryService(
            ProcurementRequirementConfirmationMapper mapper,
            ProcurementMapper procurementMapper,
            LocalDbProcurementRequirementConfirmationService readService,
            ProcurementAutoInquiryEventLogger autoInquiryEventLogger,
            ProcurementCandidatePoolAuditService auditService,
            ProcurementCandidatePoolPermissionGuard permissionGuard,
            ProcurementPoolWriteSupport support
    ) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.readService = readService;
        this.autoInquiryEventLogger = autoInquiryEventLogger;
        this.auditService = auditService;
        this.permissionGuard = permissionGuard;
        this.support = support;
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView finishInquiry(Long demandItemId, FinishPoolInquiryCommand command) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "收口自动询价");
        support.requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = support.requirePool(demandItemId);
        String poolStatus = support.upper(pool.getStatus());
        if (!STATUS_POOL_INQUIRY_RUNNING.equals(poolStatus) && !STATUS_POOL_PARTIAL_HANDOFF.equals(poolStatus)) {
            throw new IllegalStateException("当前阶段不允许收口询价。");
        }

        List<PoolItemLockRow> currentItems = mapper.listCurrentPoolItemsForUpdate(pool.getPoolId());
        if (currentItems.isEmpty()) {
            throw new IllegalStateException("当前待选池为空，不能收口询价。");
        }

        boolean manualConfirm = Boolean.TRUE.equals(command.getForce())
                || "MANUAL_CONFIRM".equals(support.upper(command.getFinishMode()));
        if (!manualConfirm) {
            List<Long> activePoolItemIds = new ArrayList<>();
            for (PoolItemLockRow item : currentItems) {
                if (!ProcurementCandidatePoolStatusPolicy.isClosableItemStatus(item.getStatus())) {
                    activePoolItemIds.add(item.getPoolItemId());
                }
            }
            if (!activePoolItemIds.isEmpty()) {
                throw new IllegalStateException("仍有候选正在询价中，请等待收口或先转人工处理。");
            }
        }

        Long snapshotId = auditService.createSnapshot(pool, "INQUIRY_FINISHED", STATUS_POOL_INQUIRY_FINISHED, "采购确认询价收口。", null, context.operatorUserId);
        mapper.updatePoolInquiryFinished(pool.getPoolId(), snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                null,
                "POOL_INQUIRY_FINISHED",
                context,
                pool.getStatus(),
                STATUS_POOL_INQUIRY_FINISHED,
                snapshotId,
                support.firstNonBlank(command.getNote(), "采购确认当前待选池询价结果可进入最终 2 个选择。"),
                Map.of("finishMode", support.firstNonBlank(command.getFinishMode(), "AUTO_CHECK"))
        );

        return support.detail(demandItemId, context.ownerUserId, "询价已收口，可以确认最终 2 个。");
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView recordPoolItemReply(
            Long demandItemId,
            Long poolItemId,
            RecordPoolItemReplyCommand command
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "记录供应商回复");
        support.requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = support.requirePool(demandItemId);
        support.requirePoolItemChangeAllowed(pool.getStatus(), "当前阶段不允许记录供应商回复。");
        PoolItemLockRow item = support.requireCurrentPoolItem(pool, poolItemId, "该候选不在当前待选池中。");
        String beforeStatus = support.upper(item.getStatus());
        if ("CLOSED".equals(beforeStatus)) {
            throw new IllegalStateException("该候选询价已收口，不能再记录回复。");
        }

        String quotePriceText = support.normalize(command.getQuotePriceText());
        String quoteMoqText = support.normalize(command.getQuoteMoqText());
        String quoteDeliveryText = support.normalize(command.getQuoteDeliveryText());
        String nextItemStatus = StringUtils.hasText(quotePriceText) && StringUtils.hasText(quoteMoqText)
                ? ITEM_REPLIED
                : ITEM_PARTIAL_REPLY;
        String replySummary = support.firstNonBlank(
                command.getReplySummary(),
                ProcurementCandidatePoolStatusPolicy.buildReplySummary(quotePriceText, quoteMoqText, quoteDeliveryText, nextItemStatus)
        );
        String riskNote = support.normalize(command.getRiskNote());

        mapper.updatePoolItemReply(
                pool.getPoolId(),
                poolItemId,
                nextItemStatus,
                quotePriceText,
                quoteMoqText,
                quoteDeliveryText,
                replySummary,
                riskNote,
                context.operatorUserId
        );
        if (item.getInquiryTaskId() != null) {
            mapper.markPoolAutoInquiryTaskChatting(
                    item.getInquiryTaskId(),
                    replySummary,
                    "采购已记录供应商回复。",
                    context.operatorUserId
            );
            autoInquiryEventLogger.append(
                    item.getInquiryTaskId(),
                    "SUPPLIER_REPLY_RECORDED",
                    null,
                    ProcurementAutoInquiryLifecycle.STATUS_CHATTING,
                    "REPLY_RECORDED",
                    replySummary,
                    "poolItemId=" + poolItemId,
                    context.operatorUserId
            );
        }

        String nextPoolStatus = support.resolvePoolStatusAfterCurrentItems(pool.getPoolId(), pool.getStatus());
        Long snapshotId = auditService.createSnapshot(pool, "INQUIRY_REPLY_RECORDED", nextPoolStatus, "采购记录供应商回复。", null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), nextPoolStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                item,
                "REPLY_RECORDED",
                context,
                beforeStatus,
                nextItemStatus,
                snapshotId,
                replySummary,
                support.detailMap(
                        "quotePriceText", quotePriceText,
                        "quoteMoqText", quoteMoqText,
                        "quoteDeliveryText", quoteDeliveryText,
                        "riskNote", riskNote
                )
        );

        return support.detail(demandItemId, context.ownerUserId, "供应商回复已记录。");
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView advancePoolItemFollowUp(
            Long demandItemId,
            Long poolItemId,
            AdvancePoolItemFollowUpCommand command
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "推进催发");
        return advancePoolItemFollowUp(demandItemId, poolItemId, command, context);
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView advancePoolItemFollowUpBySystem(
            Long ownerUserId,
            Long demandItemId,
            Long poolItemId,
            String expectedStatus,
            String note
    ) {
        AdvancePoolItemFollowUpCommand command = new AdvancePoolItemFollowUpCommand();
        command.setOwnerUserId(ownerUserId);
        command.setExpectedStatus(expectedStatus);
        command.setNote(note);
        return advancePoolItemFollowUp(demandItemId, poolItemId, command, permissionGuard.systemWriteContext(ownerUserId));
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView markNoReplyHandoff(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command
    ) {
        return markPoolItemHandoff(
                demandItemId,
                poolItemId,
                command,
                ITEM_NO_REPLY_HANDOFF,
                "NO_REPLY_HANDOFF",
                "24 小时无回复，已要求人工介入。",
                "NO_REPLY_24H",
                "24 小时无回复，自动询价转人工处理。"
        );
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView markNoReplyHandoffBySystem(
            Long ownerUserId,
            Long demandItemId,
            Long poolItemId,
            String expectedStatus,
            String reason,
            String replySummary,
            String riskNote
    ) {
        MarkPoolItemExceptionCommand command = new MarkPoolItemExceptionCommand();
        command.setOwnerUserId(ownerUserId);
        command.setReason(reason);
        command.setReplySummary(replySummary);
        command.setRiskNote(riskNote);
        command.setExpectedStatus(expectedStatus);
        return markPoolItemHandoff(
                demandItemId,
                poolItemId,
                command,
                ITEM_NO_REPLY_HANDOFF,
                "NO_REPLY_HANDOFF",
                "24 小时无回复，已要求人工介入。",
                "NO_REPLY_24H",
                "24 小时无回复，自动询价转人工处理。",
                permissionGuard.systemWriteContext(ownerUserId)
        );
    }

    @Transactional
    ProcurementRequirementConfirmationDetailView markReplyParseFailure(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command
    ) {
        return markPoolItemHandoff(
                demandItemId,
                poolItemId,
                command,
                ITEM_REPLY_PARSE_FAILED,
                "REPLY_PARSE_FAILED",
                "已回复，但结构化解析失败，需要人工补录关键字段。",
                "REPLY_PARSE_FAILED",
                "供应商回复解析失败，自动询价转人工处理。"
        );
    }

    private ProcurementRequirementConfirmationDetailView advancePoolItemFollowUp(
            Long demandItemId,
            Long poolItemId,
            AdvancePoolItemFollowUpCommand command,
            ProcurementCandidatePoolWriteContext context
    ) {
        support.requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = support.requirePool(demandItemId);
        support.requirePoolItemChangeAllowed(pool.getStatus(), "当前阶段不允许推进催发。");
        PoolItemLockRow item = support.requireCurrentPoolItem(pool, poolItemId, "该候选不在当前待选池中。");
        String beforeStatus = support.upper(item.getStatus());
        support.requireExpectedStatus(command.getExpectedStatus(), beforeStatus);
        String nextItemStatus = ProcurementCandidatePoolStatusPolicy.nextFollowUpStatus(beforeStatus);
        if (nextItemStatus == null) {
            throw new IllegalStateException("当前候选状态不能继续催发。");
        }
        String replySummary = support.firstNonBlank(command.getNote(), ProcurementCandidatePoolStatusPolicy.followUpSummary(nextItemStatus));

        mapper.updatePoolItemFollowUp(
                pool.getPoolId(),
                poolItemId,
                nextItemStatus,
                replySummary,
                context.operatorUserId
        );
        if (item.getInquiryTaskId() != null) {
            mapper.markPoolAutoInquiryTaskFollowUp(
                    item.getInquiryTaskId(),
                    nextItemStatus,
                    replySummary,
                    context.operatorUserId
            );
            autoInquiryEventLogger.append(
                    item.getInquiryTaskId(),
                    "FOLLOW_UP_SENT",
                    null,
                    ProcurementAutoInquiryLifecycle.STATUS_SENT,
                    nextItemStatus,
                    replySummary,
                    "poolItemId=" + poolItemId,
                    context.operatorUserId
            );
        }

        String nextPoolStatus = support.resolvePoolStatusAfterCurrentItems(pool.getPoolId(), pool.getStatus());
        Long snapshotId = auditService.createSnapshot(pool, "INQUIRY_FOLLOW_UP_SENT", nextPoolStatus, "采购推进催发状态。", null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), nextPoolStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                item,
                "FOLLOW_UP_ADVANCED",
                context,
                beforeStatus,
                nextItemStatus,
                snapshotId,
                replySummary,
                Map.of("poolItemId", poolItemId)
        );

        return support.detail(demandItemId, context.ownerUserId, "催发状态已更新。");
    }

    private ProcurementRequirementConfirmationDetailView markPoolItemHandoff(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command,
            String nextItemStatus,
            String operationType,
            String defaultSummary,
            String failureCode,
            String taskMessage
    ) {
        ProcurementCandidatePoolWriteContext context = permissionGuard.resolveWriteContext(command, "标记询价异常");
        return markPoolItemHandoff(
                demandItemId,
                poolItemId,
                command,
                nextItemStatus,
                operationType,
                defaultSummary,
                failureCode,
                taskMessage,
                context
        );
    }

    private ProcurementRequirementConfirmationDetailView markPoolItemHandoff(
            Long demandItemId,
            Long poolItemId,
            MarkPoolItemExceptionCommand command,
            String nextItemStatus,
            String operationType,
            String defaultSummary,
            String failureCode,
            String taskMessage,
            ProcurementCandidatePoolWriteContext context
    ) {
        support.requireDemandForUpdate(demandItemId, context.ownerUserId);
        PoolLockRow pool = support.requirePool(demandItemId);
        support.requirePoolItemChangeAllowed(pool.getStatus(), "当前阶段不允许标记询价异常。");
        PoolItemLockRow item = support.requireCurrentPoolItem(pool, poolItemId, "该候选不在当前待选池中。");
        String beforeStatus = support.upper(item.getStatus());
        support.requireExpectedStatus(command.getExpectedStatus(), beforeStatus);
        if ("CLOSED".equals(beforeStatus)) {
            throw new IllegalStateException("该候选询价已收口，不能再标记异常。");
        }

        String replySummary = support.firstNonBlank(command.getReplySummary(), defaultSummary);
        String riskNote = support.firstNonBlank(command.getRiskNote(), support.firstNonBlank(command.getReason(), defaultSummary));
        mapper.updatePoolItemHandoff(
                pool.getPoolId(),
                poolItemId,
                nextItemStatus,
                replySummary,
                riskNote,
                context.operatorUserId
        );
        if (item.getInquiryTaskId() != null) {
            procurementMapper.markAutoInquiryTaskHandoff(
                    item.getInquiryTaskId(),
                    nextItemStatus,
                    failureCode,
                    riskNote,
                    nextItemStatus,
                    taskMessage,
                    context.operatorUserId
            );
            mapper.releaseAutoInquirySessionByTask(item.getInquiryTaskId(), taskMessage, context.operatorUserId);
            autoInquiryEventLogger.append(
                    item.getInquiryTaskId(),
                    ProcurementAutoInquiryLifecycle.EVENT_TASK_HANDOFF,
                    null,
                    ProcurementAutoInquiryLifecycle.STATUS_HANDOFF,
                    nextItemStatus,
                    taskMessage,
                    "poolItemId=" + poolItemId,
                    context.operatorUserId
            );
        }

        String nextPoolStatus = support.resolvePoolStatusAfterCurrentItems(pool.getPoolId(), STATUS_POOL_PARTIAL_HANDOFF);
        Long snapshotId = auditService.createSnapshot(pool, operationType, nextPoolStatus, defaultSummary, null, context.operatorUserId);
        mapper.updatePoolAfterItemChange(pool.getPoolId(), nextPoolStatus, snapshotId, context.operatorUserId);
        auditService.appendLog(
                pool,
                item,
                operationType,
                context,
                beforeStatus,
                nextItemStatus,
                snapshotId,
                support.firstNonBlank(command.getReason(), defaultSummary),
                support.detailMap("replySummary", replySummary, "riskNote", riskNote, "failureCode", failureCode)
        );

        return support.detail(demandItemId, context.ownerUserId, defaultSummary);
    }

}

package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementAutoInquiryMessageAssembler.MessageDraft;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendAttemptResult;
import com.nuono.next.procurement.ProcurementAutoInquirySendGateway.SendPreparationResult;
import com.nuono.next.procurement.ProcurementAutoInquirySessionManager.SessionLease;
import com.nuono.next.procurement.ProcurementAutoInquiryTargetResolver.TargetResolution;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-db")
public class ProcurementAutoInquiryExecutor {

    private final ProcurementMapper procurementMapper;
    private final ProcurementRequirementConfirmationMapper procurementRequirementConfirmationMapper;
    private final ProcurementAutoInquiryEventLogger procurementAutoInquiryEventLogger;
    private final ProcurementAutoInquirySessionManager procurementAutoInquirySessionManager;
    private final ProcurementAutoInquiryTargetResolver procurementAutoInquiryTargetResolver;
    private final ProcurementStructuredFieldParser procurementStructuredFieldParser;
    private final ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor;
    private final ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor;
    private final ProcurementAutoInquiryMessageAssembler procurementAutoInquiryMessageAssembler;
    private final ProcurementAutoInquirySendGateway procurementAutoInquirySendGateway;

    public ProcurementAutoInquiryExecutor(
            ProcurementMapper procurementMapper,
            ProcurementRequirementConfirmationMapper procurementRequirementConfirmationMapper,
            ProcurementAutoInquiryEventLogger procurementAutoInquiryEventLogger,
            ProcurementAutoInquirySessionManager procurementAutoInquirySessionManager,
            ProcurementAutoInquiryTargetResolver procurementAutoInquiryTargetResolver,
            ProcurementStructuredFieldParser procurementStructuredFieldParser,
            ProcurementDecisionSupportAdvisor procurementDecisionSupportAdvisor,
            ProcurementInquiryPreparationAdvisor procurementInquiryPreparationAdvisor,
            ProcurementAutoInquiryMessageAssembler procurementAutoInquiryMessageAssembler,
            ProcurementAutoInquirySendGateway procurementAutoInquirySendGateway
    ) {
        this.procurementMapper = procurementMapper;
        this.procurementRequirementConfirmationMapper = procurementRequirementConfirmationMapper;
        this.procurementAutoInquiryEventLogger = procurementAutoInquiryEventLogger;
        this.procurementAutoInquirySessionManager = procurementAutoInquirySessionManager;
        this.procurementAutoInquiryTargetResolver = procurementAutoInquiryTargetResolver;
        this.procurementStructuredFieldParser = procurementStructuredFieldParser;
        this.procurementDecisionSupportAdvisor = procurementDecisionSupportAdvisor;
        this.procurementInquiryPreparationAdvisor = procurementInquiryPreparationAdvisor;
        this.procurementAutoInquiryMessageAssembler = procurementAutoInquiryMessageAssembler;
        this.procurementAutoInquirySendGateway = procurementAutoInquirySendGateway;
    }

    @Transactional
    public void execute(Long taskId, Long operatorUserId) {
        if (taskId == null) {
            return;
        }

        AutoInquiryTaskView existingTask = procurementMapper.selectAutoInquiryTask(taskId);
        if (existingTask == null) {
            return;
        }

        String currentStatus = ProcurementAutoInquiryLifecycle.upper(existingTask.getStatus());
        String currentStage = ProcurementAutoInquiryLifecycle.upper(existingTask.getExecutionStage());
        if (ProcurementAutoInquiryLifecycle.STATUS_SENT.equals(currentStatus)
                || ProcurementAutoInquiryLifecycle.STATUS_CHATTING.equals(currentStatus)
                || ProcurementAutoInquiryLifecycle.STATUS_CLOSED.equals(currentStatus)
                || ProcurementAutoInquiryLifecycle.STATUS_HANDOFF.equals(currentStatus)) {
            return;
        }

        if (ProcurementAutoInquiryLifecycle.STATUS_RUNNING.equals(currentStatus)
                && (ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY.equals(currentStage)
                || ProcurementAutoInquiryLifecycle.STAGE_SEND_PREPARED.equals(currentStage))) {
            continueSend(taskId, existingTask, operatorUserId);
            return;
        }

        int claimedRows = procurementMapper.claimAutoInquiryTask(taskId, operatorUserId);
        if (claimedRows <= 0) {
            return;
        }

        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_TASK_CLAIMED,
                existingTask.getStatus(),
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_CLAIMED,
                "执行器已接手当前自动询价任务，开始准备会话与聊天目标。",
                null,
                operatorUserId
        );

        AutoInquiryTaskView runningTask = procurementMapper.selectAutoInquiryTask(taskId);
        if (runningTask == null) {
            return;
        }

        DemandItemView demandItem = procurementMapper.selectOwnedDemandItem(
                runningTask.getOwnerUserId(),
                runningTask.getDemandItemId()
        );
        CandidateView candidate = procurementMapper.selectOwnedCandidate(
                runningTask.getOwnerUserId(),
                runningTask.getDemandItemId(),
                runningTask.getCandidateId()
        );
        if (demandItem == null || candidate == null) {
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_CLAIMED,
                    "MISSING_CONTEXT",
                    "当前需求或候选上下文缺失，任务已转人工接管。",
                    "缺少需求或候选上下文",
                    operatorUserId
            );
            return;
        }

        SessionLease sessionLease = procurementAutoInquirySessionManager.reserveReadySession(taskId, operatorUserId);
        if (!sessionLease.isLeased()) {
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_CLAIMED,
                    sessionLease.getFailureCode(),
                    sessionLease.getFailureMessage(),
                    "当前没有可直接复用的 1688 托管会话",
                    operatorUserId
            );
            return;
        }

        AutoInquirySessionView sessionView = sessionLease.getSession();
        procurementMapper.updateAutoInquiryTaskSessionReady(
                taskId,
                sessionView.getId(),
                "已锁定托管会话，继续解析聊天目标。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_SESSION_ALLOCATED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_SESSION_READY,
                "自动询价任务已拿到服务端托管会话。",
                "sessionKey=" + sessionView.getSessionKey() + "；account=" + sessionView.getAccountLabel(),
                operatorUserId
        );

        TargetResolution targetResolution = procurementAutoInquiryTargetResolver.resolve(candidate);
        if (!targetResolution.isResolved()) {
            procurementAutoInquirySessionManager.releaseSession(
                    sessionView.getId(),
                    operatorUserId,
                    "目标命中失败后释放"
            );
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_SESSION_READY,
                    targetResolution.getFailureCode(),
                    targetResolution.getFailureMessage(),
                    "聊天目标未能安全命中",
                    operatorUserId
            );
            return;
        }

        procurementMapper.updateAutoInquiryTaskTargetResolved(
                taskId,
                targetResolution.getPlatform(),
                targetResolution.getOfferId(),
                targetResolution.getSupplierIdentity(),
                targetResolution.getEntryUrl(),
                targetResolution.getLocatorText(),
                "已完成目标命中，继续准备输入内容。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_TARGET_RESOLVED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_TARGET_RESOLVED,
                "已根据 offerId 和供应商主体生成聊天目标定位信息。",
                targetResolution.getLocatorText(),
                operatorUserId
        );

        procurementStructuredFieldParser.enrichDemandItem(demandItem);
        procurementStructuredFieldParser.enrichCandidate(candidate);
        procurementDecisionSupportAdvisor.enrichCandidateDecisionSupport(demandItem, candidate);
        procurementInquiryPreparationAdvisor.enrichCandidateInquiryPreparation(demandItem, candidate);

        MessageDraft messageDraft;
        try {
            messageDraft = procurementAutoInquiryMessageAssembler.assemble(demandItem, candidate);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            procurementAutoInquirySessionManager.releaseSession(
                    sessionView.getId(),
                    operatorUserId,
                    "输入内容准备失败后释放"
            );
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_TARGET_RESOLVED,
                    "INPUT_PREPARATION_FAILED",
                    exception.getMessage(),
                    "自动询价输入内容尚未稳定可用",
                    operatorUserId
            );
            return;
        }

        procurementMapper.updateAutoInquiryTaskInputReady(
                taskId,
                messageDraft.getPreviewText(),
                messageDraft.getPayloadText(),
                messageDraft.getPayloadHash(),
                "已完成输入区命中与填充前准备，等待发送链路接手。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_INPUT_PREPARED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY,
                "已生成可供后续发送执行器复用的输入内容。",
                "hash=" + messageDraft.getPayloadHash(),
                operatorUserId
        );

        AutoInquiryTaskView inputReadyTask = procurementMapper.selectAutoInquiryTask(taskId);
        if (inputReadyTask != null) {
            continueSend(taskId, inputReadyTask, operatorUserId);
        }
    }

    private void continueSend(Long taskId, AutoInquiryTaskView task, Long operatorUserId) {
        AutoInquirySessionView sessionView = resolveTaskSession(task, operatorUserId);
        if (sessionView == null) {
            markTaskHandoff(
                    taskId,
                    firstNonBlank(task.getExecutionStage(), ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY),
                    "MISSING_SESSION",
                    "当前任务还没有有效的托管会话，暂时不能继续自动发送。",
                    "发送阶段缺少托管会话",
                    operatorUserId
            );
            return;
        }

        SendPreparationResult preparationResult;
        try {
            preparationResult = procurementAutoInquirySendGateway.prepareInput(task, sessionView);
        } catch (IllegalStateException exception) {
            releaseTaskSession(sessionView.getId(), operatorUserId, "发送准备异常后释放");
            markTaskHandoff(
                    taskId,
                    firstNonBlank(task.getExecutionStage(), ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY),
                    "SEND_PREPARATION_EXCEPTION",
                    exception.getMessage(),
                    "发送准备阶段异常",
                    operatorUserId
            );
            return;
        }
        if (!preparationResult.isReady()) {
            releaseTaskSession(sessionView.getId(), operatorUserId, "发送准备失败后释放");
            markTaskHandoff(
                    taskId,
                    firstNonBlank(task.getExecutionStage(), ProcurementAutoInquiryLifecycle.STAGE_INPUT_READY),
                    preparationResult.getFailureCode(),
                    preparationResult.getFailureMessage(),
                    "发送前未能确认输入区已命中",
                    operatorUserId
            );
            return;
        }

        String sendChannel = resolveSendChannel(sessionView);
        procurementMapper.updateAutoInquiryTaskSendPrepared(
                taskId,
                preparationResult.getInputLocator(),
                sendChannel,
                firstNonBlank(preparationResult.getEvidence(), preparationResult.getContentEcho()),
                "发送前证据已确认，继续执行发送动作。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_SEND_PREPARED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_SEND_PREPARED,
                "已回读输入区定位与内容证据，准备执行发送。",
                buildPreparationPayload(preparationResult, sendChannel),
                operatorUserId
        );

        AutoInquiryTaskView sendPreparedTask = procurementMapper.selectAutoInquiryTask(taskId);
        if (sendPreparedTask == null) {
            return;
        }

        SendAttemptResult sendAttemptResult;
        try {
            sendAttemptResult = procurementAutoInquirySendGateway.send(sendPreparedTask, sessionView);
        } catch (IllegalStateException exception) {
            releaseTaskSession(sessionView.getId(), operatorUserId, "发送执行异常后释放");
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_SEND_PREPARED,
                    "SEND_EXECUTION_EXCEPTION",
                    exception.getMessage(),
                    "发送执行阶段异常",
                    operatorUserId
            );
            return;
        }
        if (!sendAttemptResult.isDelivered()) {
            releaseTaskSession(sessionView.getId(), operatorUserId, "发送未确认后释放");
            markTaskHandoff(
                    taskId,
                    ProcurementAutoInquiryLifecycle.STAGE_SEND_PREPARED,
                    sendAttemptResult.getFailureCode(),
                    sendAttemptResult.getFailureMessage(),
                    "未拿到已发送确认",
                    operatorUserId
            );
            return;
        }

        procurementMapper.markAutoInquiryTaskSent(
                taskId,
                sendChannel,
                sendAttemptResult.getEvidence(),
                sendAttemptResult.getThreadCheckpoint(),
                firstNonBlank(sendAttemptResult.getMessageDigest(), sendPreparedTask.getInputPayloadHash()),
                "已完成自动发送并拿到发送确认，等待后续回复抓取链路接手。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_SEND_CONFIRMED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_SENT,
                ProcurementAutoInquiryLifecycle.STAGE_SEND_CONFIRMED,
                "已拿到发送确认，任务进入 SENT。",
                buildSendPayload(sendAttemptResult, sendChannel),
                operatorUserId
        );
        syncPoolItemFromTask(taskId, operatorUserId);
        releaseTaskSession(sessionView.getId(), operatorUserId, "发送确认完成后释放");
    }

    private AutoInquirySessionView resolveTaskSession(AutoInquiryTaskView task, Long operatorUserId) {
        if (task.getSessionId() != null) {
            AutoInquirySessionView sessionView = procurementMapper.selectAutoInquirySession(task.getSessionId());
            if (sessionView != null) {
                return sessionView;
            }
        }
        SessionLease sessionLease = procurementAutoInquirySessionManager.reserveReadySession(task.getId(), operatorUserId);
        if (!sessionLease.isLeased()) {
            return null;
        }
        AutoInquirySessionView sessionView = sessionLease.getSession();
        procurementMapper.updateAutoInquiryTaskSessionReady(
                task.getId(),
                sessionView.getId(),
                "已为发送阶段补锁托管会话，继续执行发送。",
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                task.getId(),
                ProcurementAutoInquiryLifecycle.EVENT_SESSION_ALLOCATED,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STAGE_SESSION_READY,
                "发送阶段已补锁托管会话。",
                "sessionKey=" + sessionView.getSessionKey() + "；account=" + sessionView.getAccountLabel(),
                operatorUserId
        );
        return sessionView;
    }

    private void releaseTaskSession(Long sessionId, Long operatorUserId, String note) {
        if (sessionId == null) {
            return;
        }
        procurementAutoInquirySessionManager.releaseSession(sessionId, operatorUserId, note);
    }

    private void markTaskHandoff(
            Long taskId,
            String executionStage,
            String failureCode,
            String failureMessage,
            String handoffReason,
            Long operatorUserId
    ) {
        procurementMapper.markAutoInquiryTaskHandoff(
                taskId,
                executionStage,
                failureCode,
                failureMessage,
                handoffReason,
                failureMessage,
                operatorUserId
        );
        procurementAutoInquiryEventLogger.append(
                taskId,
                ProcurementAutoInquiryLifecycle.EVENT_TASK_HANDOFF,
                ProcurementAutoInquiryLifecycle.STATUS_RUNNING,
                ProcurementAutoInquiryLifecycle.STATUS_HANDOFF,
                executionStage,
                handoffReason,
                failureCode == null ? failureMessage : "failureCode=" + failureCode + "；" + failureMessage,
                operatorUserId
        );
        syncPoolItemFromTask(taskId, operatorUserId);
    }

    private void syncPoolItemFromTask(Long taskId, Long operatorUserId) {
        AutoInquiryTaskView task = procurementMapper.selectAutoInquiryTask(taskId);
        if (task == null || task.getPoolItemId() == null) {
            return;
        }
        procurementRequirementConfirmationMapper.syncPoolItemFromAutoInquiryTask(
                task.getPoolItemId(),
                taskId,
                operatorUserId
        );
    }

    private String buildPreparationPayload(SendPreparationResult result, String sendChannel) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sendChannel)) {
            builder.append("sendChannel=").append(sendChannel);
        }
        if (StringUtils.hasText(result.getInputLocator())) {
            appendWithSeparator(builder, "inputLocator=" + result.getInputLocator());
        }
        if (StringUtils.hasText(result.getEvidence())) {
            appendWithSeparator(builder, "evidence=" + result.getEvidence());
        }
        if (StringUtils.hasText(result.getContentEcho())) {
            appendWithSeparator(builder, "contentEcho=" + result.getContentEcho());
        }
        return builder.toString();
    }

    private String buildSendPayload(SendAttemptResult result, String sendChannel) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sendChannel)) {
            builder.append("sendChannel=").append(sendChannel);
        }
        if (StringUtils.hasText(result.getThreadCheckpoint())) {
            appendWithSeparator(builder, "threadCheckpoint=" + result.getThreadCheckpoint());
        }
        if (StringUtils.hasText(result.getMessageDigest())) {
            appendWithSeparator(builder, "messageDigest=" + result.getMessageDigest());
        }
        if (StringUtils.hasText(result.getEvidence())) {
            appendWithSeparator(builder, "evidence=" + result.getEvidence());
        }
        return builder.toString();
    }

    private void appendWithSeparator(StringBuilder builder, String part) {
        if (builder.length() > 0) {
            builder.append("；");
        }
        builder.append(part);
    }

    private String resolveSendChannel(AutoInquirySessionView sessionView) {
        String endpoint = sessionView == null ? null : sessionView.getBrowserEndpoint();
        if (endpoint == null) {
            return "unknown";
        }
        if (endpoint.startsWith("mock://") || endpoint.startsWith("ws://local-placeholder")) {
            return "mock-browser-gateway";
        }
        return "hosted-browser-gateway";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

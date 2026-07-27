package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementAutoInquiryProbeScopeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAliAiBulkInquiryReadService {

    private final ProcurementAutoInquiryProbeScopeMapper scopeMapper;
    private final AliAiBulkInquiryReadAdapter readAdapter;
    private final AliAiBulkInquiryResultParser resultParser;
    private final ObjectMapper objectMapper;
    private final Ali1688BrowserUrlPolicy urlPolicy;

    public LocalDbAliAiBulkInquiryReadService(
            ProcurementAutoInquiryProbeScopeMapper scopeMapper,
            AliAiBulkInquiryReadAdapter readAdapter,
            AliAiBulkInquiryResultParser resultParser,
            ObjectMapper objectMapper,
            Ali1688BrowserUrlPolicy urlPolicy
    ) {
        this.scopeMapper = scopeMapper;
        this.readAdapter = readAdapter;
        this.resultParser = resultParser;
        this.objectMapper = objectMapper;
        this.urlPolicy = urlPolicy;
    }

    @Transactional
    public AliAiBulkInquiryResultView probeResult(
            Long ownerUserId,
            Long operatorUserId,
            AliAiBulkInquiryResultProbeCommand command
    ) {
        requireScope(ownerUserId, operatorUserId);
        AliAiBulkInquiryResultProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryResultProbeCommand()
                : command;
        requireOwnedTask(ownerUserId, safeCommand.getTaskId());
        safeCommand.setResultUrl(urlPolicy.validateRequestedUrl(
                safeCommand.getResultUrl(),
                Ali1688BrowserUrlPolicy.PageKind.INQUIRY_RESULT
        ));
        AliAiBulkInquiryResultView view = buildProbeResult(safeCommand);
        if (Boolean.TRUE.equals(safeCommand.getPersistResult())) {
            persistProbeResult(ownerUserId, operatorUserId, safeCommand, view);
        }
        return view;
    }

    private AliAiBulkInquiryResultView buildProbeResult(AliAiBulkInquiryResultProbeCommand command) {
        if (StringUtils.hasText(command.getSampleText())) {
            return resultParser.parse(
                    command.getSampleText(),
                    command.getResultUrl(),
                    "sample",
                    command.getExternalInquiryId(),
                    "sample-text"
            );
        }

        AliAiBulkInquiryPageSnapshot snapshot = readAdapter.readResultPage(
                command.getResultUrl(),
                Boolean.TRUE.equals(command.getOpenIfMissing())
        );
        if (!snapshot.isOk()) {
            AliAiBulkInquiryResultView view = new AliAiBulkInquiryResultView();
            view.setReady(true);
            view.setReadable(false);
            view.setSource("chrome");
            view.setResultUrl(snapshot.getUrl());
            view.setPageTitle(snapshot.getTitle());
            view.setExternalInquiryId(command.getExternalInquiryId());
            view.setExternalResultStatus("FAILED");
            view.setReplySource("ALI_AI_RESULT");
            view.setReplyParseStatus("NOT_AVAILABLE");
            view.setReplyParseError(snapshot.getFailureMessage());
            view.setMessage(snapshot.getFailureMessage());
            return view;
        }

        return resultParser.parse(
                snapshot.getText(),
                snapshot.getUrl(),
                snapshot.getTitle(),
                command.getExternalInquiryId(),
                "chrome"
        );
    }

    private void persistProbeResult(
            Long ownerUserId,
            Long operatorUserId,
            AliAiBulkInquiryResultProbeCommand command,
            AliAiBulkInquiryResultView view
    ) {
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("持久化 1688 智能询盘只读结果时必须提供 taskId。");
        }
        if (!view.isReady()) {
            throw new IllegalStateException("1688 智能询盘结果尚未就绪，不能写回任务。");
        }
        String payload = serializeView(view);
        int updatedRows = scopeMapper.updateOwnedAutoInquiryTaskAliAiResult(
                ownerUserId,
                command.getTaskId(),
                view.getExternalInquiryId(),
                view.getResultUrl(),
                view.getExternalResultStatus(),
                payload,
                view.getReplySource(),
                view.getReplyParseStatus(),
                view.getReplyParseError(),
                "已完成 1688 智能询盘结果只读回写。",
                operatorUserId
        );
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("自动询价任务不存在，无法写回 1688 智能询盘结果。");
        }
        view.setPersistedTaskId(command.getTaskId());
    }

    private void requireOwnedTask(Long ownerUserId, Long taskId) {
        if (taskId != null && scopeMapper.selectOwnedAutoInquiryTask(ownerUserId, taskId) == null) {
            throw new IllegalArgumentException("自动询价任务不存在或无权访问。");
        }
    }

    private void requireScope(Long ownerUserId, Long operatorUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("缺少有效的采购业务身份。");
        }
    }

    private String serializeView(AliAiBulkInquiryResultView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 智能询盘结果序列化失败。", exception);
        }
    }

}

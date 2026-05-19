package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAliAiBulkInquiryReadService {

    private final ProcurementMapper procurementMapper;
    private final AliAiBulkInquiryReadAdapter readAdapter;
    private final AliAiBulkInquiryResultParser resultParser;
    private final ObjectMapper objectMapper;

    public LocalDbAliAiBulkInquiryReadService(
            ProcurementMapper procurementMapper,
            AliAiBulkInquiryReadAdapter readAdapter,
            AliAiBulkInquiryResultParser resultParser,
            ObjectMapper objectMapper
    ) {
        this.procurementMapper = procurementMapper;
        this.readAdapter = readAdapter;
        this.resultParser = resultParser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AliAiBulkInquiryResultView probeResult(AliAiBulkInquiryResultProbeCommand command) {
        AliAiBulkInquiryResultProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryResultProbeCommand()
                : command;
        AliAiBulkInquiryResultView view = buildProbeResult(safeCommand);
        if (Boolean.TRUE.equals(safeCommand.getPersistResult())) {
            persistProbeResult(safeCommand, view);
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

    private void persistProbeResult(AliAiBulkInquiryResultProbeCommand command, AliAiBulkInquiryResultView view) {
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("持久化 1688 智能询盘只读结果时必须提供 taskId。");
        }
        if (!view.isReady()) {
            throw new IllegalStateException("1688 智能询盘结果尚未就绪，不能写回任务。");
        }
        String payload = serializeView(view);
        int updatedRows = procurementMapper.updateAutoInquiryTaskAliAiResult(
                command.getTaskId(),
                view.getExternalInquiryId(),
                view.getResultUrl(),
                view.getExternalResultStatus(),
                payload,
                view.getReplySource(),
                view.getReplyParseStatus(),
                view.getReplyParseError(),
                "已完成 1688 智能询盘结果只读回写。",
                resolveOperatorUserId(command)
        );
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("自动询价任务不存在，无法写回 1688 智能询盘结果。");
        }
        view.setPersistedTaskId(command.getTaskId());
    }

    private String serializeView(AliAiBulkInquiryResultView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 智能询盘结果序列化失败。", exception);
        }
    }

    private Long resolveOperatorUserId(AliAiBulkInquiryResultProbeCommand command) {
        return command.getOperatorUserId() == null ? 0L : command.getOperatorUserId();
    }
}

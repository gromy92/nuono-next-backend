package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAliAiBulkInquiryCreateService {

    private final ProcurementMapper procurementMapper;
    private final AliAiBulkInquiryCreatePlanner createPlanner;
    private final ObjectMapper objectMapper;

    @Value("${nuono.procurement.ali-ai-bulk-inquiry.create-enabled:false}")
    private boolean createEnabled;

    public LocalDbAliAiBulkInquiryCreateService(
            ProcurementMapper procurementMapper,
            AliAiBulkInquiryCreatePlanner createPlanner,
            ObjectMapper objectMapper
    ) {
        this.procurementMapper = procurementMapper;
        this.createPlanner = createPlanner;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AliAiBulkInquiryCreateProbeView probeCreate(AliAiBulkInquiryCreateProbeCommand command) {
        AliAiBulkInquiryCreateProbeCommand hydratedCommand = hydrateFromTask(command);
        AliAiBulkInquiryCreateProbeView view = createPlanner.buildPlan(
                hydratedCommand,
                createEnabled,
                "后端未开启 nuono.procurement.ali-ai-bulk-inquiry.create-enabled，真实创建 1688 智能询盘被阻止。"
        );
        if (Boolean.TRUE.equals(hydratedCommand.getPersistPlan())) {
            persistPlan(hydratedCommand, view);
        }
        return view;
    }

    private AliAiBulkInquiryCreateProbeCommand hydrateFromTask(AliAiBulkInquiryCreateProbeCommand command) {
        AliAiBulkInquiryCreateProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryCreateProbeCommand()
                : command;
        if (safeCommand.getTaskId() == null) {
            return safeCommand;
        }

        AutoInquiryTaskView task = procurementMapper.selectAutoInquiryTask(safeCommand.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("自动询价任务不存在，不能生成 1688 智能询盘创建计划。");
        }

        if (safeCommand.getOfferUrls() == null || safeCommand.getOfferUrls().isEmpty()) {
            List<String> offerUrls = new ArrayList<>();
            if (StringUtils.hasText(task.getTargetEntryUrl())) {
                offerUrls.add(task.getTargetEntryUrl());
            } else if (StringUtils.hasText(task.getTargetOfferId())) {
                offerUrls.add("https://detail.1688.com/offer/" + task.getTargetOfferId() + ".html");
            }
            safeCommand.setOfferUrls(offerUrls);
        }
        if (!StringUtils.hasText(safeCommand.getInquiryMessage())) {
            safeCommand.setInquiryMessage(firstNonBlank(task.getInputPayloadText(), task.getInputPreviewText()));
        }
        return safeCommand;
    }

    private void persistPlan(AliAiBulkInquiryCreateProbeCommand command, AliAiBulkInquiryCreateProbeView view) {
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("持久化 1688 智能询盘创建计划时必须提供 taskId。");
        }
        if (!view.isReady()) {
            throw new IllegalStateException("1688 智能询盘创建计划尚未就绪，不能写回任务。");
        }
        int updatedRows = procurementMapper.updateAutoInquiryTaskAliAiCreatePlan(
                command.getTaskId(),
                serializeView(view),
                "已生成 1688 智能询盘创建计划，尚未真实创建外部询盘。",
                resolveOperatorUserId(command)
        );
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("自动询价任务不存在，无法写回 1688 智能询盘创建计划。");
        }
        view.setPersisted(true);
    }

    private String serializeView(AliAiBulkInquiryCreateProbeView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 智能询盘创建计划序列化失败。", exception);
        }
    }

    private Long resolveOperatorUserId(AliAiBulkInquiryCreateProbeCommand command) {
        return command.getOperatorUserId() == null ? 0L : command.getOperatorUserId();
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

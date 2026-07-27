package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementAutoInquiryProbeScopeMapper;
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

    private final ProcurementAutoInquiryProbeScopeMapper scopeMapper;
    private final AliAiBulkInquiryCreatePlanner createPlanner;
    private final ObjectMapper objectMapper;

    @Value("${nuono.procurement.ali-ai-bulk-inquiry.create-enabled:false}")
    private boolean createEnabled;

    public LocalDbAliAiBulkInquiryCreateService(
            ProcurementAutoInquiryProbeScopeMapper scopeMapper,
            AliAiBulkInquiryCreatePlanner createPlanner,
            ObjectMapper objectMapper
    ) {
        this.scopeMapper = scopeMapper;
        this.createPlanner = createPlanner;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AliAiBulkInquiryCreateProbeView probeCreate(
            Long ownerUserId,
            Long operatorUserId,
            AliAiBulkInquiryCreateProbeCommand command
    ) {
        requireScope(ownerUserId, operatorUserId);
        AliAiBulkInquiryCreateProbeCommand hydratedCommand = hydrateFromTask(ownerUserId, command);
        AliAiBulkInquiryCreateProbeView view = createPlanner.buildPlan(
                hydratedCommand,
                createEnabled,
                "后端未开启 nuono.procurement.ali-ai-bulk-inquiry.create-enabled，真实创建 1688 智能询盘被阻止。"
        );
        if (Boolean.TRUE.equals(hydratedCommand.getPersistPlan())) {
            persistPlan(ownerUserId, operatorUserId, hydratedCommand, view);
        }
        return view;
    }

    private AliAiBulkInquiryCreateProbeCommand hydrateFromTask(
            Long ownerUserId,
            AliAiBulkInquiryCreateProbeCommand command
    ) {
        AliAiBulkInquiryCreateProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryCreateProbeCommand()
                : command;
        if (safeCommand.getTaskId() == null) {
            return safeCommand;
        }

        AutoInquiryTaskView task = scopeMapper.selectOwnedAutoInquiryTask(ownerUserId, safeCommand.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("自动询价任务不存在或无权访问。");
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

    private void persistPlan(
            Long ownerUserId,
            Long operatorUserId,
            AliAiBulkInquiryCreateProbeCommand command,
            AliAiBulkInquiryCreateProbeView view
    ) {
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("持久化 1688 智能询盘创建计划时必须提供 taskId。");
        }
        if (!view.isReady()) {
            throw new IllegalStateException("1688 智能询盘创建计划尚未就绪，不能写回任务。");
        }
        int updatedRows = scopeMapper.updateOwnedAutoInquiryTaskAliAiCreatePlan(
                ownerUserId,
                command.getTaskId(),
                serializeView(view),
                "已生成 1688 智能询盘创建计划，尚未真实创建外部询盘。",
                operatorUserId
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

    private void requireScope(Long ownerUserId, Long operatorUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("缺少有效的采购业务身份。");
        }
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

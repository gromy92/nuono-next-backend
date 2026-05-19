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
public class LocalDbAliUnpaidOrderCreateService {

    private final ProcurementMapper procurementMapper;
    private final AliUnpaidOrderCreatePlanner createPlanner;
    private final ObjectMapper objectMapper;

    @Value("${nuono.procurement.ali-unpaid-order.create-enabled:false}")
    private boolean createEnabled;

    public LocalDbAliUnpaidOrderCreateService(
            ProcurementMapper procurementMapper,
            AliUnpaidOrderCreatePlanner createPlanner,
            ObjectMapper objectMapper
    ) {
        this.procurementMapper = procurementMapper;
        this.createPlanner = createPlanner;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AliUnpaidOrderCreateProbeView probeCreate(AliUnpaidOrderCreateProbeCommand command) {
        AliUnpaidOrderCreateProbeCommand hydratedCommand = hydrateFromTask(command);
        AliUnpaidOrderCreateProbeView view = createPlanner.buildPlan(
                hydratedCommand,
                createEnabled,
                "后端未开启 nuono.procurement.ali-unpaid-order.create-enabled，真实拍下未付款订单被阻止。"
        );
        if (Boolean.TRUE.equals(hydratedCommand.getPersistPlan())) {
            persistPlan(hydratedCommand, view);
        }
        return view;
    }

    private AliUnpaidOrderCreateProbeCommand hydrateFromTask(AliUnpaidOrderCreateProbeCommand command) {
        AliUnpaidOrderCreateProbeCommand safeCommand = command == null
                ? new AliUnpaidOrderCreateProbeCommand()
                : command;
        if (safeCommand.getTaskId() == null) {
            return safeCommand;
        }

        AutoInquiryTaskView task = procurementMapper.selectAutoInquiryTask(safeCommand.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("自动询价任务不存在，不能生成 1688 拍下未付款订单计划。");
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

    private void persistPlan(AliUnpaidOrderCreateProbeCommand command, AliUnpaidOrderCreateProbeView view) {
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("持久化 1688 拍单计划时必须提供 taskId。");
        }
        if (!view.isReady()) {
            throw new IllegalStateException("1688 拍单计划尚未就绪，不能写回任务。");
        }
        int updatedRows = procurementMapper.updateAutoInquiryTaskUnpaidOrderPlan(
                command.getTaskId(),
                serializeView(view),
                "已生成 1688 拍下未付款订单计划，尚未真实创建外部订单。",
                resolveOperatorUserId(command)
        );
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("自动询价任务不存在，无法写回 1688 拍单计划。");
        }
        view.setPersisted(true);
    }

    private String serializeView(AliUnpaidOrderCreateProbeView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 拍单计划序列化失败。", exception);
        }
    }

    private Long resolveOperatorUserId(AliUnpaidOrderCreateProbeCommand command) {
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

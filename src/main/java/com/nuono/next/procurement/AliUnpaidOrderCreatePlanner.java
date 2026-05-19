package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class AliUnpaidOrderCreatePlanner {

    private static final int MAX_OFFER_COUNT = 5;
    private static final Pattern OFFER_URL_PATTERN = Pattern.compile(
            "^https?://(?:[^/]+\\.)?1688\\.com/(?:offer/\\d+\\.html|.*[?&](?:offerId|offer_id|id)=\\d+).*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String ORDER_WORKBENCH_URL = "https://trade.1688.com/order/buyer_order_list.htm";

    private final ObjectMapper objectMapper;

    AliUnpaidOrderCreatePlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AliUnpaidOrderCreateProbeView buildPlan(
            AliUnpaidOrderCreateProbeCommand command,
            boolean createEnabled,
            String fallbackMessage
    ) {
        AliUnpaidOrderCreateProbeCommand safeCommand = command == null
                ? new AliUnpaidOrderCreateProbeCommand()
                : command;
        List<String> offerUrls = normalizeOfferUrls(safeCommand.getOfferUrls());
        String inquiryMessage = normalize(safeCommand.getInquiryMessage());
        boolean dryRun = !Boolean.FALSE.equals(safeCommand.getDryRun());
        boolean confirmCreate = Boolean.TRUE.equals(safeCommand.getConfirmCreate());

        AliUnpaidOrderCreateProbeView view = new AliUnpaidOrderCreateProbeView();
        view.setReady(true);
        view.setDryRun(dryRun);
        view.setCreateEnabled(createEnabled);
        view.setConfirmCreate(confirmCreate);
        view.setTaskId(safeCommand.getTaskId());
        view.setPlannedChannel(ProcurementInquiryChannels.ALI_UNPAID_ORDER_INQUIRY);
        view.setActiveChannel(ProcurementInquiryChannels.ALI_UNPAID_ORDER_INQUIRY);
        view.setQuantity(safeCommand.getQuantity());
        view.setOfferUrls(offerUrls);
        view.setOfferCount(offerUrls.size());
        view.setInquiryMessagePreview(limit(inquiryMessage, 300));
        view.setOrderWorkbenchUrl(ORDER_WORKBENCH_URL);
        view.setUnpaidOrderStatus("PLANNED");
        view.setOrderPayloadDigest(digestPayload(offerUrls, inquiryMessage, safeCommand.getQuantity()));

        String validationFailure = validate(offerUrls, inquiryMessage, safeCommand.getQuantity());
        if (validationFailure != null) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_UNPAID_ORDER_INPUT_INVALID");
            view.setBlockedReason(validationFailure);
            view.setMessage("1688 拍下未付款订单计划未通过输入校验。");
            return view;
        }
        if (dryRun) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_UNPAID_ORDER_DRY_RUN");
            view.setBlockedReason("当前是 dryRun，只生成拍单计划，不触达 1688。");
            view.setMessage("已生成 1688 拍下未付款订单计划。");
            return view;
        }
        if (!createEnabled) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_UNPAID_ORDER_CREATE_DISABLED");
            view.setBlockedReason(firstNonBlank(fallbackMessage, "后端未开启 1688 拍下未付款订单真实创建开关。"));
            view.setMessage("真实拍单被安全开关阻止。");
            return view;
        }
        if (!confirmCreate) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_UNPAID_ORDER_CONFIRM_REQUIRED");
            view.setBlockedReason("请求未显式 confirmCreate=true，不能真实创建 1688 未付款订单。");
            view.setMessage("真实拍单需要二次确认参数。");
            return view;
        }

        view.setCreationAllowed(false);
        view.setFailureCode("ALI_UNPAID_ORDER_ADAPTER_NOT_IMPLEMENTED");
        view.setBlockedReason("真实拍下未付款订单 adapter 尚未实现；当前阶段只允许拍单计划和普通聊天降级。");
        view.setMessage("拍单计划已通过门禁，但真实 adapter 尚未接入。");
        return view;
    }

    private String validate(List<String> offerUrls, String inquiryMessage, Integer quantity) {
        if (offerUrls.isEmpty()) {
            return "至少需要 1 个 1688 offer 链接。";
        }
        if (offerUrls.size() > MAX_OFFER_COUNT) {
            return "1688 拍下未付款订单计划最多允许 5 个 offer 链接。";
        }
        for (String offerUrl : offerUrls) {
            if (!OFFER_URL_PATTERN.matcher(offerUrl).matches()) {
                return "存在非 1688 offer 链接：" + offerUrl;
            }
        }
        if (quantity == null || quantity <= 0) {
            return "采购数量必须大于 0。";
        }
        if (!StringUtils.hasText(inquiryMessage)) {
            return "订单询价话术不能为空。";
        }
        if (inquiryMessage.length() > 500) {
            return "订单询价话术不能超过 500 个字符。";
        }
        return null;
    }

    private List<String> normalizeOfferUrls(List<String> offerUrls) {
        if (offerUrls == null) {
            return new ArrayList<>();
        }
        return offerUrls.stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private String digestPayload(List<String> offerUrls, String inquiryMessage, Integer quantity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerUrls", offerUrls);
        payload.put("inquiryMessage", inquiryMessage);
        payload.put("quantity", quantity);
        payload.put("plannedChannel", ProcurementInquiryChannels.ALI_UNPAID_ORDER_INQUIRY);
        try {
            return sha256(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 拍下未付款订单计划序列化失败。", exception);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte part : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256。", exception);
        }
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

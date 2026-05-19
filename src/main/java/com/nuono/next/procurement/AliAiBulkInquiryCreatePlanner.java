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
class AliAiBulkInquiryCreatePlanner {

    private static final int MAX_OFFER_COUNT = 5;
    private static final Pattern OFFER_URL_PATTERN = Pattern.compile(
            "^https?://(?:[^/]+\\.)?1688\\.com/(?:offer/\\d+\\.html|.*[?&](?:offerId|offer_id|id)=\\d+).*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String RESULT_URL = "https://air.1688.com/kapp/1688-pc-front/ai-avatar/inquiryResult";

    private final ObjectMapper objectMapper;

    AliAiBulkInquiryCreatePlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AliAiBulkInquiryCreateProbeView buildPlan(
            AliAiBulkInquiryCreateProbeCommand command,
            boolean createEnabled,
            String fallbackMessage
    ) {
        AliAiBulkInquiryCreateProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryCreateProbeCommand()
                : command;
        List<String> offerUrls = normalizeOfferUrls(safeCommand.getOfferUrls());
        String inquiryMessage = normalize(safeCommand.getInquiryMessage());
        boolean dryRun = !Boolean.FALSE.equals(safeCommand.getDryRun());
        boolean confirmCreate = Boolean.TRUE.equals(safeCommand.getConfirmCreate());

        AliAiBulkInquiryCreateProbeView view = new AliAiBulkInquiryCreateProbeView();
        view.setReady(true);
        view.setDryRun(dryRun);
        view.setCreateEnabled(createEnabled);
        view.setConfirmCreate(confirmCreate);
        view.setTaskId(safeCommand.getTaskId());
        view.setPlannedChannel(ProcurementInquiryChannels.ALI_AI_BULK_INQUIRY);
        view.setActiveChannel(ProcurementInquiryChannels.ALI_AI_BULK_INQUIRY);
        view.setQuantity(safeCommand.getQuantity());
        view.setOfferUrls(offerUrls);
        view.setOfferCount(offerUrls.size());
        view.setInquiryMessagePreview(limit(inquiryMessage, 300));
        view.setResultUrl(RESULT_URL);
        view.setCreatePayloadDigest(digestPayload(offerUrls, inquiryMessage, safeCommand.getQuantity()));

        String validationFailure = validate(offerUrls, inquiryMessage, safeCommand.getQuantity());
        if (validationFailure != null) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_AI_CREATE_INPUT_INVALID");
            view.setBlockedReason(validationFailure);
            view.setMessage("1688 智能询盘创建计划未通过输入校验。");
            return view;
        }
        if (dryRun) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_AI_CREATE_DRY_RUN");
            view.setBlockedReason("当前是 dryRun，只生成创建计划，不触达 1688。");
            view.setMessage("已生成 1688 智能询盘创建计划。");
            return view;
        }
        if (!createEnabled) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_AI_CREATE_DISABLED");
            view.setBlockedReason(firstNonBlank(fallbackMessage, "后端未开启 1688 智能询盘真实创建开关。"));
            view.setMessage("真实创建被安全开关阻止。");
            return view;
        }
        if (!confirmCreate) {
            view.setCreationAllowed(false);
            view.setFailureCode("ALI_AI_CREATE_CONFIRM_REQUIRED");
            view.setBlockedReason("请求未显式 confirmCreate=true，不能真实创建 1688 智能询盘。");
            view.setMessage("真实创建需要二次确认参数。");
            return view;
        }

        view.setCreationAllowed(false);
        view.setFailureCode("ALI_AI_CREATE_ADAPTER_NOT_IMPLEMENTED");
        view.setBlockedReason("真实创建点击/提交 adapter 尚未实现；当前阶段只允许创建计划和只读结果探针。");
        view.setMessage("创建计划已通过门禁，但真实创建 adapter 尚未接入。");
        return view;
    }

    private String validate(List<String> offerUrls, String inquiryMessage, Integer quantity) {
        if (offerUrls.isEmpty()) {
            return "至少需要 1 个 1688 offer 链接。";
        }
        if (offerUrls.size() > MAX_OFFER_COUNT) {
            return "1688 智能询盘创建探针最多允许 5 个 offer 链接。";
        }
        for (String offerUrl : offerUrls) {
            if (!OFFER_URL_PATTERN.matcher(offerUrl).matches()) {
                return "存在非 1688 offer 链接：" + offerUrl;
            }
        }
        if (!StringUtils.hasText(inquiryMessage)) {
            return "询价话术不能为空。";
        }
        if (inquiryMessage.length() > 500) {
            return "询价话术不能超过 500 个字符。";
        }
        if (quantity != null && quantity <= 0) {
            return "采购数量必须大于 0。";
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
        try {
            return sha256(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("1688 智能询盘创建计划序列化失败。", exception);
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

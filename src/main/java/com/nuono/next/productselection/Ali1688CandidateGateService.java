package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688CandidateGateService {

    private static final int MIN_MATCH_SCORE = 18;
    private static final int MIN_SPEC_SCORE = 10;

    private final ObjectMapper objectMapper;

    public Ali1688CandidateGateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Ali1688CandidateGateView resolve(Ali1688CollectionRecords.CandidateRecord candidate) {
        return resolve(candidate, "list_hint_only", false, null);
    }

    Ali1688CandidateGateView resolve(
            Ali1688CollectionRecords.CandidateRecord candidate,
            String priceState,
            Boolean autoInquiryEligible,
            String priceFailureReason
    ) {
        if (candidate == null) {
            return gate("ai_pending", "待AI评分", "AI 匹配/规格补分未完成。", false, false);
        }
        if ("failed".equals(candidate.aiAssessmentStatus)) {
            return gate("ai_failed", "AI评分失败", "AI 补分失败，需人工复核。", false, false);
        }
        if (!"final".equals(candidate.scoreStatus) || candidate.matchScore == null || candidate.specScore == null) {
            return gate("ai_pending", "待AI评分", "AI 匹配/规格补分未完成。", false, false);
        }

        String riskLevel = readRiskLevel(candidate.scoreDetailJson);
        if ("high".equals(riskLevel) || candidate.matchScore < MIN_MATCH_SCORE) {
            return gate(
                    "mismatch_rejected",
                    "匹配不通过",
                    "AI 匹配分 " + candidate.matchScore + ("high".equals(riskLevel) ? " 且风险高。" : " 未达到门禁。"),
                    false,
                    false
            );
        }
        if ("medium".equals(riskLevel) || candidate.specScore < MIN_SPEC_SCORE) {
            return gate(
                    "spec_uncertain",
                    "规格待确认",
                    "规格分 " + candidate.specScore + " 偏低，需人工确认型号/套装。",
                    false,
                    false
            );
        }

        if ("price_probe_failed".equals(priceState)) {
            return gate("price_probe_failed", "取价失败", defaultText(priceFailureReason, "真实价格探针失败。"), false, false);
        }
        if ("price_confirmed".equals(priceState)) {
            if (Boolean.TRUE.equals(autoInquiryEligible)) {
                return gate("inquiry_eligible", "可询盘", "AI 与真实价格门禁通过。", false, true);
            }
            return gate("price_confirmed", "价格已确认", "真实价格已确认，等待询盘资格门禁。", false, false);
        }
        return gate("price_probe_pending", "待真实价格", "AI 门禁通过，等待订单预览价确认。", true, false);
    }

    private Ali1688CandidateGateView gate(
            String state,
            String label,
            String reason,
            boolean allowsPriceProbe,
            boolean allowsAutoInquiry
    ) {
        Ali1688CandidateGateView view = new Ali1688CandidateGateView();
        view.state = state;
        view.label = label;
        view.reason = reason;
        view.allowsPriceProbe = allowsPriceProbe;
        view.allowsAutoInquiry = allowsAutoInquiry;
        return view;
    }

    private String readRiskLevel(String scoreDetailJson) {
        if (!StringUtils.hasText(scoreDetailJson)) {
            return "unknown";
        }
        try {
            JsonNode root = objectMapper.readTree(scoreDetailJson);
            JsonNode riskLevel = root.get("riskLevel");
            return riskLevel == null || riskLevel.isNull() ? "unknown" : riskLevel.asText("unknown");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}

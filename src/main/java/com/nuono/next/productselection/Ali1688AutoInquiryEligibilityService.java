package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688AutoInquiryEligibilityService {

    private static final int MIN_MATCH_SCORE = 18;
    private static final int MIN_SPEC_SCORE = 10;

    private final ObjectMapper objectMapper;

    public Ali1688AutoInquiryEligibilityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Ali1688InquiryEligibilityView resolve(
            Ali1688CollectionRecords.CandidateRecord candidate,
            Ali1688RealPriceSnapshot priceSnapshot
    ) {
        if (candidate == null) {
            return rejected("rejected_missing_ai", "待AI", "AI 匹配/规格补分未完成。", null);
        }
        if ("failed".equals(candidate.aiAssessmentStatus)) {
            return rejected("rejected_ai_failed", "AI失败", "AI 补分失败，需人工复核。", null);
        }
        if (!"final".equals(candidate.scoreStatus) || candidate.matchScore == null || candidate.specScore == null) {
            return rejected("rejected_missing_ai", "待AI", "AI 匹配/规格补分未完成。", null);
        }

        String riskLevel = readRiskLevel(candidate.scoreDetailJson);
        if ("high".equals(riskLevel)) {
            return rejected("rejected_high_risk", "匹配风险高", "AI 判断匹配风险高。", null);
        }
        if (candidate.matchScore < MIN_MATCH_SCORE) {
            return rejected(
                    "rejected_high_risk",
                    "匹配不通过",
                    "AI 匹配分 " + candidate.matchScore + " 未达到门禁。",
                    null
            );
        }
        if ("medium".equals(riskLevel) || candidate.specScore < MIN_SPEC_SCORE) {
            return rejected(
                    "rejected_spec_uncertain",
                    "规格待确认",
                    "规格分 " + candidate.specScore + " 偏低，需人工确认型号/套装。",
                    null
            );
        }

        if (priceSnapshot == null) {
            return rejected("rejected_missing_real_price", "待真实价格", "缺少已确认真实采购价。", null);
        }
        if ("failed".equals(priceSnapshot.status)) {
            String failureCode = defaultText(priceSnapshot.failureCode, "preview_failed");
            return rejected(
                    "rejected_price_failed",
                    "取价失败",
                    "真实价格探针失败：" + failureCode,
                    failureCode
            );
        }
        if (!"confirmed".equals(priceSnapshot.status)) {
            return rejected("rejected_missing_real_price", "待真实价格", "缺少已确认真实采购价。", null);
        }
        return eligible();
    }

    private Ali1688InquiryEligibilityView eligible() {
        Ali1688InquiryEligibilityView view = new Ali1688InquiryEligibilityView();
        view.state = "eligible";
        view.label = "可询盘";
        view.reason = "AI、规格、风险和真实价格门禁已通过。";
        view.eligible = true;
        return view;
    }

    private Ali1688InquiryEligibilityView rejected(String state, String label, String reason, String priceFailureCode) {
        Ali1688InquiryEligibilityView view = new Ali1688InquiryEligibilityView();
        view.state = state;
        view.label = label;
        view.reason = reason;
        view.eligible = false;
        view.priceFailureCode = priceFailureCode;
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

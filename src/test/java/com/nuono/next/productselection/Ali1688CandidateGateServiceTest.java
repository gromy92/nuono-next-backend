package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688CandidateGateServiceTest {

    private final Ali1688CandidateGateService service = new Ali1688CandidateGateService(new ObjectMapper());

    @Test
    void resolveReturnsStableGateStateAndActionFlags() {
        List<GateCase> cases = List.of(
                new GateCase(
                        "AI pending",
                        candidate("pending", "partial", null, null, null),
                        "list_hint_only",
                        null,
                        null,
                        "ai_pending",
                        false,
                        false,
                        "待AI评分"
                ),
                new GateCase(
                        "AI failed",
                        candidate("failed", "partial", null, null, null),
                        "list_hint_only",
                        null,
                        null,
                        "ai_failed",
                        false,
                        false,
                        "AI评分失败"
                ),
                new GateCase(
                        "high risk mismatch",
                        candidate("success", "final", 28, 16, "high"),
                        "list_hint_only",
                        null,
                        null,
                        "mismatch_rejected",
                        false,
                        false,
                        "匹配不通过"
                ),
                new GateCase(
                        "low match mismatch",
                        candidate("success", "final", 17, 18, "low"),
                        "list_hint_only",
                        null,
                        null,
                        "mismatch_rejected",
                        false,
                        false,
                        "匹配不通过"
                ),
                new GateCase(
                        "uncertain spec",
                        candidate("success", "final", 27, 9, "low"),
                        "list_hint_only",
                        null,
                        null,
                        "spec_uncertain",
                        false,
                        false,
                        "规格待确认"
                ),
                new GateCase(
                        "missing real price",
                        candidate("success", "final", 31, 14, "low"),
                        "list_hint_only",
                        null,
                        null,
                        "price_probe_pending",
                        true,
                        false,
                        "待真实价格"
                ),
                new GateCase(
                        "price probe failed",
                        candidate("success", "final", 31, 14, "low"),
                        "price_probe_failed",
                        null,
                        "shipping_unavailable",
                        "price_probe_failed",
                        false,
                        false,
                        "取价失败"
                ),
                new GateCase(
                        "price confirmed but not eligible",
                        candidate("success", "final", 31, 14, "low"),
                        "price_confirmed",
                        false,
                        null,
                        "price_confirmed",
                        false,
                        false,
                        "价格已确认"
                ),
                new GateCase(
                        "inquiry eligible",
                        candidate("success", "final", 31, 14, "low"),
                        "price_confirmed",
                        true,
                        null,
                        "inquiry_eligible",
                        false,
                        true,
                        "可询盘"
                )
        );

        for (GateCase testCase : cases) {
            Ali1688CandidateGateView gate = service.resolve(
                    testCase.candidate,
                    testCase.priceState,
                    testCase.autoInquiryEligible,
                    testCase.priceFailureReason
            );

            assertEquals(testCase.expectedState, gate.state, testCase.name);
            assertEquals(testCase.expectedAllowsPriceProbe, gate.allowsPriceProbe, testCase.name);
            assertEquals(testCase.expectedAllowsAutoInquiry, gate.allowsAutoInquiry, testCase.name);
            assertEquals(testCase.expectedLabel, gate.label, testCase.name);
        }
    }

    @Test
    void resolveDefaultListHintCandidateAsPendingAiUntilFinalAiScoreExists() {
        Ali1688CollectionRecords.CandidateRecord candidate = candidate(null, "partial", null, null, null);

        Ali1688CandidateGateView gate = service.resolve(candidate);

        assertEquals("ai_pending", gate.state);
        assertEquals("待AI评分", gate.label);
        assertEquals(false, gate.allowsPriceProbe);
        assertEquals(false, gate.allowsAutoInquiry);
    }

    private Ali1688CollectionRecords.CandidateRecord candidate(
            String aiAssessmentStatus,
            String scoreStatus,
            Integer matchScore,
            Integer specScore,
            String riskLevel
    ) {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88201L;
        candidate.title = "Razr Fold 手机候选";
        candidate.aiAssessmentStatus = aiAssessmentStatus;
        candidate.scoreStatus = scoreStatus;
        candidate.matchScore = matchScore;
        candidate.specScore = specScore;
        candidate.scoreDetailJson = riskLevel == null ? "{}" : "{\"riskLevel\":\"" + riskLevel + "\"}";
        return candidate;
    }

    private static class GateCase {
        private final String name;
        private final Ali1688CollectionRecords.CandidateRecord candidate;
        private final String priceState;
        private final Boolean autoInquiryEligible;
        private final String priceFailureReason;
        private final String expectedState;
        private final boolean expectedAllowsPriceProbe;
        private final boolean expectedAllowsAutoInquiry;
        private final String expectedLabel;

        private GateCase(
                String name,
                Ali1688CollectionRecords.CandidateRecord candidate,
                String priceState,
                Boolean autoInquiryEligible,
                String priceFailureReason,
                String expectedState,
                boolean expectedAllowsPriceProbe,
                boolean expectedAllowsAutoInquiry,
                String expectedLabel
        ) {
            this.name = name;
            this.candidate = candidate;
            this.priceState = priceState;
            this.autoInquiryEligible = autoInquiryEligible;
            this.priceFailureReason = priceFailureReason;
            this.expectedState = expectedState;
            this.expectedAllowsPriceProbe = expectedAllowsPriceProbe;
            this.expectedAllowsAutoInquiry = expectedAllowsAutoInquiry;
            this.expectedLabel = expectedLabel;
        }
    }
}

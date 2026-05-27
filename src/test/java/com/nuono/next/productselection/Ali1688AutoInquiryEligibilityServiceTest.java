package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688AutoInquiryEligibilityServiceTest {

    private final Ali1688AutoInquiryEligibilityService service =
            new Ali1688AutoInquiryEligibilityService(new ObjectMapper());

    @Test
    void rejectsEveryNonEligibleCandidateWithStableReasonCode() {
        List<Case> cases = List.of(
                new Case("missing ai score", pendingAiCandidate(), null, "rejected_missing_ai", "AI 匹配/规格补分未完成。"),
                new Case("ai failed", aiFailedCandidate(), null, "rejected_ai_failed", "AI 补分失败，需人工复核。"),
                new Case("high risk mismatch", highRiskCandidate(), confirmedPrice(), "rejected_high_risk", "AI 判断匹配风险高。"),
                new Case("low match mismatch", lowMatchCandidate(), confirmedPrice(), "rejected_high_risk", "AI 匹配分 12 未达到门禁。"),
                new Case("spec uncertain", specUncertainCandidate(), confirmedPrice(), "rejected_spec_uncertain", "规格分 8 偏低，需人工确认型号/套装。"),
                new Case("missing real price", gatePassedCandidate(), null, "rejected_missing_real_price", "缺少已确认真实采购价。"),
                new Case("price probe failed", gatePassedCandidate(), failedPrice("captcha_required"), "rejected_price_failed", "真实价格探针失败：captcha_required")
        );

        for (Case testCase : cases) {
            Ali1688InquiryEligibilityView eligibility = service.resolve(testCase.candidate, testCase.priceSnapshot);

            assertFalse(Boolean.TRUE.equals(eligibility.eligible), testCase.name);
            assertEquals(testCase.expectedState, eligibility.state, testCase.name);
            assertEquals(testCase.expectedReason, eligibility.reason, testCase.name);
        }
    }

    @Test
    void exposesEligiblePoolOnlyAfterAiSpecRiskAndRealPricePass() {
        Ali1688InquiryEligibilityView eligibility = service.resolve(gatePassedCandidate(), confirmedPrice());

        assertTrue(Boolean.TRUE.equals(eligibility.eligible));
        assertEquals("eligible", eligibility.state);
        assertEquals("可询盘", eligibility.label);
        assertEquals("AI、规格、风险和真实价格门禁已通过。", eligibility.reason);
    }

    private Ali1688CollectionRecords.CandidateRecord gatePassedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = 87001L;
        candidate.scoreStatus = "final";
        candidate.aiAssessmentStatus = "success";
        candidate.matchScore = 31;
        candidate.specScore = 14;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord pendingAiCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        candidate.scoreStatus = "partial";
        candidate.matchScore = null;
        candidate.specScore = null;
        candidate.aiAssessmentStatus = "pending";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord aiFailedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = pendingAiCandidate();
        candidate.aiAssessmentStatus = "failed";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord highRiskCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        candidate.scoreDetailJson = "{\"riskLevel\":\"high\"}";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord lowMatchCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        candidate.matchScore = 12;
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord specUncertainCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        candidate.specScore = 8;
        return candidate;
    }

    private Ali1688RealPriceSnapshot confirmedPrice() {
        Ali1688RealPriceSnapshot snapshot = new Ali1688RealPriceSnapshot();
        snapshot.status = "confirmed";
        snapshot.totalPrice = new BigDecimal("38.46");
        snapshot.currency = "CNY";
        return snapshot;
    }

    private Ali1688RealPriceSnapshot failedPrice(String failureCode) {
        Ali1688RealPriceSnapshot snapshot = new Ali1688RealPriceSnapshot();
        snapshot.status = "failed";
        snapshot.failureCode = failureCode;
        snapshot.failureMessage = "1688 要求验证码";
        return snapshot;
    }

    private static class Case {
        private final String name;
        private final Ali1688CollectionRecords.CandidateRecord candidate;
        private final Ali1688RealPriceSnapshot priceSnapshot;
        private final String expectedState;
        private final String expectedReason;

        private Case(
                String name,
                Ali1688CollectionRecords.CandidateRecord candidate,
                Ali1688RealPriceSnapshot priceSnapshot,
                String expectedState,
                String expectedReason
        ) {
            this.name = name;
            this.candidate = candidate;
            this.priceSnapshot = priceSnapshot;
            this.expectedState = expectedState;
            this.expectedReason = expectedReason;
        }
    }
}

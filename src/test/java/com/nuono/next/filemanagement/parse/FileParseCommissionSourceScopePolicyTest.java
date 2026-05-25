package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FileParseCommissionSourceScopePolicyTest {

    private final FileParseCommissionSourceScopePolicy policy = new FileParseCommissionSourceScopePolicy();

    @Test
    void shouldClipReferralFeesBeforeFaqAndFollowingUnnumberedSections() {
        List<String> lines = List.of(
                "Noon seller fees",
                "1. Referral Fees",
                "Fashion > Apparel | All | 12%",
                "Colour Cosmetics | All | 15% for Generic brand, 10% for all other brands",
                "FAQ",
                "Value Added Services",
                "FBN Outbound Fees"
        );

        List<String> scoped = policy.referralFeeSectionRows(lines, value -> value);

        assertEquals(List.of(
                "1. Referral Fees",
                "Fashion > Apparel | All | 12%",
                "Colour Cosmetics | All | 15% for Generic brand, 10% for all other brands"
        ), scoped);
    }

    @Test
    void shouldReturnEmptyWhenReferralFeesSectionIsMissing() {
        List<String> scoped = policy.referralFeeSectionRows(
                List.of("FBN Outbound Fees", "Monthly Storage Fees"),
                value -> value
        );

        assertTrue(scoped.isEmpty());
    }
}

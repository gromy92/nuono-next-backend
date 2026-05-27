package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonProviderEnablementAndSmokeGateTest {

    @Test
    void realProviderShouldRemainDisabledByDefault() {
        NoonProviderEnablementPolicy policy = NoonProviderEnablementPolicy.disabledByDefault();

        assertFalse(policy.isEnabled());
        assertEquals("provider_not_configured", policy.disabledFailureType());
    }

    @Test
    void targetEnvironmentGateShouldRequireAllDomainSmokeAndRollbackStrategy() {
        NoonPullSmokeGate gate = new NoonPullSmokeGate();
        gate.record(NoonPullSmokeEvidence.builder()
                .targetEnvironment("test")
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .targetIdentity("catalog:list")
                .rowOrItemCount(20)
                .taskId(130001L)
                .sourceBatchId("batch-product")
                .elapsed(Duration.ofSeconds(12))
                .qualityState("ready")
                .build());
        gate.record(NoonPullSmokeEvidence.builder()
                .targetEnvironment("test")
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .rowOrItemCount(7)
                .taskId(130002L)
                .sourceBatchId("batch-sales")
                .elapsed(Duration.ofSeconds(18))
                .latestFactDate(LocalDate.of(2026, 5, 21))
                .qualityState("ready")
                .build());

        NoonPullSmokeGateResult blocked = gate.evaluate("test");

        assertFalse(blocked.isReleaseAllowed());
        assertTrue(blocked.getMissingRequirements().containsAll(List.of("ORDER_SMOKE", "ROLLBACK_OR_GLOBAL_PAUSE")));

        gate.record(NoonPullSmokeEvidence.builder()
                .targetEnvironment("test")
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.ORDER)
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .rowOrItemCount(1)
                .taskId(130003L)
                .sourceBatchId("batch-order")
                .elapsed(Duration.ofSeconds(20))
                .qualityState("provider_not_configured")
                .failureClassification("provider_not_configured")
                .build());
        gate.confirmRollbackOrGlobalPause("test", "global pause can stop all Noon pull plans before production scheduling");

        NoonPullSmokeGateResult ready = gate.evaluate("test");

        assertTrue(ready.isReleaseAllowed());
        assertEquals(0, ready.getMissingRequirements().size());
    }
}

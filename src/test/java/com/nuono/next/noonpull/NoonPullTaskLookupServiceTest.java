package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoonPullTaskLookupServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void latestTaskMatchesScopeDomainTypeAndTargetPrefix() {
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService = new NoonPullFoundationService(repository, FIXED_CLOCK);
        NoonPullTaskLookupService lookupService = new NoonPullTaskLookupService(foundationService);

        NoonPullTaskRecord olderProductList = createTask(
                foundationService,
                307L,
                "STR108065-NAE",
                "AE",
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                "product-list:2026-05-23"
        );
        foundationService.markRunning(olderProductList.getId(), "worker-1");
        foundationService.markSucceeded(olderProductList.getId(), "batch-1", "ok");

        createTask(
                foundationService,
                307L,
                "STR108065-NAE",
                "AE",
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                "product-detail:SKU-1"
        );
        createTask(
                foundationService,
                307L,
                "OTHER-STORE",
                "AE",
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                "product-list:2026-05-24"
        );
        NoonPullTaskRecord latestProductList = createTask(
                foundationService,
                307L,
                "STR108065-NAE",
                "AE",
                NoonPullType.INTERFACE,
                NoonPullDataDomain.PRODUCT,
                "product-list:2026-05-24"
        );

        Optional<NoonPullTaskRecord> result = lookupService.latestTask(NoonPullTaskLookupQuery.builder()
                .ownerUserId(307L)
                .storeCode("str108065-nae")
                .siteCode("ae")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .targetIdentityPrefix("product-list:")
                .build());

        assertTrue(result.isPresent());
        assertEquals(latestProductList.getId(), result.get().getId());
    }

    @Test
    void latestTaskReturnsEmptyWhenScopeDoesNotMatch() {
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService = new NoonPullFoundationService(repository, FIXED_CLOCK);
        NoonPullTaskLookupService lookupService = new NoonPullTaskLookupService(foundationService);
        createTask(
                foundationService,
                307L,
                "STR108065-NAE",
                "AE",
                NoonPullType.REPORT,
                NoonPullDataDomain.SALES,
                "sales-product-views:2026-05-24"
        );

        Optional<NoonPullTaskRecord> result = lookupService.latestTask(NoonPullTaskLookupQuery.builder()
                .ownerUserId(308L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .build());

        assertTrue(result.isEmpty());
    }

    private static NoonPullTaskRecord createTask(
            NoonPullFoundationService foundationService,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            NoonPullType pullType,
            NoonPullDataDomain dataDomain,
            String targetIdentity
    ) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .pullType(pullType)
                .dataDomain(dataDomain)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .scheduleExpression("test")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .pullType(pullType)
                .dataDomain(dataDomain)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .targetIdentity(targetIdentity)
                .targetDateFrom(LocalDate.parse("2026-05-24"))
                .targetDateTo(LocalDate.parse("2026-05-24"))
                .build()).orElseThrow();
    }
}

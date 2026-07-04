package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonInterfacePullerTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonInterfacePuller puller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T05:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        puller = new NoonInterfacePuller(foundationService);
    }

    @Test
    void shouldExecutePagedInterfacePullTaskAndRecordSourceBatch() {
        NoonPullTaskRecord task = createProductTask();
        NoonInterfacePullRequest request = productListRequest()
                .requestSummary("POST /offer/list/noon cookie=secret; Authorization: Bearer token-abc")
                .build();
        FakeProvider provider = new FakeProvider(List.of(
                NoonInterfacePullPage.builder()
                        .items(List.of(Map.of("sku_parent", "ZP-1"), Map.of("sku_parent", "ZP-2")))
                        .pageNumber(1)
                        .totalItems(3)
                        .hasNextPage(true)
                        .requestCount(1)
                        .build(),
                NoonInterfacePullPage.builder()
                        .items(List.of(Map.of("sku_parent", "ZP-3")))
                        .pageNumber(2)
                        .totalItems(3)
                        .hasNextPage(false)
                        .requestCount(1)
                        .build()
        ));

        NoonInterfacePullResult result = puller.execute(task.getId(), request, provider);
        NoonPullTaskRecord persistedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, persistedTask.getStatus());
        assertEquals(3, result.getItems().size());
        assertEquals(2, result.getPageCount());
        assertEquals(2, result.getRequestCount());
        assertNotNull(persistedTask.getSourceBatchId());
        assertEquals(persistedTask.getSourceBatchId(), result.getSourceBatchId());
        assertTrue(persistedTask.getDiagnosticSummary().contains("product-list"));
        assertTrue(persistedTask.getDiagnosticSummary().contains("pages=2"));
        assertFalse(persistedTask.getDiagnosticSummary().contains("secret"));
        assertFalse(persistedTask.getDiagnosticSummary().contains("token-abc"));
    }

    @Test
    void shouldClassifyAuthRequiredWithoutRetrying() {
        NoonPullTaskRecord task = createProductTask();
        FakeProvider provider = FakeProvider.failing("401 auth required Authorization: Bearer token-abc");

        NoonInterfacePullResult result = puller.execute(task.getId(), productListRequest().build(), provider);
        NoonPullTaskRecord persistedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("auth_required", persistedTask.getFailureType());
        assertEquals("MANUAL_ACTION", persistedTask.getRetryAction());
        assertEquals(Boolean.FALSE, persistedTask.getRetryable());
        assertEquals(Boolean.TRUE, persistedTask.getRequiresManualAction());
        assertFalse(persistedTask.getDiagnosticSummary().contains("token-abc"));
    }

    @Test
    void shouldClassifyTimeoutAndProviderUnavailableForBoundedRetry() {
        NoonPullTaskRecord timeoutTask = createProductTask();
        NoonInterfacePullResult timeout = puller.execute(
                timeoutTask.getId(),
                productListRequest().build(),
                FakeProvider.failing("socket timeout")
        );

        NoonPullTaskRecord timeoutRecord = repository.selectTask(timeoutTask.getId());
        assertEquals(NoonPullTaskStatus.FAILED, timeout.getStatus());
        assertEquals("timeout", timeoutRecord.getFailureType());
        assertEquals("RETRY", timeoutRecord.getRetryAction());
        assertEquals(Boolean.TRUE, timeoutRecord.getRetryable());
        assertNotNull(repository.selectPlan(timeoutTask.getPlanId()).getNextRetryAt());

        NoonPullTaskRecord unavailableTask = createProductTask("catalog:list:unavailable");
        NoonInterfacePullResult unavailable = puller.execute(
                unavailableTask.getId(),
                productListRequest().build(),
                FakeProvider.failing("provider unavailable http 503")
        );

        NoonPullTaskRecord unavailableRecord = repository.selectTask(unavailableTask.getId());
        assertEquals(NoonPullTaskStatus.FAILED, unavailable.getStatus());
        assertEquals("provider_unavailable", unavailableRecord.getFailureType());
        assertEquals("RETRY", unavailableRecord.getRetryAction());
        assertEquals(Boolean.TRUE, unavailableRecord.getRetryable());
    }

    @Test
    void shouldRecordProductRiskBackoffAndDelaySameProductScopeWithoutCallingProvider() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T05:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository riskRepository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard riskBackoffGuard = new NoonRiskBackoffGuard(riskRepository, clock);
        puller = new NoonInterfacePuller(foundationService, riskBackoffGuard, new NoonPullFailurePolicy(clock));
        NoonPullTaskRecord riskTask = createProductTask("catalog:list:risk");

        NoonInterfacePullResult riskResult = puller.execute(
                riskTask.getId(),
                productListRequest().targetIdentity("catalog:list:risk").build(),
                FakeProvider.failing("429 too many requests")
        );

        NoonPullTaskRecord delayedRiskTask = repository.selectTask(riskTask.getId());
        assertEquals(NoonPullTaskStatus.FAILED, riskResult.getStatus());
        assertEquals("rate_limited", delayedRiskTask.getFailureType());
        assertEquals("DELAY", delayedRiskTask.getRetryAction());
        assertEquals(Boolean.TRUE, delayedRiskTask.getRetryable());
        assertEquals(Boolean.FALSE, delayedRiskTask.getRequiresManualAction());
        assertEquals("risk_backoff", delayedRiskTask.getReadinessState());
        assertEquals("rate_limited", riskRepository.selectLatestHold(
                NoonRiskBackoffScope.productInterface(307L, "STR245027", "AE").getScopeKey()
        ).getRiskType());
        assertEquals(1, riskRepository.selectLatestHold(
                NoonRiskBackoffScope.productInterface(307L, "STR245027", "AE").getScopeKey()
        ).getAttemptCount());

        NoonPullTaskRecord heldTask = createProductTask("catalog:list:held");
        FakeProvider heldProvider = new FakeProvider(List.of(NoonInterfacePullPage.builder()
                .items(List.of(Map.of("sku_parent", "ZP-HELD")))
                .pageNumber(1)
                .totalItems(1)
                .hasNextPage(false)
                .requestCount(1)
                .build()));

        NoonInterfacePullResult heldResult = puller.execute(
                heldTask.getId(),
                productListRequest().targetIdentity("catalog:list:held").build(),
                heldProvider
        );

        NoonPullTaskRecord delayedHeldTask = repository.selectTask(heldTask.getId());
        assertEquals(NoonPullTaskStatus.FAILED, heldResult.getStatus());
        assertEquals("rate_limited", delayedHeldTask.getFailureType());
        assertEquals("risk_backoff", delayedHeldTask.getReadinessState());
        assertEquals(0, heldProvider.callCount);
    }

    @Test
    void shouldApplyProductRiskHoldToSalesPageQueryInterfacePull() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T05:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository riskRepository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard riskBackoffGuard = new NoonRiskBackoffGuard(riskRepository, clock);
        riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.productInterface(307L, "STR245027", "AE"),
                "rate_limited",
                "PRODUCT",
                130001L,
                null,
                "product risk"
        );
        puller = new NoonInterfacePuller(foundationService, riskBackoffGuard, new NoonPullFailurePolicy(clock));
        NoonPullTaskRecord task = createSalesPageQueryTask();
        FakeProvider provider = new FakeProvider(List.of(NoonInterfacePullPage.builder()
                .items(List.of(Map.of("sku", "SKU-1")))
                .pageNumber(1)
                .totalItems(1)
                .hasNextPage(false)
                .requestCount(1)
                .build()));

        NoonInterfacePullResult result = puller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(307L)
                        .storeCode("STR245027")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.SALES)
                        .requestName("sales-page-query")
                        .targetIdentity("sales-page-query:2026-05-21..2026-05-21")
                        .timeoutSeconds(30)
                        .build(),
                provider
        );

        NoonPullTaskRecord delayedTask = repository.selectTask(task.getId());
        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("rate_limited", delayedTask.getFailureType());
        assertEquals("risk_backoff", delayedTask.getReadinessState());
        assertEquals(0, provider.callCount);
    }

    private NoonPullTaskRecord createProductTask() {
        return createProductTask("catalog:list");
    }

    private NoonPullTaskRecord createProductTask(String targetIdentity) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .scheduleExpression("manual")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity(targetIdentity)
                .build()).orElseThrow();
    }

    private NoonPullTaskRecord createSalesPageQueryTask() {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.PAGE_QUERY)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity("sales-page-query:2026-05-21..2026-05-21")
                .build()).orElseThrow();
    }

    private NoonInterfacePullRequest.Builder productListRequest() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("product-list")
                .targetIdentity("catalog:list")
                .timeoutSeconds(30);
    }

    private static final class FakeProvider implements NoonInterfacePullProvider {
        private final List<NoonInterfacePullPage> pages;
        private final RuntimeException failure;
        private int callCount;

        private FakeProvider(List<NoonInterfacePullPage> pages, RuntimeException failure) {
            this.pages = pages;
            this.failure = failure;
        }

        private FakeProvider(List<NoonInterfacePullPage> pages) {
            this(pages, null);
        }

        private static FakeProvider failing(String message) {
            return new FakeProvider(List.of(), new NoonInterfacePullException(message));
        }

        @Override
        public NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber) {
            callCount++;
            if (failure != null) {
                throw failure;
            }
            return pages.get(pageNumber - 1);
        }
    }
}

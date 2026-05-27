package com.nuono.next.aiopsdashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiOperationsDashboardServiceTest {

    @Test
    void springContextCanInstantiateServiceWithProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(
                        AiOperationsDashboardSignalProviderRegistry.class,
                        () -> new AiOperationsDashboardSignalProviderRegistry(List.of())
                )
                .withUserConfiguration(AiOperationsDashboardService.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(AiOperationsDashboardService.class));
                });
    }

    @Test
    void overviewWithoutProvidersReturnsStableReadModelWithoutFakeBusinessValues() {
        AiOperationsDashboardService service = new AiOperationsDashboardService(
                new AiOperationsDashboardSignalProviderRegistry(List.of()),
                () -> LocalDate.of(2026, 5, 21)
        );

        AiOperationsDashboardOverview overview = service.overview(new AiOperationsDashboardQuery(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "last7Days"
        ));

        assertEquals(10002L, overview.getScope().getOwnerUserId());
        assertEquals(555L, overview.getScope().getOperatorUserId());
        assertEquals("STR245027-NAE", overview.getScope().getStoreCode());
        assertEquals("AE", overview.getScope().getSiteCode());
        assertEquals("last7Days", overview.getScope().getDatePreset());
        assertEquals(LocalDate.of(2026, 5, 15), overview.getScope().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), overview.getScope().getDateTo());

        assertEquals("empty", overview.getSummary().getState());
        assertFalse(overview.getMetricCards().isEmpty());
        overview.getMetricCards().forEach(card -> {
            assertNull(card.getValue(), "A01 must not fabricate numeric business values");
            assertEquals("not_connected", card.getState());
        });
        assertTrue(overview.getSignals().isEmpty());
        assertEquals("runtime_disabled", overview.getAiSummary().getState());
        assertTrue(overview.getSuggestions().getItems().isEmpty());
        assertTrue(overview.getEvidence().getItems().isEmpty());

        assertTrue(overview.getQualityStates().contains("not_connected"));
        assertTrue(overview.getQualityStates().contains("ready"));
        assertTrue(overview.getQualityStates().contains("sync_in_progress"));
        assertTrue(overview.getQualityStates().contains("empty"));
        assertTrue(overview.getQualityStates().contains("stale"));
        assertTrue(overview.getQualityStates().contains("backfill_required"));
        assertTrue(overview.getQualityStates().contains("backfill_running"));
        assertTrue(overview.getQualityStates().contains("backfill_failed"));
        assertTrue(overview.getQualityStates().contains("empty_report"));
        assertTrue(overview.getQualityStates().contains("missing_mapping"));
        assertTrue(overview.getQualityStates().contains("workspace_empty"));
        assertTrue(overview.getQualityStates().contains("provider_unavailable"));
        assertTrue(overview.getQualityStates().contains("runtime_disabled"));
        assertTrue(overview.getQualityStates().contains("partial_success"));
    }

    @Test
    void overviewIncludesSignalsAndEvidenceFromRegisteredProviders() {
        AiOperationsDashboardOverview.EvidenceItem evidence =
                new AiOperationsDashboardOverview.EvidenceItem(
                        "sales-sync",
                        "销量同步状态",
                        "noon_sync",
                        "partial_success",
                        "销量同步已接入但仍有订正窗口。"
                );
        AiOperationsDashboardSignalProvider provider = query -> List.of(
                new AiOperationsDashboardOverview.Signal(
                        "sales_lifecycle",
                        "销量生命周期",
                        "partial_success",
                        "info",
                        "销量数据已进入看板证据层。",
                        "sales",
                        List.of(evidence)
                )
        );
        AiOperationsDashboardService service = new AiOperationsDashboardService(
                new AiOperationsDashboardSignalProviderRegistry(List.of(provider)),
                () -> LocalDate.of(2026, 5, 21)
        );

        AiOperationsDashboardOverview overview = service.overview(new AiOperationsDashboardQuery(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "today"
        ));

        assertEquals("partial_success", overview.getSummary().getState());
        assertEquals(1, overview.getSignals().size());
        assertEquals("sales_lifecycle", overview.getSignals().get(0).getKey());
        assertEquals("partial_success", overview.getEvidence().getState());
        assertEquals(1, overview.getEvidence().getItems().size());
        assertEquals("sales-sync", overview.getEvidence().getItems().get(0).getId());
    }

    @Test
    void overviewIncludesProviderMetricCardsAndStandaloneEvidenceWithoutChangingRouteContract() {
        AiOperationsDashboardOverview.EvidenceItem evidence =
                new AiOperationsDashboardOverview.EvidenceItem(
                        "sales-freshness-scope",
                        "销量 Freshness 范围",
                        "sales",
                        "partial_success",
                        "owner=10002;store=STR245027-NAE;site=AE;date=2026-05-19"
                );
        AiOperationsDashboardSignalProvider provider = new AiOperationsDashboardSignalProvider() {
            @Override
            public List<AiOperationsDashboardOverview.Signal> collect(AiOperationsDashboardQuery query) {
                return List.of();
            }

            @Override
            public AiOperationsDashboardContribution contribute(
                    AiOperationsDashboardQuery query,
                    AiOperationsDashboardScope scope
            ) {
                return new AiOperationsDashboardContribution(
                        List.of(new AiOperationsDashboardOverview.MetricCard(
                                "sales_freshness",
                                "销量数据新鲜度",
                                "partial_success",
                                "partial_success",
                                null,
                                null,
                                "最新可用销量日期 2026-05-19"
                        )),
                        List.of(),
                        List.of(evidence)
                );
            }
        };
        AiOperationsDashboardService service = new AiOperationsDashboardService(
                new AiOperationsDashboardSignalProviderRegistry(List.of(provider)),
                () -> LocalDate.of(2026, 5, 21)
        );

        AiOperationsDashboardOverview overview = service.overview(new AiOperationsDashboardQuery(
                10002L,
                555L,
                "STR245027-NAE",
                "AE",
                "last7Days"
        ));

        assertTrue(overview.getMetricCards().stream()
                .anyMatch(card -> "sales_freshness".equals(card.getKey())
                        && card.getValue() == null
                        && "partial_success".equals(card.getState())));
        assertEquals("partial_success", overview.getEvidence().getState());
        assertTrue(overview.getEvidence().getItems().stream()
                .anyMatch(item -> "sales-freshness-scope".equals(item.getId())
                        && item.getDescription().contains("date=2026-05-19")));
    }
}

package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NoonAdsAdvertiserContextFailureFlowTest {

    @Test
    void shouldStopRetryingAndPauseOnlyTheAdsPlan() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        NoonPullFailurePolicy failurePolicy = new NoonPullFailurePolicy(clock);
        NoonPullFoundationService foundation =
                new NoonPullFoundationService(repository, clock, failurePolicy);
        NoonReportPuller puller = new NoonReportPuller(
                foundation,
                new NoonRiskBackoffGuard(new InMemoryNoonRiskBackoffRepository(), clock),
                failurePolicy
        );
        NoonPullPlanRecord plan = foundation.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        NoonPullTaskRecord task = foundation.createTaskForPlan(
                plan.getId(),
                NoonPullTaskDraft.builder()
                        .ownerUserId(307L)
                        .storeCode("STR108065")
                        .siteCode("AE")
                        .pullType(NoonPullType.REPORT)
                        .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                        .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                        .targetIdentity("ads:2026-07-28")
                        .targetDateFrom(LocalDate.of(2026, 7, 28))
                        .targetDateTo(LocalDate.of(2026, 7, 28))
                        .build()
        ).orElseThrow();
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR108065")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .reportType("advertising")
                .dateFrom(LocalDate.of(2026, 7, 28))
                .dateTo(LocalDate.of(2026, 7, 28))
                .maxPollAttempts(2)
                .build();

        NoonReportPullResult result = puller.execute(
                task.getId(),
                request,
                new AdvertiserContextFailureProvider(),
                file -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("ads_advertiser_context_mismatch", persisted.getFailureType());
        assertEquals(Boolean.FALSE, persisted.getRetryable());
        assertEquals(Boolean.TRUE, persisted.getRequiresManualAction());
        assertTrue(repository.selectPlan(plan.getId()).isPaused());
    }

    private static final class AdvertiserContextFailureProvider implements NoonReportProvider {
        @Override
        public String createExport(NoonReportPullRequest request) {
            return "EXP-ADS";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            return NoonReportExportStatus.ready("https://download.test/ads.csv");
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            throw new NoonInterfacePullException(
                    "ads advertiser context mismatch: Noon HTTP 400 at /_svc/productads/v2/noon/metrics"
            );
        }
    }
}

package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoonRiskBackoffSuccessIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void successfulReportResetsItsReportAndAccountWideRiskState() {
        NoonPullFoundationService foundation = mock(NoonPullFoundationService.class);
        NoonRiskBackoffGuard guard = emptyGuard();
        NoonReportPullRequest request = reportRequest();
        NoonPullTaskRecord running = task(NoonPullTaskStatus.RUNNING);
        running.setStartedAt(LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
        NoonPullTaskRecord created = running.copy();
        created.setReportExportId("EXP-1");
        created.setReportPollAttempts(0);
        when(foundation.markRunning(11L, "noon-report-puller")).thenReturn(running);
        when(foundation.recordReportExportCreated(eq(11L), eq("EXP-1"), anyString())).thenReturn(created);
        when(foundation.recordReportExportPollResult(eq(11L), eq("EXP-1"), any(), anyInt(), any(), anyString()))
                .thenReturn(created);
        when(foundation.markSucceeded(eq(11L), anyString(), anyString())).thenReturn(task(NoonPullTaskStatus.SUCCEEDED));
        NoonReportProvider provider = new NoonReportProvider() {
            public String createExport(NoonReportPullRequest ignored) { return "EXP-1"; }
            public NoonReportExportStatus pollExport(NoonReportPullRequest ignored, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/report.csv", 1);
            }
            public byte[] download(NoonReportPullRequest ignored, String downloadUrl) {
                return "sku,units\nSKU-1,1\n".getBytes(StandardCharsets.UTF_8);
            }
        };

        new NoonReportPuller(foundation, guard, new NoonPullFailurePolicy(CLOCK)).execute(
                11L, request, provider, file -> NoonReportProcessResult.succeeded(1, 0)
        );

        verify(guard).recordSuccess(
                argThat(scope -> NoonRiskBackoffScope.report(request).getScopeKey().equals(scope.getScopeKey())),
                eq("SALES")
        );
    }

    @Test
    void successfulInterfaceResetsItsInterfaceAndAccountWideRiskState() {
        NoonPullFoundationService foundation = mock(NoonPullFoundationService.class);
        NoonRiskBackoffGuard guard = emptyGuard();
        NoonInterfacePullRequest request = interfaceRequest();
        when(foundation.markRunning(12L, "noon-interface-puller")).thenReturn(task(NoonPullTaskStatus.RUNNING));
        when(foundation.recordProgress(eq(12L), any(), anyInt(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(task(NoonPullTaskStatus.RUNNING));
        NoonPullTaskRecord succeeded = task(NoonPullTaskStatus.SUCCEEDED);
        succeeded.setSourceBatchId("product-batch");
        when(foundation.markSucceeded(eq(12L), anyString(), anyString())).thenReturn(succeeded);

        new NoonInterfacePuller(foundation, guard, new NoonPullFailurePolicy(CLOCK)).execute(
                12L,
                request,
                (ignored, page) -> NoonInterfacePullPage.builder()
                        .items(List.of(Map.of("sku", "SKU-1")))
                        .pageNumber(page)
                        .totalItems(1)
                        .hasNextPage(false)
                        .requestCount(1)
                        .build()
        );

        verify(guard).recordSuccess(
                argThat(scope -> NoonRiskBackoffScope.productInterface(request).getScopeKey().equals(scope.getScopeKey())),
                eq("PRODUCT")
        );
    }

    @Test
    void pendingReportDoesNotResetRiskState() {
        NoonPullFoundationService foundation = mock(NoonPullFoundationService.class);
        NoonRiskBackoffGuard guard = emptyGuard();
        NoonPullTaskRecord running = task(NoonPullTaskStatus.RUNNING);
        running.setStartedAt(LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
        NoonPullTaskRecord created = running.copy();
        created.setReportExportId("EXP-2");
        when(foundation.markRunning(13L, "noon-report-puller")).thenReturn(running);
        when(foundation.recordReportExportCreated(eq(13L), eq("EXP-2"), anyString())).thenReturn(created);
        when(foundation.recordReportExportPollResult(eq(13L), eq("EXP-2"), any(), anyInt(), any(), anyString()))
                .thenReturn(created);

        new NoonReportPuller(foundation, guard, new NoonPullFailurePolicy(CLOCK)).execute(
                13L,
                reportRequest(),
                new NoonReportProvider() {
                    public String createExport(NoonReportPullRequest ignored) { return "EXP-2"; }
                    public NoonReportExportStatus pollExport(NoonReportPullRequest ignored, String exportId) {
                        return NoonReportExportStatus.pending();
                    }
                    public byte[] download(NoonReportPullRequest ignored, String downloadUrl) { return new byte[0]; }
                },
                file -> NoonReportProcessResult.succeeded(1, 0)
        );

        verify(guard, never()).recordSuccess(any(), anyString());
    }

    private NoonRiskBackoffGuard emptyGuard() {
        NoonRiskBackoffGuard guard = mock(NoonRiskBackoffGuard.class);
        when(guard.currentHold(any())).thenReturn(Optional.empty());
        return guard;
    }

    private NoonPullTaskRecord task(NoonPullTaskStatus status) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setStatus(status);
        return task;
    }

    private NoonReportPullRequest reportRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L).storeCode("STR69486-NSA").siteCode("SA")
                .dataDomain(NoonPullDataDomain.SALES).reportType("productviewsandsalesdata")
                .dateFrom(LocalDate.of(2026, 7, 19)).dateTo(LocalDate.of(2026, 7, 19)).build();
    }

    private NoonInterfacePullRequest interfaceRequest() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(307L).storeCode("STR245027-NAE").siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT).requestName("product-list")
                .targetIdentity("product-list").timeoutSeconds(30).build();
    }
}

package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NoonPullDiagnosticsControllerTest {

    @Mock
    private ObjectProvider<NoonPullFoundationService> serviceProvider;

    @Mock
    private NoonPullFoundationService service;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepositoryProvider;

    @Mock
    private NoonPullSmokeRunRepository smokeRunRepository;

    private NoonPullDiagnosticsController controller;

    @BeforeEach
    void setUp() {
        controller = new NoonPullDiagnosticsController(serviceProvider, sessionTokenService, smokeRunRepositoryProvider);
    }

    @Test
    void shouldExposePlansAndTasksForSystemAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(service.listPlans()).thenReturn(List.of(plan()));
        when(service.listTasks()).thenReturn(List.of(task()));

        NoonPullDiagnosticsView view = controller.overview(request);

        assertEquals(1, view.getPlans().size());
        assertEquals(1, view.getTasks().size());
        assertEquals("PRODUCT", view.getPlans().get(0).getDataDomain());
        assertEquals("SUCCEEDED", view.getTasks().get(0).getStatus());
        assertEquals("RETRY", view.getTasks().get(0).getRetryAction());
        assertEquals(Boolean.TRUE, view.getTasks().get(0).getRetryable());
        assertEquals(Boolean.FALSE, view.getTasks().get(0).getRequiresManualAction());
        assertEquals("safe summary", view.getTasks().get(0).getDiagnosticSummary());
    }

    @Test
    void shouldExposeRecentSmokeRunsForSystemAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(smokeRunRepositoryProvider.getIfAvailable()).thenReturn(smokeRunRepository);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(service.listPlans()).thenReturn(List.of());
        when(service.listTasks()).thenReturn(List.of());
        when(smokeRunRepository.listRecent(20)).thenReturn(List.of(smokeRun()));

        NoonPullDiagnosticsView view = controller.overview(request);

        assertEquals(1, view.getSmokeRuns().size());
        NoonPullDiagnosticsView.SmokeRunView run = view.getSmokeRuns().get(0);
        assertEquals(140000L, run.getId());
        assertEquals("test", run.getTargetEnvironment());
        assertEquals("STR245027-NAE", run.getStoreCode());
        assertEquals(List.of("ORDER_SMOKE_READY"), run.getMissingRequirements());
        assertEquals(false, run.isProductionSchedulingAllowed());
        assertFalse(run.getRollbackOrGlobalPauseStrategy().contains("abc"));
        assertFalse(run.getRollbackOrGlobalPauseStrategy().contains("token-abc"));
        assertFalse(run.getRollbackOrGlobalPauseStrategy().contains("https://download.noon.test/raw.csv"));
        assertEquals(1, run.getEvidence().size());
        NoonPullSmokeEvidenceView evidence = run.getEvidence().get(0);
        assertEquals("ORDER", evidence.getDataDomain());
        assertEquals(NoonPullTaskStatus.FAILED.name(), evidence.getStatus());
        assertNull(evidence.getSourceBatchId());
        assertEquals("provider_not_configured", evidence.getFailureClassification());
    }

    @Test
    void shouldRedactSensitiveDiagnosticsAndDownloadUrls() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonPullTaskRecord task = task();
        task.setDiagnosticSummary("cookie=abc password=secret api_key=key authorization: bearer token-abc https://download.noon.test/raw.csv");
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(service.listPlans()).thenReturn(List.of(plan()));
        when(service.listTasks()).thenReturn(List.of(task));

        NoonPullDiagnosticsView view = controller.overview(request);

        String summary = view.getTasks().get(0).getDiagnosticSummary();
        assertFalse(summary.contains("abc"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("token-abc"));
        assertFalse(summary.contains("https://download.noon.test/raw.csv"));
    }

    @Test
    void shouldPauseResumeAndRetryFromDiagnosticsForSystemAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        NoonPullPlanRecord paused = plan();
        paused.setPaused(true);
        paused.setPauseReason("smoke rollback");
        NoonPullPlanRecord resumed = plan();
        resumed.setPaused(false);
        NoonPullTaskRecord retryTask = task();
        retryTask.setId(130001L);
        retryTask.setStatus(NoonPullTaskStatus.QUEUED);
        when(service.pausePlan(120000L, "smoke rollback")).thenReturn(paused);
        when(service.resumePlan(120000L)).thenReturn(resumed);
        when(service.retryTask(130000L)).thenReturn(retryTask);

        NoonPullDiagnosticsView.PlanView pausedView = controller.pausePlan(
                120000L,
                new NoonPullPauseCommand("smoke rollback"),
                request
        );
        NoonPullDiagnosticsView.PlanView resumedView = controller.resumePlan(120000L, request);
        NoonPullDiagnosticsView.TaskView retryView = controller.retryTask(130000L, request);

        assertEquals(true, pausedView.isPaused());
        assertEquals("smoke rollback", pausedView.getPauseReason());
        assertEquals(false, resumedView.isPaused());
        assertEquals("QUEUED", retryView.getStatus());
        assertEquals(130001L, retryView.getId());
    }

    @Test
    void shouldRejectNonSystemAdminDiagnosticsAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10002L, 2L, 1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.overview(request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(smokeRunRepository);
    }

    @Test
    void shouldTranslateServiceUnavailable() {
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.overview(new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    private NoonPullPlanRecord plan() {
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setId(120000L);
        plan.setOwnerUserId(307L);
        plan.setStoreCode("STR245027");
        plan.setSiteCode("AE");
        plan.setPullType(NoonPullType.INTERFACE);
        plan.setDataDomain(NoonPullDataDomain.PRODUCT);
        plan.setTriggerMode(NoonPullTriggerMode.ONBOARDING);
        plan.setEnabled(true);
        plan.setPaused(false);
        plan.setCreatedAt(LocalDateTime.of(2026, 5, 22, 12, 0));
        plan.setUpdatedAt(LocalDateTime.of(2026, 5, 22, 12, 0));
        return plan;
    }

    private NoonPullTaskRecord task() {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(130000L);
        task.setPlanId(120000L);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR245027");
        task.setSiteCode("AE");
        task.setPullType(NoonPullType.INTERFACE);
        task.setDataDomain(NoonPullDataDomain.PRODUCT);
        task.setTriggerMode(NoonPullTriggerMode.ONBOARDING);
        task.setTargetIdentity("catalog:list");
        task.setStatus(NoonPullTaskStatus.SUCCEEDED);
        task.setSourceBatchId("batch-product-001");
        task.setRetryAction("RETRY");
        task.setRetryable(true);
        task.setRequiresManualAction(false);
        task.setDiagnosticSummary("safe summary");
        task.setCreatedAt(LocalDateTime.of(2026, 5, 22, 12, 0));
        task.setUpdatedAt(LocalDateTime.of(2026, 5, 22, 12, 1));
        return task;
    }

    private NoonPullSmokeRunRecord smokeRun() {
        NoonPullSmokeRunRecord run = new NoonPullSmokeRunRecord();
        run.setId(140000L);
        run.setTargetEnvironment("test");
        run.setOwnerUserId(10002L);
        run.setProjectCode("PRJ245027");
        run.setProjectName("xingyao");
        run.setStoreCode("STR245027-NAE");
        run.setSiteCode("AE");
        run.setRollbackOrGlobalPauseStrategy("global pause cookie=abc authorization: bearer token-abc https://download.noon.test/raw.csv");
        run.setRequestedDataDomains(List.of("ORDER"));
        run.setMissingRequirements(List.of("ORDER_SMOKE_READY"));
        run.setEvidenceGateSatisfied(true);
        run.setProductionSchedulingAllowed(false);
        NoonPullSmokeEvidenceRecord evidence = new NoonPullSmokeEvidenceRecord();
        evidence.setId(141000L);
        evidence.setRunId(140000L);
        evidence.setSequenceNo(1);
        evidence.setDataDomain("ORDER");
        evidence.setTargetIdentity("orders:2026-05-21..2026-05-21");
        evidence.setDateFrom(java.time.LocalDate.of(2026, 5, 21));
        evidence.setDateTo(java.time.LocalDate.of(2026, 5, 21));
        evidence.setRowOrItemCount(0);
        evidence.setTaskId(130018L);
        evidence.setSourceBatchId(null);
        evidence.setElapsedMillis(73L);
        evidence.setStatus(NoonPullTaskStatus.FAILED.name());
        evidence.setFailureClassification("provider_not_configured");
        run.setEvidence(List.of(evidence));
        run.setCreatedAt(LocalDateTime.of(2026, 5, 22, 17, 30));
        run.setUpdatedAt(LocalDateTime.of(2026, 5, 22, 17, 30));
        return run;
    }
}

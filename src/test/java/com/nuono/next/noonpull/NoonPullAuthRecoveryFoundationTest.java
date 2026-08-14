package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullAuthRecoveryFoundationTest {
    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService service;
    private AtomicInteger manualLoginRequests;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        service = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        manualLoginRequests = new AtomicInteger();
        service.setAccountSessionAttention(new NoonAccountSessionAttentionPort() {
            @Override
            public void requireManualLogin() {
                manualLoginRequests.incrementAndGet();
            }

            @Override
            public boolean blocksProviderCalls() {
                return true;
            }
        });
    }

    @Test
    void authFailureStopsOnlyThisTaskAndRequiresManualLoginWithoutReplay() {
        NoonPullTaskRecord task = reportTask();
        task.setReportExportId("EXP-EXISTING");
        task.setCheckpointCursor("page:7");
        repository.updateTask(task);

        NoonPullTaskRecord failed = service.markFailedWithPolicy(
                task.getId(), "auth_required: persisted cookie expired", 1
        );

        assertEquals(NoonPullTaskStatus.FAILED, failed.getStatus());
        assertEquals(NoonPullFailureType.AUTH_REQUIRED.code(), failed.getFailureType());
        assertEquals(NoonPullRetryAction.MANUAL_ACTION.name(), failed.getRetryAction());
        assertFalse(failed.getRetryable());
        assertTrue(failed.getRequiresManualAction());
        assertEquals("EXP-EXISTING", failed.getReportExportId());
        assertEquals("page:7", failed.getCheckpointCursor());
        assertEquals(1, manualLoginRequests.get());
    }

    @Test
    void nonAuthFailureDoesNotRequestManualLogin() {
        NoonPullTaskRecord task = reportTask();

        NoonPullTaskRecord failed = service.markFailedWithPolicy(task.getId(), "HTTP 502", 1);

        assertEquals(NoonPullFailureType.PROVIDER_UNAVAILABLE.code(), failed.getFailureType());
        assertTrue(failed.getRetryable());
        assertEquals(0, manualLoginRequests.get());
    }

    private NoonPullTaskRecord reportTask() {
        NoonPullPlanRecord plan = service.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(308L)
                .storeCode("STR313934-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return service.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(308L)
                .storeCode("STR313934-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity("sales:2026-07-15..2026-07-15")
                .targetDateFrom(LocalDate.of(2026, 7, 15))
                .targetDateTo(LocalDate.of(2026, 7, 15))
                .build()).orElseThrow();
    }
}

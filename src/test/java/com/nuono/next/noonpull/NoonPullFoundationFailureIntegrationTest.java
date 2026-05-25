package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullFoundationFailureIntegrationTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T04:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        service = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
    }

    @Test
    void shouldRecordRetryDecisionOnPlanForTransientFailure() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), productTask()).orElseThrow();

        NoonPullTaskRecord failed = service.markFailedWithPolicy(task.getId(), "socket timeout", 2);
        NoonPullPlanRecord updatedPlan = repository.selectPlan(plan.getId());

        assertEquals(NoonPullTaskStatus.FAILED, failed.getStatus());
        assertEquals("timeout", failed.getFailureType());
        assertEquals("RETRY", failed.getRetryAction());
        assertTrue(failed.getRetryable());
        assertNotNull(updatedPlan.getLatestFailureAt());
        assertEquals("timeout", updatedPlan.getLatestFailureType());
        assertNotNull(updatedPlan.getNextRetryAt());
    }

    @Test
    void shouldPausePlanForCaptchaWithoutTightRetry() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), productTask()).orElseThrow();

        NoonPullTaskRecord failed = service.markFailedWithPolicy(task.getId(), "captcha required", 1);
        NoonPullPlanRecord updatedPlan = repository.selectPlan(plan.getId());

        assertEquals("captcha_required", failed.getFailureType());
        assertEquals("PAUSE", failed.getRetryAction());
        assertTrue(failed.getRequiresManualAction());
        assertTrue(updatedPlan.isPaused());
        assertTrue(updatedPlan.getPauseReason().contains("captcha_required"));
        assertNotNull(updatedPlan.getNextRetryAt());
    }

    private NoonPullPlanDraft productPlan() {
        return NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .scheduleExpression("manual")
                .build();
    }

    private NoonPullTaskDraft productTask() {
        return NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity("catalog:list")
                .build();
    }
}

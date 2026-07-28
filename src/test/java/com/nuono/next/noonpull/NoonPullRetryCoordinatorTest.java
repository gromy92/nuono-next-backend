package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullRetryCoordinatorTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonPullRetryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(
                repository,
                CLOCK,
                new NoonPullFailurePolicy(CLOCK)
        );
        coordinator = new NoonPullRetryCoordinator(repository, foundationService, CLOCK);
    }

    @Test
    void requeuesLatestRetryableManualTaskWhenPersistedBackoffIsDue() {
        NoonPullTaskRecord failed = failedAsnTask("HTTP 502 Bad Gateway");
        NoonPullPlanRecord plan = repository.selectPlan(failed.getPlanId());
        plan.setNextRetryAt(LocalDateTime.of(2026, 7, 28, 3, 59));
        repository.updatePlan(plan);

        List<NoonPullTaskRecord> retries = coordinator.retryDueFailedTasks();

        assertThat(retries).hasSize(1);
        assertThat(retries.get(0).getStatus()).isEqualTo(NoonPullTaskStatus.QUEUED);
        assertThat(retries.get(0).getTargetIdentity()).isEqualTo(failed.getTargetIdentity());
        assertThat(repository.listTasks()).hasSize(2);
        assertThat(repository.selectPlan(failed.getPlanId()).getNextRetryAt()).isNull();
    }

    @Test
    void leavesRetryableManualTaskAloneUntilBackoffIsDue() {
        NoonPullTaskRecord failed = failedAsnTask("HTTP 502 Bad Gateway");

        assertThat(coordinator.retryDueFailedTasks()).isEmpty();
        assertThat(repository.listTasks()).hasSize(1);
        assertThat(repository.selectPlan(failed.getPlanId()).getNextRetryAt())
                .isAfter(LocalDateTime.of(2026, 7, 28, 4, 0));
    }

    private NoonPullTaskRecord failedAsnTask(String failure) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(65267L)
                .storeCode("STR65267-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .scheduleExpression("manual")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(
                plan.getId(),
                NoonPullTaskDraft.builder()
                        .ownerUserId(plan.getOwnerUserId())
                        .storeCode(plan.getStoreCode())
                        .siteCode(plan.getSiteCode())
                        .pullType(plan.getPullType())
                        .dataDomain(plan.getDataDomain())
                        .triggerMode(plan.getTriggerMode())
                        .targetIdentity("official-warehouse-asn-list")
                        .build()
        ).orElseThrow();
        foundationService.markRunning(task.getId(), "official-warehouse-asn-list-sync");
        return foundationService.markFailedWithPolicy(task.getId(), failure, 1);
    }
}

package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListPullService;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListTaskExecutor;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.web.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OfficialWarehouseAsnListPullServiceTest {
    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonPullRetryCoordinator retryCoordinator;
    private OfficialWarehouseAsnListTaskExecutor executor;
    private OfficialWarehouseAsnListPullService service;
    private BusinessAccessContext access;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        retryCoordinator = new NoonPullRetryCoordinator(repository, foundationService, clock);
        executor = mock(OfficialWarehouseAsnListTaskExecutor.class);
        ObjectProvider<OfficialWarehouseAsnListTaskExecutor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(executor);
        service = new OfficialWarehouseAsnListPullService(foundationService, retryCoordinator, provider);
        access = BusinessAccessContext.builder()
                .sessionUserId(65267L)
                .businessOwnerUserId(65267L)
                .storeCodes(Set.of("STR65267-NSA"))
                .storeOwnerUserIds(Map.of("STR65267-NSA", 65267L))
                .build();
    }

    @Test
    void shouldPersist502AsRetryableTaskWithBackoff() {
        when(executor.syncNoonAsnListForTask(access, "STR65267-NSA", "SA"))
                .thenThrow(new IllegalStateException("HTTP 502 Bad Gateway"));

        assertThatThrownBy(() -> service.sync(access, "STR65267-NSA", "SA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("502");

        NoonPullTaskRecord task = repository.listTasks().get(0);
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());
        assertThat(task.getStatus()).isEqualTo(NoonPullTaskStatus.FAILED);
        assertThat(task.getFailureType()).isEqualTo(NoonPullFailureType.PROVIDER_UNAVAILABLE.code());
        assertThat(task.getRetryable()).isTrue();
        assertThat(task.getRequiresManualAction()).isFalse();
        assertThat(plan.getNextRetryAt()).isAfter(LocalDateTime.of(2026, 7, 28, 4, 0));
    }

    @Test
    void shouldResumeSameTaskAutomaticallyAfterAuthRecovery() {
        AsnListSyncView success = new AsnListSyncView();
        success.fetched = 3;
        when(executor.syncNoonAsnListForTask(any(), eq("STR65267-NSA"), eq("SA")))
                .thenThrow(new IllegalStateException("auth_required: cookie expired"))
                .thenReturn(success);
        foundationService.setAuthRecoveryQueue((task, rawFailure) -> {
            repository.blockTaskForAuth(
                    task.getId(),
                    88001L,
                    rawFailure,
                    LocalDateTime.of(2026, 7, 28, 4, 0)
            );
            return Optional.of(88001L);
        });

        assertThatThrownBy(() -> service.sync(access, "STR65267-NSA", "SA"))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getStatus().value()).isEqualTo(409);
                    assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING");
                    assertThat(problem.getDetails()).containsEntry("automaticRetry", true);
                });

        NoonPullTaskRecord blocked = repository.listTasks().get(0);
        assertThat(blocked.getStatus()).isEqualTo(NoonPullTaskStatus.BLOCKED_AUTH);
        repository.requeueBlockedTaskAfterAuthForTest(
                blocked.getId(),
                blocked.getAuthRecoveryId(),
                LocalDateTime.of(2026, 7, 28, 4, 1)
        );

        NoonPullTaskStatus status = service.executeScheduled(repository.selectTask(blocked.getId()));

        assertThat(status).isEqualTo(NoonPullTaskStatus.SUCCEEDED);
        verify(executor, times(2)).syncNoonAsnListForTask(any(), eq("STR65267-NSA"), eq("SA"));
    }
}

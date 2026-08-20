package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Ali1688Dp10ManualSyncTest {
    private static final Instant NOW = Instant.parse("2026-08-21T01:15:30.123456Z");

    @Mock private Ali1688HistoricalOrderMapper authorizations;
    @Mock private DataPullTaskStore tasks;
    private Ali1688Dp10ManualSync sync;

    @BeforeEach
    void setUp() {
        sync = new Ali1688Dp10ManualSync(
                authorizations,
                tasks,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void enqueuesAnImmediateDp10TaskForTheExactAuthorization() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        when(authorizations.selectAuthorizationById(31L, 41L)).thenReturn(authorization);
        when(tasks.nextTaskId()).thenReturn(71L);
        when(tasks.enqueue(any(DataPullTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(sync.request(31L, 41L, 51L)).isTrue();

        ArgumentCaptor<DataPullTask> captured = ArgumentCaptor.forClass(DataPullTask.class);
        verify(tasks).enqueue(captured.capture());
        DataPullTask task = captured.getValue();
        assertThat(task.getId()).isEqualTo(71L);
        assertThat(task.getOperationCode()).isEqualTo(OperationCode.DP10);
        assertThat(task.getProviderChannel()).isEqualTo("ALI1688_OPEN_API");
        assertThat(task.getOwnerUserId()).isEqualTo(31L);
        assertThat(task.getAccountKey()).isEqualTo(
                Ali1688Dp10ScopeIdentity.accountKey(authorization));
        assertThat(task.getScopeKey()).isEqualTo(
                Ali1688Dp10ScopeIdentity.scopeKey(authorization));
        assertThat(task.getScheduleSlot())
                .isEqualTo(LocalDateTime.parse("2026-08-21T01:15:30.123"));
        assertThat(task.getBusinessWindowKey())
                .isEqualTo("DP10:manual:2026-08-21T01:15:30.123:operator:51");
        assertThat(task.getState()).isEqualTo(TaskState.QUEUED);
        assertThat(task.getStepCode()).isEqualTo(Ali1688Dp10Job.INITIAL_STEP);
    }

    @Test
    void rejectsAuthorizationOutsideTheExactActiveOpenApiScope() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        authorization.setStatus("revoked");
        when(authorizations.selectAuthorizationById(31L, 41L)).thenReturn(authorization);

        assertThat(sync.request(31L, 41L, 51L)).isFalse();

        verify(tasks, never()).nextTaskId();
        verify(tasks, never()).enqueue(any());
    }

    private static Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(41L);
        row.setOwnerUserId(31L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("seller-9001");
        row.setStatus("authorized");
        return row;
    }
}

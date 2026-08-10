package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreightBillAutoSyncServiceTest {
    @Mock private FreightBillProviderAdapter provider;
    @Mock private LogisticsAutoSyncAccessContextFactory accessContextFactory;
    @Mock private FreightBillSyncPreviewService previewService;
    @Mock private InTransitFreightCostService freightCostService;
    @Mock private OperationalTaskService taskService;
    @Mock private LogisticsAutoSyncMapper mapper;

    @Test
    void incompleteSnapshotFailsPreviewAndNeverWritesActualCosts() {
        LogisticsAutoSyncAccount account = account(true);
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        FreightBillFetchResult fetch = FreightBillFetchResult.success(command, false, 1, "digest", List.of("partial"));
        FreightBillSyncPreview preview = new FreightBillSyncPreview();
        preview.setCommittable(false);
        preview.setIssues(List.of(new FreightBillSyncPreview.Issue("INCOMPLETE_SNAPSHOT", null, null, "partial")));
        when(provider.sourceSystem()).thenReturn("YITE");
        when(provider.fetchFreightBills(any())).thenReturn(fetch);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(previewService.preview(any(), eq(fetch))).thenReturn(preview);
        when(taskService.start(eq(FreightBillAutoSyncService.TASK_TYPE), any(), any(OperationalTaskPayload.class)))
                .thenReturn(task(92001L));

        FreightBillAutoSyncTaskSummary summary = service().runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("preview_blocked");
        verify(freightCostService, never()).syncActualCosts(any());
        verify(taskService).fail(eq(92001L), eq("PREVIEW_BLOCKED"), any(), any());
    }

    @Test
    void independentCommitSwitchDefaultsToPreviewOnly() {
        LogisticsAutoSyncAccount account = account(false);
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        FreightBillFetchResult fetch = FreightBillFetchResult.success(command, true, 1, "digest", List.of());
        FreightBillSyncPreview preview = new FreightBillSyncPreview();
        preview.setCommittable(true);
        preview.setBillCount(1);
        preview.setChangedCommand(command);
        when(provider.sourceSystem()).thenReturn("YITE");
        when(provider.fetchFreightBills(any())).thenReturn(fetch);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(previewService.preview(any(), eq(fetch))).thenReturn(preview);
        when(taskService.start(eq(FreightBillAutoSyncService.TASK_TYPE), any(), any(OperationalTaskPayload.class)))
                .thenReturn(task(92002L));

        FreightBillAutoSyncTaskSummary summary = service().runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("preview_only");
        verify(freightCostService, never()).syncActualCosts(any());
        verify(taskService).complete(eq(92002L), any(), eq("三方物流费用预览通过，费用提交开关未开启。"));
    }

    @Test
    void writesOnlyChangedBillsAfterCompletePreviewAndExplicitCostCommit() {
        LogisticsAutoSyncAccount account = account(true);
        ActualFreightSyncCommand fetched = new ActualFreightSyncCommand();
        ActualFreightSyncCommand changed = new ActualFreightSyncCommand();
        FreightBillFetchResult fetch = FreightBillFetchResult.success(fetched, true, 1, "digest", List.of());
        FreightBillSyncPreview preview = new FreightBillSyncPreview();
        preview.setCommittable(true);
        preview.setBillCount(1);
        preview.setChangedCommand(changed);
        changed.setBills(List.of(new com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand()));
        when(provider.sourceSystem()).thenReturn("YITE");
        when(provider.fetchFreightBills(any())).thenReturn(fetch);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(previewService.preview(any(), eq(fetch))).thenReturn(preview);
        when(taskService.start(eq(FreightBillAutoSyncService.TASK_TYPE), any(), any(OperationalTaskPayload.class)))
                .thenReturn(task(92003L));

        FreightBillAutoSyncTaskSummary summary = service().runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("committed");
        verify(freightCostService).syncActualCosts(changed);
    }

    private FreightBillAutoSyncService service() {
        return new FreightBillAutoSyncService(
                List.of(provider),
                cipher(),
                accessContextFactory,
                previewService,
                freightCostService,
                taskService,
                mapper,
                new ObjectMapper()
        );
    }

    private static LogisticsAutoSyncAccount account(boolean costCommitEnabled) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(180001L);
        account.setOwnerUserId(307L);
        account.setOperatorUserId(408L);
        account.setSourceSystem("YITE");
        account.setForwarderName("义特");
        account.setLoginAccount("secret-login");
        account.setPasswordCipher(cipher().encrypt("super-secret"));
        account.setFreightBillCommitEnabled(costCommitEnabled);
        account.setMinIntervalHours(24);
        return account;
    }

    private static LogisticsCredentialCipher cipher() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.setCredentialCipherSecret("test-logistics-cipher-secret");
        return new LogisticsCredentialCipher(properties);
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(408L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .storeCodes(Set.of("STR108065-NSA"))
                .build();
    }

    private static OperationalTask task(Long id) {
        OperationalTask task = new OperationalTask();
        task.setId(id);
        return task;
    }
}

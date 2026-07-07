package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncCommitView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncIssueView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import com.nuono.next.intransit.InTransitPluginSyncService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncServiceTest {

    @Mock
    private LogisticsProviderAdapter provider;
    @Mock
    private LogisticsAutoSyncAccessContextFactory accessContextFactory;
    @Mock
    private InTransitPluginSyncService pluginSyncService;
    @Mock
    private OperationalTaskService taskService;
    @Mock
    private LogisticsAutoSyncMapper mapper;

    @Test
    void emptyProviderResultCompletesNoDataAndDoesNotPreviewOrCommit() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(false);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(provider.fetch(any(LogisticsProviderFetchRequest.class)))
                .thenReturn(LogisticsProviderFetchResult.success(new PluginSyncCommand()));
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91001L));

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("no_data");
        verify(pluginSyncService, never()).preview(any());
        verify(pluginSyncService, never()).commit(any());
        verify(taskService).complete(eq(91001L), any(), eq("物流自动同步完成：没有新数据。"));
        verify(mapper).updateAccountRunState(
                eq(180001L),
                eq(91001L),
                eq("SUCCESS"),
                eq(null),
                eq("NO_DATA"),
                eq("READY"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(null),
                eq(null),
                eq(null),
                eq(408L)
        );
    }

    @Test
    void previewBlockedFailsTaskAndDoesNotCommit() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(false);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(provider.fetch(any(LogisticsProviderFetchRequest.class)))
                .thenReturn(LogisticsProviderFetchResult.success(commandWithBatch()));
        PluginSyncPreviewView preview = new PluginSyncPreviewView();
        preview.setCommittable(false);
        preview.setBatchCount(1);
        preview.setIssues(List.of(
                PluginSyncIssueView.error(
                        "XGGEKSA04082",
                        "BOX-01",
                        "secret-login",
                        "barcode",
                        "barcode super-secret 未解析，不能提交。"
                )
        ));
        when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(preview);
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91002L));
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("preview_blocked");
        verify(pluginSyncService).preview(any(PluginSyncCommand.class));
        verify(pluginSyncService, never()).commit(any());
        verify(taskService).fail(
                eq(91002L),
                eq("PREVIEW_BLOCKED"),
                eq("物流自动同步预览未通过，未提交。"),
                resultCaptor.capture()
        );
        assertThat(resultCaptor.getValue())
                .contains("\"status\":\"preview_blocked\"")
                .contains("\"previewIssueCount\":1")
                .contains("\"batchNo\":\"XGGEKSA04082\"")
                .contains("\"field\":\"barcode\"")
                .contains("[redacted]")
                .doesNotContain("secret-login")
                .doesNotContain("super-secret");
        verify(mapper).updateAccountRunState(
                eq(180001L), eq(91002L), eq("SUCCESS"), eq("FAILED"), eq("FAILED"),
                eq("FAILED"), eq(null), any(LocalDateTime.class), eq(null),
                eq("PREVIEW_BLOCKED"), eq("物流自动同步预览未通过，未提交。"), eq(408L)
        );
    }

    @Test
    void previewOnlyAccountDoesNotCommitAndDoesNotLeakSecretsInTaskJson() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(false);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(provider.fetch(any(LogisticsProviderFetchRequest.class)))
                .thenReturn(LogisticsProviderFetchResult.success(commandWithBatch()));
        PluginSyncPreviewView preview = new PluginSyncPreviewView();
        preview.setCommittable(true);
        preview.setBatchCount(1);
        when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(preview);
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91003L));
        ArgumentCaptor<OperationalTaskPayload> payloadCaptor = ArgumentCaptor.forClass(OperationalTaskPayload.class);
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("preview_only");
        verify(pluginSyncService, never()).commit(any());
        verify(taskService).start(eq("LOGISTICS_AUTO_SYNC"), any(), payloadCaptor.capture());
        verify(taskService).complete(eq(91003L), resultCaptor.capture(), eq("物流自动同步完成：预览通过，账号未开启自动提交。"));
        assertThat(payloadCaptor.getValue().getPayloadJson())
                .doesNotContain("secret-login")
                .doesNotContain("super-secret");
        assertThat(resultCaptor.getValue())
                .doesNotContain("secret-login")
                .doesNotContain("super-secret");
    }

    @Test
    void commitEnabledAccountCommitsAfterCommittablePreview() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(true);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(provider.fetch(any(LogisticsProviderFetchRequest.class)))
                .thenReturn(LogisticsProviderFetchResult.success(commandWithBatch()));
        PluginSyncPreviewView preview = new PluginSyncPreviewView();
        preview.setCommittable(true);
        when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(preview);
        when(pluginSyncService.commit(any(PluginSyncCommand.class))).thenReturn(new PluginSyncCommitView());
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91004L));

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("committed");
        verify(pluginSyncService).commit(any(PluginSyncCommand.class));
        verify(taskService).complete(eq(91004L), any(), eq("物流自动同步完成：已自动提交。"));
    }

    @Test
    void captchaRequiredAppliesCooldownAndSkipsPreviewAndCommit() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(false);
        when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
        when(provider.fetch(any(LogisticsProviderFetchRequest.class)))
                .thenReturn(LogisticsProviderFetchResult.failure("CAPTCHA_REQUIRED", "需要验证码"));
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91005L));

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("provider_failed");
        verify(pluginSyncService, never()).preview(any());
        verify(pluginSyncService, never()).commit(any());
        verify(taskService).fail(91005L, "CAPTCHA_REQUIRED", "需要验证码");
        verify(mapper).updateAccountRunState(
                eq(180001L), eq(91005L), eq("FAILED"), eq(null), eq("FAILED"),
                eq("BLOCKED"), eq(null), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("CAPTCHA_REQUIRED"), eq("需要验证码"), eq(408L)
        );
    }

    @Test
    void unexpectedExceptionAfterTaskStartFailsTaskAndLeavesLastSyncedAtUntouched() {
        LogisticsAutoSyncService service = service();
        LogisticsAutoSyncAccount account = account(false);
        when(accessContextFactory.requireAccessContext(account))
                .thenThrow(new IllegalStateException("账号 secret-login 不能操作该店铺"));
        when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task(91006L));

        LogisticsAutoSyncTaskSummary summary = service.runAccount(account);

        assertThat(summary.getStatus()).isEqualTo("internal_failed");
        assertThat(summary.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.PROVIDER_ERROR);
        assertThat(summary.getFailureMessage())
                .contains("[redacted]")
                .doesNotContain("secret-login")
                .doesNotContain("super-secret");
        verify(pluginSyncService, never()).preview(any());
        verify(pluginSyncService, never()).commit(any());
        verify(taskService).fail(eq(91006L), eq(LogisticsProviderFailureCode.PROVIDER_ERROR), any());
        verify(mapper).updateAccountRunState(
                eq(180001L), eq(91006L), eq("FAILED"), eq(null), eq("FAILED"),
                eq("FAILED"), eq(null), any(LocalDateTime.class), eq(null),
                eq(LogisticsProviderFailureCode.PROVIDER_ERROR), any(), eq(408L)
        );
    }

    private LogisticsAutoSyncService service() {
        when(provider.sourceSystem()).thenReturn("CHIC");
        return new LogisticsAutoSyncService(
                List.of(provider),
                cipher(),
                accessContextFactory,
                pluginSyncService,
                taskService,
                mapper
        );
    }

    private static LogisticsAutoSyncAccount account(boolean commitEnabled) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(180001L);
        account.setOwnerUserId(307L);
        account.setOperatorUserId(408L);
        account.setSourceSystem("CHIC");
        account.setForwarderName("Chic");
        account.setLoginAccount("secret-login");
        account.setPasswordCipher(cipher().encrypt("super-secret"));
        account.setCommitEnabled(commitEnabled);
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

    private static PluginSyncCommand commandWithBatch() {
        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo("BATCH-001");
        PluginSyncCommand command = new PluginSyncCommand();
        command.setBatches(List.of(batch));
        return command;
    }

    private static OperationalTask task(Long taskId) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        return task;
    }
}

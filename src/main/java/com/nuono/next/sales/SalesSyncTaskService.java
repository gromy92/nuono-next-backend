package com.nuono.next.sales;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class SalesSyncTaskService {

    private final SalesSyncTaskRepository taskRepository;
    private final NoonSalesReportProvider reportProvider;
    private final NoonSalesCsvImportService importService;
    private NoonAccountSessionAttentionPort accountSessionAttention;

    public SalesSyncTaskService(
            SalesSyncTaskRepository taskRepository,
            NoonSalesReportProvider reportProvider,
            NoonSalesCsvImportService importService
    ) {
        this.taskRepository = taskRepository;
        this.reportProvider = reportProvider;
        this.importService = importService;
    }

    public SalesSyncTaskRecord triggerAndRun(SalesSyncTaskCommand command) {
        SalesSyncTaskRecord task = taskRepository.createQueued(command);
        return runQueued(task.getId());
    }

    public SalesSyncTaskRecord runQueued(Long taskId) {
        if (!taskRepository.claimRunning(taskId)) {
            return taskRepository.findById(taskId);
        }
        SalesSyncTaskRecord task = taskRepository.findById(taskId);
        SalesSyncTaskCommand command = command(task);
        try {
            NoonSalesReportPayload payload = reportProvider.fetch(new NoonSalesReportRequest(
                    command.getOwnerUserId(),
                    command.getLogicalStoreId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    command.getDateFrom(),
                    command.getDateTo()
            ));
            NoonSalesCsvImportResult result = importService.importCsv(new NoonSalesCsvImportCommand(
                    command.getOwnerUserId(),
                    command.getLogicalStoreId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    payload.getSourceFilename(),
                    payload.getCsv()
            ));
            return taskRepository.markSucceeded(task.getId(), result);
        } catch (RuntimeException exception) {
            if (requiresManualLogin(exception)) {
                if (accountSessionAttention != null) {
                    accountSessionAttention.requireManualLogin();
                }
                return taskRepository.markFailed(
                        task.getId(),
                        "Noon 共享账号需要人工登录；系统不会自动发送验证码、重试或重放销量导入。"
                );
            }
            return taskRepository.markFailed(task.getId(), readableMessage(exception));
        }
    }

    @Autowired(required = false)
    void setAccountSessionAttention(NoonAccountSessionAttentionPort accountSessionAttention) {
        this.accountSessionAttention = accountSessionAttention;
    }

    public SalesSyncTaskRecord getTask(Long taskId) {
        return taskRepository.findById(taskId);
    }

    private String readableMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Noon sales sync failed";
        }
        return exception.getMessage();
    }

    private boolean requiresManualLogin(RuntimeException exception) {
        return NoonAuthenticationFailureClassifier.isAuthenticationFailure(exception)
                && !NoonAuthenticationFailureClassifier
                        .hasPermanentAuthenticationFailureEvidence(exception);
    }

    private SalesSyncTaskCommand command(SalesSyncTaskRecord task) {
        return new SalesSyncTaskCommand(
                task.getOwnerUserId(),
                task.getLogicalStoreId(),
                task.getStoreCode(),
                task.getSiteCode(),
                task.getDateFrom(),
                task.getDateTo(),
                task.getRequestedBy(),
                task.getTriggerType()
        );
    }

}

package com.nuono.next.sales;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthRetrySuppressedException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SalesSyncTaskService {

    private final SalesSyncTaskRepository taskRepository;
    private final NoonSalesReportProvider reportProvider;
    private final NoonSalesCsvImportService importService;
    private final SalesFactRepository salesFactRepository;
    private NoonAuthWaitQueue authWaitQueue = request -> Optional.empty();

    public SalesSyncTaskService(
            SalesSyncTaskRepository taskRepository,
            NoonSalesReportProvider reportProvider,
            NoonSalesCsvImportService importService,
            SalesFactRepository salesFactRepository
    ) {
        this.taskRepository = taskRepository;
        this.reportProvider = reportProvider;
        this.importService = importService;
        this.salesFactRepository = salesFactRepository;
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
            markSiteOffersNotListedIfConfirmedEmptySite(command, result);
            return taskRepository.markSucceeded(task.getId(), result);
        } catch (RuntimeException exception) {
            SalesSyncTaskRecord waiting = waitForAuthorization(task, exception);
            if (waiting != null) {
                return waiting;
            }
            return taskRepository.markFailed(task.getId(), readableMessage(exception));
        }
    }

    @Autowired(required = false)
    void setAuthWaitQueue(NoonAuthWaitQueue authWaitQueue) {
        if (authWaitQueue != null) {
            this.authWaitQueue = authWaitQueue;
        }
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

    private SalesSyncTaskRecord waitForAuthorization(
            SalesSyncTaskRecord task,
            RuntimeException exception
    ) {
        if (task == null
                || !NoonAuthenticationFailureClassifier.isAuthenticationFailure(exception)
                || NoonAuthenticationFailureClassifier
                        .hasPermanentAuthenticationFailureEvidence(exception)) {
            return null;
        }
        try {
            Optional<Long> recoveryId = authWaitQueue.enqueue(NoonAuthWaitRequest.task(
                    task.getOwnerUserId(),
                    null,
                    task.getStoreCode(),
                    task.getSiteCode(),
                    "SALES_SYNC",
                    task.getId(),
                    "REPORT_EXPORT",
                    NoonAuthResumePolicy.AUTO_RESUME
            ));
            return recoveryId
                    .map(id -> taskRepository.markWaitingForAuthorization(task.getId(), id))
                    .orElse(null);
        } catch (NoonAuthRetrySuppressedException suppressed) {
            return taskRepository.markFailed(task.getId(), suppressed.getMessage());
        } catch (RuntimeException queueFailure) {
            return null;
        }
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
                task.getTriggerType(),
                listingCoverageMode(task.getListingCoverageMode())
        );
    }

    private SalesListingCoverageMode listingCoverageMode(String value) {
        try {
            return value == null
                    ? SalesListingCoverageMode.NONE
                    : SalesListingCoverageMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return SalesListingCoverageMode.NONE;
        }
    }

    private void markSiteOffersNotListedIfConfirmedEmptySite(
            SalesSyncTaskCommand command,
            NoonSalesCsvImportResult result
    ) {
        if (command.getListingCoverageMode() != SalesListingCoverageMode.CONFIRMED_EMPTY_SITE) {
            return;
        }
        if (!"empty".equals(result.getStatus())) {
            return;
        }
        salesFactRepository.markSiteOffersNotListedForEmptyReport(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                updatedBy(command)
        );
    }

    private Long updatedBy(SalesSyncTaskCommand command) {
        return command.getRequestedBy() == null ? command.getOwnerUserId() : command.getRequestedBy();
    }
}

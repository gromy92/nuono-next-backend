package com.nuono.next.sales;

import org.springframework.stereotype.Service;

@Service
public class SalesSyncTaskService {

    private final SalesSyncTaskRepository taskRepository;
    private final NoonSalesReportProvider reportProvider;
    private final NoonSalesCsvImportService importService;
    private final SalesFactRepository salesFactRepository;

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
        taskRepository.markRunning(task.getId());
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
            return taskRepository.markFailed(task.getId(), readableMessage(exception));
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

package com.nuono.next.sales;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SalesSyncTaskService {

    private final SalesSyncTaskRepository taskRepository;
    private final NoonSalesReportProvider reportProvider;
    private final NoonSalesCsvImportService importService;

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
        SalesSyncTaskRecord task = taskRepository.findReusableExportTask(command)
                .orElseGet(() -> taskRepository.createQueued(command));
        task = taskRepository.markRunning(task.getId());
        try {
            NoonSalesReportRequest request = new NoonSalesReportRequest(
                    command.getOwnerUserId(),
                    command.getLogicalStoreId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    command.getDateFrom(),
                    command.getDateTo()
            );
            NoonSalesReportExportStatus exportStatus = exportStatus(request, task);
            task = taskRepository.markExportStatus(task.getId(), exportStatus);
            if (exportStatus.isFailed()) {
                throw new IllegalStateException("Noon 销量报表导出失败：" + exportStatus.getStatus());
            }
            if (!exportStatus.isComplete()) {
                return task;
            }

            NoonSalesReportPayload payload = reportProvider.download(request, exportStatus);
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
            return taskRepository.markFailed(task.getId(), readableMessage(exception));
        }
    }

    public SalesSyncTaskRecord getTask(Long taskId) {
        return taskRepository.findById(taskId);
    }

    private NoonSalesReportExportStatus exportStatus(NoonSalesReportRequest request, SalesSyncTaskRecord task) {
        String exportCode = task.getExportCode();
        if (!StringUtils.hasText(exportCode)) {
            NoonSalesReportExportStatus created = reportProvider.createExport(request);
            taskRepository.markExportStatus(task.getId(), created);
            exportCode = created.getExportCode();
            if (created.isComplete() || created.isFailed()) {
                return created;
            }
        }
        if (!StringUtils.hasText(exportCode)) {
            throw new IllegalStateException("Noon 销量报表导出创建失败：缺少 exportCode。");
        }
        NoonSalesReportExportStatus latest = NoonSalesReportExportStatus.pending(exportCode, "PENDING");
        int maxPollAttempts = Math.max(1, reportProvider.maxPollAttempts());
        for (int attempt = 1; attempt <= maxPollAttempts; attempt++) {
            latest = reportProvider.pollExport(request, exportCode);
            if (latest.isComplete() || latest.isFailed()) {
                return latest;
            }
            if (attempt < maxPollAttempts) {
                reportProvider.waitBeforeNextPoll();
            }
        }
        return latest;
    }

    private String readableMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Noon sales sync failed";
        }
        return exception.getMessage();
    }
}

package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonInterfacePuller {
    private final NoonPullFoundationService foundationService;

    public NoonInterfacePuller(NoonPullFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public NoonInterfacePullResult execute(
            Long taskId,
            NoonInterfacePullRequest request,
            NoonInterfacePullProvider provider
    ) {
        if (taskId == null || request == null || provider == null) {
            throw new IllegalArgumentException("Noon interface pull task, request and provider are required.");
        }
        foundationService.markRunning(taskId, "noon-interface-puller");
        List<Map<String, Object>> items = new ArrayList<>();
        int pageNumber = request.getResumePage();
        int pageCount = 0;
        int requestCount = request.getInitialRequestCount();
        int processedItemCount = request.getInitialProcessedItemCount();
        try {
            boolean hasNext;
            do {
                if (pageNumber > request.getMaxPages()) {
                    throw new NoonInterfacePullException("timeout: interface pull exceeded max pages " + request.getMaxPages());
                }
                NoonInterfacePullPage page = provider.fetchPage(request, pageNumber);
                if (page == null) {
                    throw new NoonInterfacePullException("provider unavailable: empty interface page");
                }
                items.addAll(page.getItems());
                pageCount++;
                processedItemCount += page.getItems().size();
                requestCount += Math.max(page.getRequestCount(), 1);
                String checkpointCursor = "page:" + pageNumber;
                String summary = progressSummary(request, pageCount, requestCount, processedItemCount);
                hasNext = page.isHasNextPage();
                if (hasNext && budgetExhausted(request.getBudget(), pageCount, requestCount, processedItemCount)) {
                    String sourceBatchId = sourceBatchId(request, taskId);
                    NoonPullTaskRecord partial = foundationService.markPartial(
                            taskId,
                            sourceBatchId,
                            diagnosticSummary(request, pageCount, requestCount),
                            checkpointCursor,
                            processedItemCount,
                            requestCount,
                            "page:" + (pageNumber + 1),
                            summary,
                            "large_store_backfill_in_progress"
                    );
                    return NoonInterfacePullResult.completed(
                            partial.getStatus(),
                            partial.getSourceBatchId(),
                            items,
                            pageCount,
                            requestCount
                    );
                }
                pageNumber++;
            } while (hasNext);

            String sourceBatchId = sourceBatchId(request, taskId);
            String checkpointCursor = pageCount == 0 ? null : "page:" + (pageNumber - 1);
            foundationService.recordProgress(
                    taskId,
                    checkpointCursor,
                    processedItemCount,
                    requestCount,
                    null,
                    progressSummary(request, pageCount, requestCount, processedItemCount),
                    "ready"
            );
            NoonPullTaskRecord succeeded = foundationService.markSucceeded(
                    taskId,
                    sourceBatchId,
                    diagnosticSummary(request, pageCount, requestCount)
            );
            return NoonInterfacePullResult.succeeded(
                    succeeded.getSourceBatchId(),
                    items,
                    pageCount,
                    requestCount
            );
        } catch (RuntimeException exception) {
            NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(taskId, failureMessage(request, exception), 1);
            return NoonInterfacePullResult.failed(failed);
        }
    }

    private boolean budgetExhausted(
            NoonPullRequestBudget budget,
            int pageCount,
            int requestCount,
            int processedItemCount
    ) {
        if (budget == null) {
            return false;
        }
        if (budget.getMaxPagesPerRun() != null && budget.getMaxPagesPerRun() > 0
                && pageCount >= budget.getMaxPagesPerRun()) {
            return true;
        }
        if (budget.getMaxRequestsPerRun() != null && budget.getMaxRequestsPerRun() > 0
                && requestCount >= budget.getMaxRequestsPerRun()) {
            return true;
        }
        return budget.getMaxProductsPerRun() != null && budget.getMaxProductsPerRun() > 0
                && processedItemCount >= budget.getMaxProductsPerRun();
    }

    private String sourceBatchId(NoonInterfacePullRequest request, Long taskId) {
        String domain = request.getDataDomain() == null
                ? "unknown"
                : request.getDataDomain().name().toLowerCase(Locale.ROOT);
        return "noon-interface-" + domain + "-" + taskId;
    }

    private String diagnosticSummary(NoonInterfacePullRequest request, int pageCount, int requestCount) {
        StringBuilder summary = new StringBuilder();
        summary.append(request.safeDescriptor());
        summary.append("; pages=").append(pageCount);
        summary.append("; requests=").append(requestCount);
        if (StringUtils.hasText(request.getRequestSummary())) {
            summary.append("; ").append(request.getRequestSummary());
        }
        return summary.toString();
    }

    private String progressSummary(
            NoonInterfacePullRequest request,
            int pageCount,
            int requestCount,
            int processedItemCount
    ) {
        return request.safeDescriptor()
                + "; pages=" + pageCount
                + "; requests=" + requestCount
                + "; processedItems=" + processedItemCount;
    }

    private String failureMessage(NoonInterfacePullRequest request, RuntimeException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
        return request.safeDescriptor() + "; " + message;
    }
}

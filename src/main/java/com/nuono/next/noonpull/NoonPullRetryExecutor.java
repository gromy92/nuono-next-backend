package com.nuono.next.noonpull;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListPullService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class NoonPullRetryExecutor {
    private final NoonPullFoundationService foundationService;
    private final NoonPullRetryCoordinator retryCoordinator;
    private final ObjectProvider<OfficialWarehouseAsnListPullService> asnListPullServiceProvider;

    public NoonPullRetryExecutor(
            NoonPullFoundationService foundationService,
            NoonPullRetryCoordinator retryCoordinator,
            ObjectProvider<OfficialWarehouseAsnListPullService> asnListPullServiceProvider
    ) {
        this.foundationService = foundationService;
        this.retryCoordinator = retryCoordinator;
        this.asnListPullServiceProvider = asnListPullServiceProvider;
    }

    public int retryDueTasks() {
        return retryCoordinator.retryDueFailedTasks().size();
    }

    public void executeAsn(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        OfficialWarehouseAsnListPullService service = asnListPullServiceProvider.getIfAvailable();
        if (service == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled official warehouse ASN list service is disabled",
                    retryCoordinator.attemptNumber(task)
            );
            result.failed();
            return;
        }
        NoonPullTaskStatus status = service.executeScheduled(task);
        if (status == NoonPullTaskStatus.SUCCEEDED) {
            result.executed();
        } else if (status == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.skipped();
        } else {
            result.failed();
        }
    }
}

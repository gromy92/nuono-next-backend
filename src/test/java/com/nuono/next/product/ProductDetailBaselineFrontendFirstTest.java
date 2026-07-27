package com.nuono.next.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.productpublicdetail.ProductPublicDetailFetchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductDetailBaselineFrontendFirstTest {

    @Test
    void usableFrontendDetailNeverCallsMerchantBackend() {
        AtomicInteger backendCalls = new AtomicInteger();
        OperationalTaskService tasks = taskService();
        ProductDetailBaselineBackfillService service = service(
                tasks,
                ProductPublicDetailSyncStatus.PARTIAL,
                "frontend detail available"
        );

        service.enqueue(command(), "daily-maintenance-audit", (command, reason) -> {
            backendCalls.incrementAndGet();
            return readySnapshot();
        });

        verify(tasks).complete(9001L, "{\"ready\":true,\"source\":\"NOON_FRONTEND\"}", "Noon 前台详情已准备。");
        verify(tasks, never()).fail(eq(9001L), any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(0, backendCalls.get());
    }

    @Test
    void explicitFrontendNotFoundIsTheOnlyMerchantFallbackCondition() {
        AtomicInteger backendCalls = new AtomicInteger();
        OperationalTaskService tasks = taskService();
        ProductDetailBaselineBackfillService service = service(
                tasks,
                ProductPublicDetailSyncStatus.NOT_FOUND,
                "frontend explicitly not found"
        );

        service.enqueue(command(), "daily-maintenance-audit", (command, reason) -> {
            backendCalls.incrementAndGet();
            return readySnapshot();
        });

        verify(tasks).complete(9001L, "{\"ready\":true}", "详情基线已准备。");
        org.junit.jupiter.api.Assertions.assertEquals(1, backendCalls.get());
    }

    @Test
    void frontendFailureOrBackoffNeverCallsMerchantBackend() {
        AtomicInteger backendCalls = new AtomicInteger();
        OperationalTaskService tasks = taskService();
        ProductDetailBaselineBackfillService service = service(
                tasks,
                ProductPublicDetailSyncStatus.FAILED,
                "Noon 前台详情正在风险退避。"
        );

        service.enqueue(command(), "daily-maintenance-audit", (command, reason) -> {
            backendCalls.incrementAndGet();
            return readySnapshot();
        });

        verify(tasks).fail(9001L, "PRODUCT_DETAIL_FRONTEND_FETCH_FAILED", "Noon 前台详情正在风险退避。");
        org.junit.jupiter.api.Assertions.assertEquals(0, backendCalls.get());
    }

    private ProductDetailBaselineBackfillService service(
            OperationalTaskService tasks,
            ProductPublicDetailSyncStatus frontendStatus,
            String message
    ) {
        return new ProductDetailBaselineBackfillService(
                tasks,
                (accountKey, task) -> task.run(),
                (ownerUserId, storeCode) -> 50003L,
                (command, taskId) -> ProductPublicDetailFetchResult.of(frontendStatus, message)
        );
    }

    private OperationalTaskService taskService() {
        OperationalTaskService tasks = mock(OperationalTaskService.class);
        when(tasks.findActive(any(), any())).thenReturn(Optional.empty());
        OperationalTask task = new OperationalTask();
        task.setId(9001L);
        when(tasks.start(any(), any(), any())).thenReturn(task);
        return tasks;
    }

    private ProductMasterFetchCommand command() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setSkuParent("Z123");
        command.setPartnerSku("PAPERSAYSB123");
        command.setPskuCode("PSKU-123");
        return command;
    }

    private ProductMasterSnapshotView readySnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        return snapshot;
    }
}

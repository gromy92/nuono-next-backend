package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductActiveStateBackfillHandlerTest {
    @Test
    void reconciliationUsesMerchantSnapshotAndSkipsPublicDetail() {
        OperationalTaskService tasks = mock(OperationalTaskService.class);
        when(tasks.findActive(any(), any())).thenReturn(Optional.empty());
        OperationalTask started = new OperationalTask();
        started.setId(150001L);
        when(tasks.start(any(), any(), any(OperationalTaskPayload.class))).thenReturn(started);
        ProductDetailBaselineBackfillService service = new ProductDetailBaselineBackfillService(
                tasks,
                (accountKey, task) -> task.run(),
                (ownerUserId, storeCode) -> 50003L,
                (command, taskId) -> {
                    throw new AssertionError("public detail is not trusted active-state evidence");
                }
        );
        AtomicInteger merchantReads = new AtomicInteger();

        service.enqueue(command(), ProductActiveStateBackfillHandler.REASON, (command, reason) -> {
            merchantReads.incrementAndGet();
            ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
            snapshot.setReady(true);
            snapshot.setSiteOffers(List.of(Map.of("storeCode", "canman", "isActive", false)));
            return snapshot;
        });

        assertEquals(1, merchantReads.get());
        verify(tasks).complete(
                150001L,
                "{\"ready\":true,\"activeStateEvidence\":true,\"source\":\"NOON_PRICING_INFO\"}",
                "商品在售状态已核对。"
        );
    }

    private ProductMasterFetchCommand command() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("canman");
        command.setSkuParent("PAPERSAYSB132");
        return command;
    }
}

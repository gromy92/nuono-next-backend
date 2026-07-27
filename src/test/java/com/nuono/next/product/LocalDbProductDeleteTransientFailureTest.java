package com.nuono.next.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.publish.ProductPublishCommandService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDbProductDeleteTransientFailureTest {

    @Test
    void preWriteEofShouldEnterBackoff() throws Exception {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("unexpected EOF");
        when(fixture.commandService.isRetryableNoonRequestFailure(failure)).thenReturn(true);
        when(fixture.commandService.scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(true);

        invokeFailure(fixture, failure, "pre_delete_captured");

        verify(fixture.commandService).scheduleProductDeleteRetryOrManualCheck(
                eq(fixture.task),
                eq("product_delete_transient_failure"),
                anyString(),
                eq("product_delete_retry_exhausted"),
                anyString(),
                anyString()
        );
    }

    @Test
    void permanentPreWriteHttpFourHundredShouldFailWithoutBackoff() throws Exception {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("Noon HTTP 400 invalid parameter");
        when(fixture.commandService.isRetryableNoonRequestFailure(failure)).thenReturn(false);

        invokeFailure(fixture, failure, "pre_delete_captured");

        verify(fixture.commandService).updateStatus(
                eq(fixture.task), eq("failed"), eq("product_delete_failed"), anyString(), anyString(),
                isNull(), any(LocalDateTime.class), isNull()
        );
        verify(fixture.commandService, never()).scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void uncertainWriteShouldRequireManualCheckWithoutClassifyingForReplay() throws Exception {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("connection reset by peer");

        invokeFailure(fixture, failure, "unmap_submitted");

        verify(fixture.commandService).updateStatus(
                eq(fixture.task), eq("pending_manual_check"), eq("product_delete_result_unknown"),
                anyString(), anyString(), isNull(), any(LocalDateTime.class), isNull()
        );
        verify(fixture.commandService, never()).isRetryableNoonRequestFailure(any());
        verify(fixture.commandService, never()).scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    private void invokeFailure(Fixture fixture, IllegalStateException failure, String stage)
            throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "handleProductDeleteFailure",
                ProductPublishTaskRecord.class,
                ProductWorkbenchRecord.class,
                ProductMasterSnapshotView.class,
                Map.class,
                List.class,
                IllegalStateException.class,
                String.class,
                ProductMasterSnapshotView.class
        );
        method.setAccessible(true);
        method.invoke(
                fixture.service,
                fixture.task,
                fixture.record,
                fixture.snapshot,
                Map.of(),
                List.of(),
                failure,
                stage,
                fixture.snapshot
        );
    }

    private Fixture fixture() {
        ProductPublishCommandService commandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class), null, null, mock(StoreSyncMapper.class), null,
                new ObjectMapper(), mock(ProductNoonAdapter.class), null, null, null, null, null,
                null, commandService, null, null, null, null, null
        );
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR108065-NAE");
        task.setCurrentSiteCode("AE");
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PRODUCT_DELETE);
        task.setStatus("running");
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(snapshot);
        record.setDraftSnapshot(snapshot);
        when(commandService.buildTaskView(any(), anyBoolean(), any(), any()))
                .thenReturn(new ProductPublishTaskView());
        return new Fixture(service, commandService, task, record, snapshot);
    }

    private static final class Fixture {
        private final LocalDbProductMasterService service;
        private final ProductPublishCommandService commandService;
        private final ProductPublishTaskRecord task;
        private final ProductWorkbenchRecord record;
        private final ProductMasterSnapshotView snapshot;

        private Fixture(
                LocalDbProductMasterService service,
                ProductPublishCommandService commandService,
                ProductPublishTaskRecord task,
                ProductWorkbenchRecord record,
                ProductMasterSnapshotView snapshot
        ) {
            this.service = service;
            this.commandService = commandService;
            this.task = task;
            this.record = record;
            this.snapshot = snapshot;
        }
    }
}

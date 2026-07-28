package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.publish.ProductPublishCommandService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalDbProductPublishWriteOutcomeTest {

    @Test
    void outcomeUnknownShouldBecomeManualCheckWithoutAutomaticTaskReplay() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductPublishCommandService commandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class), null, null, mock(StoreSyncMapper.class), null,
                objectMapper, mock(ProductNoonAdapter.class), null, null, null, null, null, null,
                commandService, null, null, null, null, null
        );
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64003L);
        task.setOwnerUserId(307L);
        task.setProductMasterId(52001L);
        task.setStoreCode("STORE");
        task.setCurrentSiteCode("AE");
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PUBLISH_CURRENT);
        task.setStatus("running");
        task.setLockedBy("claim-3");
        task.setVersionNo(2);
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(snapshot);
        record.setDraftSnapshot(snapshot);
        ProductPublishWriteOutcomeUnknownException failure =
                ProductPublishWriteOutcomeUnknownException.forProviderFailure(
                        "offer price", true, new IllegalStateException("HTTP 503")
        );
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "suspendPublishTaskForAuthRecovery",
                ProductPublishTaskRecord.class,
                ProductWorkbenchRecord.class,
                ProductMasterSnapshotView.class,
                String.class,
                Map.class,
                List.class,
                IllegalStateException.class
        );
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(
                service, task, record, snapshot, "AE", Map.of("write", 2),
                new ArrayList<>(), failure
        ));

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandService).updateStatus(
                eq(task),
                eq("pending_manual_check"),
                eq(ProductPublishWriteOutcomeUnknownException.ERROR_CODE),
                argThat((String message) -> message.contains("不会自动重放")),
                resultCaptor.capture(),
                isNull(),
                any(LocalDateTime.class),
                isNull()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertTrue(result.path("writeMayHaveOccurred").asBoolean());
        assertTrue(result.path("priorWriteCompleted").asBoolean());
        assertEquals("offer price", result.path("writeOperation").asText());
        verify(commandService, never()).scheduleNoonWriteRetryOrManualCheck(
                any(), any(), any(), any()
        );
        verify(commandService, never()).scheduleNoonRetryOrManualCheck(
                any(), any(), any(), any(), any(), any()
        );
    }
}

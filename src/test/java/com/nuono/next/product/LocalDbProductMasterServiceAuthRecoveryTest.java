package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

class LocalDbProductMasterServiceAuthRecoveryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void existingProductPublishAuthFailureShouldSuspendAndNeverScheduleAutomaticWriteRetry()
            throws Exception {
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class),
                null,
                null,
                mock(StoreSyncMapper.class),
                null,
                objectMapper,
                mock(ProductNoonAdapter.class),
                null,
                null,
                null,
                null,
                null,
                null,
                publishCommandService,
                null,
                null,
                null,
                null,
                null
        );
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getIdentity().put("skuParent", "Z1");
        snapshot.getIdentity().put("partnerSku", "P1");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(307L);
        task.setProductMasterId(52001L);
        task.setStoreCode("STR108065-NAE");
        task.setProjectCode("PRJ-1");
        task.setCurrentSiteCode("AE");
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PUBLISH_CURRENT);
        task.setStatus("running");
        task.setBaselineJson(objectMapper.writeValueAsString(snapshot));
        task.setDraftJson(objectMapper.writeValueAsString(snapshot));
        task.setLockedBy("claim-1");
        task.setVersionNo(2);
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(snapshot);
        record.setDraftSnapshot(snapshot);
        ProductWriteAuthRequiredException authFailure = new ProductWriteAuthRequiredException(
                991L,
                true,
                "Noon Project 授权恢复中；recoveryId=991。",
                new IllegalStateException("auth_required")
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

        boolean suspended = (Boolean) method.invoke(
                service,
                task,
                record,
                snapshot,
                "AE",
                Map.of("write", 1),
                new ArrayList<>(),
                authFailure
        );

        assertTrue(suspended);
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(publishCommandService).updateStatus(
                eq(task),
                eq("pending_manual_check"),
                eq(ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING),
                argThat(message -> message.contains("不会自动重放")),
                resultCaptor.capture(),
                isNull(),
                any(LocalDateTime.class),
                isNull()
        );
        JsonNode result = objectMapper.readTree(resultCaptor.getValue());
        assertEquals(991L, result.path("recoveryId").asLong());
        assertTrue(result.path("writeMayHaveOccurred").asBoolean());
        verify(publishCommandService, never()).scheduleNoonWriteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString()
        );
        verify(publishCommandService, never()).scheduleNoonRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        verify(publishCommandService, never()).scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void groupPartialAuthFailureShouldPersistWriteProgress() throws Exception {
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class), null, null, mock(StoreSyncMapper.class), null,
                objectMapper, mock(ProductNoonAdapter.class), null, null, null, null, null, null,
                publishCommandService, null, null, null, null, null
        );
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64002L);
        task.setOwnerUserId(307L);
        task.setProductMasterId(52001L);
        task.setStoreCode("STR108065-NAE");
        task.setTaskType(ProductPublishCommandService.TASK_TYPE_PUBLISH_CURRENT);
        task.setStatus("running");
        task.setLockedBy("claim-2");
        task.setVersionNo(2);
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(snapshot);
        record.setDraftSnapshot(snapshot);
        ProductWriteAuthRequiredException authFailure = new ProductWriteAuthRequiredException(
                992L, false, "Noon Project 授权恢复中。", new IllegalStateException("auth_required")
        );
        ProductGroupPartialPublishException groupFailure = new ProductGroupPartialPublishException(
                "Group 写回后刷新缓存失败。",
                authFailure
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
                service, task, record, snapshot, "AE", Map.of(), List.of(), groupFailure
        ));

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(publishCommandService).updateStatus(
                eq(task),
                eq("pending_manual_check"),
                eq(ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING),
                anyString(),
                resultCaptor.capture(),
                isNull(),
                any(LocalDateTime.class),
                isNull()
        );
        assertTrue(objectMapper.readTree(resultCaptor.getValue())
                .path("writeMayHaveOccurred").asBoolean());
    }
}

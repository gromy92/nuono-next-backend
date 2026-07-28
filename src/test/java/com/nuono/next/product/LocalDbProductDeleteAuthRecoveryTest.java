package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.publish.ProductPublishCommandService;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalDbProductDeleteAuthRecoveryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginAuthFailureShouldSuspendWithoutAutomaticRetryOrNoonWrite() throws Exception {
        Fixture fixture = fixture();
        when(fixture.adapter.loginWithPersistedCookie(
                10002L, "nuonuo@example.test", null, "PRJ245027", "STR245027-NAE"
        )).thenThrow(authFailure(false));

        invokeDelete(fixture, "product_delete_queued");

        JsonNode result = capturedAuthResult(fixture);
        assertEquals("pending_manual_check", result.path("status").asText());
        assertFalse(result.path("writeMayHaveOccurred").asBoolean());
        verify(fixture.commandService, never()).scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        verify(fixture.adapter, never()).postWriteJson(any(), anyString(), any(), anyBoolean());
        verify(fixture.productMapper, never()).markProductMasterDeletedById(any(), any());
    }

    @Test
    void deleteAuthRejectionAfterUnmapShouldRetainUnmapCheckpoint() throws Exception {
        Fixture fixture = fixture();
        fixture.task.setResultJson(objectMapper.writeValueAsString(Map.of(
                "status", "submitted",
                "stage", "unmap_submitted",
                "preDeleteSnapshot", fixture.snapshot
        )));
        when(fixture.adapter.loginWithPersistedCookie(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(fixture.session);
        when(fixture.adapter.postJson(
                any(), eq(NoonProductGateway.PROJECT_LIST_URL), any(), eq(false)
        )).thenReturn(objectMapper.readTree(
                "{\"projects\":[{\"projectCode\":\"PRJ245027\"}]}"
        ));
        when(fixture.adapter.postWriteJson(
                any(), eq(NoonProductGateway.PSKU_DELETE_URL), any(), eq(true)
        )).thenThrow(authFailure(false));

        invokeDelete(fixture, "product_delete_queued");

        JsonNode result = capturedAuthResult(fixture);
        assertEquals("unmap_submitted", result.path("stage").asText());
        assertTrue(result.path("writeMayHaveOccurred").asBoolean());
        verify(fixture.commandService, never()).scheduleProductDeleteRetryOrManualCheck(
                any(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void deleteSubmittedCheckpointShouldOnlyReadAndNeverRepeatDeleteWrite() throws Exception {
        Fixture fixture = fixture();
        fixture.task.setResultJson(objectMapper.writeValueAsString(Map.of(
                "status", "pending_manual_check",
                "stage", "delete_submitted",
                "preDeleteSnapshot", fixture.snapshot,
                "writeMayHaveOccurred", true
        )));
        when(fixture.adapter.loginWithPersistedCookie(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(fixture.session);
        when(fixture.adapter.postJson(any(), anyString(), any(), anyBoolean()))
                .thenThrow(authFailure(false));

        invokeDelete(fixture, "product_delete_queued");

        JsonNode result = capturedAuthResult(fixture);
        assertEquals("delete_submitted", result.path("stage").asText());
        assertTrue(result.path("writeMayHaveOccurred").asBoolean());
        verify(fixture.adapter, never()).postWriteJson(any(), anyString(), any(), anyBoolean());
    }

    private JsonNode capturedAuthResult(Fixture fixture) throws Exception {
        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.commandService).updateStatus(
                eq(fixture.task),
                eq("pending_manual_check"),
                eq(ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING),
                argThat(message -> message.contains("不会自动继续删除或重建")),
                resultCaptor.capture(),
                isNull(),
                any(),
                isNull()
        );
        return objectMapper.readTree(resultCaptor.getValue());
    }

    private void invokeDelete(Fixture fixture, String previousStatus) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "executeProductDeleteTask",
                ProductPublishTaskRecord.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(fixture.service, fixture.task, previousStatus);
    }

    private Fixture fixture() throws Exception {
        Fixture fixture = new Fixture();
        fixture.productMapper = mock(ProductManagementMapper.class);
        fixture.storeMapper = mock(StoreSyncMapper.class);
        fixture.adapter = mock(ProductNoonAdapter.class);
        fixture.commandService = mock(ProductPublishCommandService.class);
        NoonSessionGateway gateway = testGateway();
        fixture.session = testSession(gateway);
        fixture.service = new LocalDbProductMasterService(
                fixture.productMapper, null, null, fixture.storeMapper, null, objectMapper,
                fixture.adapter, null, null, null, null, null, null, fixture.commandService,
                null, null, null, null, null
        );
        fixture.snapshot = new ProductMasterSnapshotView();
        fixture.snapshot.setReady(true);
        fixture.snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        fixture.snapshot.getStoreContext().put("projectCode", "PRJ245027");
        fixture.snapshot.getIdentity().put("skuParent", "ZSTALE");
        fixture.snapshot.getIdentity().put("partnerSku", "MILKYWAYA17");
        fixture.snapshot.getIdentity().put("pskuCode", "PSKU-CURRENT");
        fixture.task = new ProductPublishTaskRecord();
        fixture.task.setId(77001L);
        fixture.task.setOwnerUserId(10002L);
        fixture.task.setProductMasterId(64001L);
        fixture.task.setStoreCode("STR245027-NAE");
        fixture.task.setProjectCode("PRJ245027");
        fixture.task.setSkuParent("ZSTALE");
        fixture.task.setPartnerSku("MILKYWAYA17");
        fixture.task.setPskuCode("PSKU-CURRENT");
        fixture.task.setCurrentSiteCode("AE");
        fixture.task.setTaskType("product-delete");
        fixture.task.setStatus("running");
        fixture.task.setBaselineJson(objectMapper.writeValueAsString(fixture.snapshot));
        fixture.task.setDraftJson(objectMapper.writeValueAsString(fixture.snapshot));
        fixture.task.setRetryCount(0);
        fixture.task.setVerifyAttemptCount(0);
        fixture.task.setVersionNo(1);
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        store.setProjectCode("PRJ245027");
        store.setNoonPartnerProjectUser("nuonuo@example.test");
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(10002L);
        owner.setNoonPartnerProjectUser("nuonuo@example.test");
        when(fixture.storeMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(fixture.storeMapper.selectOwnerContext(10002L)).thenReturn(owner);
        when(fixture.adapter.openRequestCountScope()).thenReturn(gateway.openRequestCountScope());
        when(fixture.commandService.buildTaskView(any(), anyBoolean(), any(), any()))
                .thenReturn(new ProductPublishTaskView());
        return fixture;
    }

    private ProductWriteAuthRequiredException authFailure(boolean writeMayHaveOccurred) {
        return new ProductWriteAuthRequiredException(
                991L,
                writeMayHaveOccurred,
                "Noon Project 授权恢复中；recoveryId=991。",
                new IllegalStateException("auth_required: WHOAMI HTTP 307")
        );
    }

    private NoonSessionGateway testGateway() {
        return new NoonSessionGateway(
                objectMapper, null, false, 0, true, "", "", "en-sa", "en",
                false, false, "", "", "", "", "", "", "", "", false,
                "HTTP", "", 0, ""
        );
    }

    private NoonSession testSession(NoonSessionGateway gateway) throws Exception {
        Class<?> stateClass = Class.forName("com.nuono.next.noon.NoonSessionGateway$AuthSessionState");
        Constructor<NoonSession> constructor = NoonSession.class.getDeclaredConstructor(
                NoonSessionGateway.class, Long.class, String.class, String.class,
                stateClass, String.class, String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                gateway, 10002L, "nuonuo@example.test", "cookie", null,
                "PRJ245027", "STR245027-NAE"
        );
    }

    private static final class Fixture {
        private ProductManagementMapper productMapper;
        private StoreSyncMapper storeMapper;
        private ProductNoonAdapter adapter;
        private ProductPublishCommandService commandService;
        private LocalDbProductMasterService service;
        private ProductPublishTaskRecord task;
        private ProductMasterSnapshotView snapshot;
        private NoonSession session;
    }
}

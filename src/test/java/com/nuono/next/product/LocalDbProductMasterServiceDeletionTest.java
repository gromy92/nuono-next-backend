package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
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
import com.nuono.next.product.publish.ProductPublishCommandService;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncStoreRecord;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LocalDbProductMasterServiceDeletionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productDeleteShouldCreateBackgroundDeleteTaskBeforeAnyLocalSoftDelete() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        ProductReadModelService readModelService = mock(ProductReadModelService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                publishCommandService,
                readModelService,
                null,
                null,
                null,
                null
        );
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "MILKYWAYA17"
        )).thenReturn(productIdentity(64001L, 50001L, "STR245027-NAE", "AE", "ZNEWPSKU001", "MILKYWAYA17", "PSKU-CURRENT"));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setSkuParent("ZNEWPSKU001");
        task.setPartnerSku("MILKYWAYA17");
        task.setPskuCode("PSKU-CURRENT");
        when(publishCommandService.createProductDeleteTask(any(ProductPublishTaskCreateCommand.class)))
                .thenReturn(ProductPublishCommandService.ProductPublishTaskCreateResult.created(task));
        ProductListDatasetView expected = new ProductListDatasetView();
        LocalDbStoreInitializationService.StoreInitializationProductListItemView item =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        item.setSkuParent("ZNEWPSKU001");
        item.setPartnerSku("MILKYWAYA17");
        expected.setItems(java.util.List.of(item));
        when(readModelService.loadListDataset(any(ProductMasterFetchCommand.class))).thenReturn(expected);

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("ZOLDPSKU001");
        command.setPartnerSku("MILKYWAYA17");
        command.setPskuCode("PSKU-STALE");

        ProductListDatasetView actual = service.deleteProduct(command);

        assertSame(expected, actual);
        ArgumentCaptor<ProductPublishTaskCreateCommand> taskCaptor =
                ArgumentCaptor.forClass(ProductPublishTaskCreateCommand.class);
        verify(publishCommandService).createProductDeleteTask(taskCaptor.capture());
        ProductPublishTaskCreateCommand createCommand = taskCaptor.getValue();
        assertEquals(10002L, createCommand.getOwnerUserId());
        assertEquals(64001L, createCommand.getProductMasterId());
        assertEquals("STR245027-NAE", createCommand.getStoreCode());
        assertEquals("ZNEWPSKU001", createCommand.getSkuParent());
        assertEquals("MILKYWAYA17", createCommand.getPartnerSku());
        assertEquals("PSKU-CURRENT", createCommand.getPskuCode());
        assertTrue(createCommand.getIdempotencyKey().startsWith("delete:50001:MILKYWAYA17:"));
        assertTrue(createCommand.getRequestJson().contains("\"product-delete\""));
        assertTrue(createCommand.getDraftJson().contains("MILKYWAYA17"));
        assertEquals("商品删除已提交后台处理，请在发布状态和历史中查看进度。", actual.getMessage());
        assertEquals("product-delete", item.getLastPublishTask().get("taskType"));
        assertEquals("删除中", item.getLastPublishTask().get("statusLabel"));
        verify(productManagementMapper, never()).selectProductMasterIdByStoreCode(any(), any(), any());
        verify(productManagementMapper, never()).markProductMasterDeletedById(any(), any());
    }

    @Test
    void productRebuildShouldCreateDeleteTaskWithCurrentLocalListingDraft() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductProjectionPersistenceService productProjectionPersistenceService =
                mock(ProductProjectionPersistenceService.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        ProductReadModelService readModelService = mock(ProductReadModelService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                null,
                objectMapper,
                null,
                productProjectionPersistenceService,
                null,
                null,
                null,
                null,
                null,
                publishCommandService,
                readModelService,
                null,
                null,
                null,
                null
        );
        StoreSyncStoreRecord store = productStore();
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "MILKYWAYA17"
        )).thenReturn(productIdentity(64001L, 50001L, "STR245027-NAE", "AE", "ZOLDPSKU001", "MILKYWAYA17", "PSKU-CURRENT"));
        when(productProjectionPersistenceService.loadLatestBaselineSnapshot(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("MILKYWAYA17"),
                eq("ZOLDPSKU001"),
                anyList()
        )).thenReturn(productRebuildSnapshot());
        when(productProjectionPersistenceService.loadPersistedWorkbenchState(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("MILKYWAYA17"),
                eq("ZOLDPSKU001"),
                anyList()
        )).thenReturn(null);
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setSkuParent("ZOLDPSKU001");
        task.setPartnerSku("MILKYWAYA17");
        task.setPskuCode("PSKU-CURRENT");
        task.setRequestJson("{\"action\":\"product-delete\",\"rebuildAction\":\"product-rebuild\"}");
        when(publishCommandService.createProductDeleteTask(any(ProductPublishTaskCreateCommand.class)))
                .thenReturn(ProductPublishCommandService.ProductPublishTaskCreateResult.created(task));
        ProductListDatasetView expected = new ProductListDatasetView();
        LocalDbStoreInitializationService.StoreInitializationProductListItemView item =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        item.setSkuParent("ZOLDPSKU001");
        item.setPartnerSku("MILKYWAYA17");
        expected.setItems(List.of(item));
        when(readModelService.loadListDataset(any(ProductMasterFetchCommand.class))).thenReturn(expected);

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("ZOLDPSKU001");
        command.setPartnerSku("MILKYWAYA17");

        ProductListDatasetView actual = service.rebuildProduct(command);

        assertSame(expected, actual);
        ArgumentCaptor<ProductPublishTaskCreateCommand> taskCaptor =
                ArgumentCaptor.forClass(ProductPublishTaskCreateCommand.class);
        verify(publishCommandService).createProductDeleteTask(taskCaptor.capture());
        JsonNode request = objectMapper.readTree(taskCaptor.getValue().getRequestJson());
        assertEquals("product-delete", request.path("action").asText());
        assertEquals("product-rebuild", request.path("rebuildAction").asText());
        assertEquals(64001L, request.path("rebuildSourceProductMasterId").asLong());
        JsonNode listingDraft = request.path("rebuildListingDraft");
        assertEquals("PRODUCT_REBUILD", listingDraft.path("sourceType").asText());
        assertEquals(64001L, listingDraft.path("sourceRefId").asLong());
        assertEquals("MILKYWAYA17", listingDraft.path("psku").asText());
        assertEquals("2026-03-12 00:00:00", listingDraft.path("inheritedListingStartedAt").asText());
        assertEquals("pv", listingDraft.path("inheritedListingStartedSource").asText());
        assertEquals("商品重建已提交后台处理：系统会先删除 Noon 旧商品，确认删除后按当前本地数据重新上架。", actual.getMessage());
        assertEquals("product-rebuild", item.getLastPublishTask().get("taskType"));
        assertEquals("重建中", item.getLastPublishTask().get("statusLabel"));
        verify(productManagementMapper, never()).markProductMasterDeletedById(any(), any());
    }

    @Test
    void productDeleteShouldUseStoreScopeSoSamePartnerSkuInOtherStoreDoesNotMatch() {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        ProductReadModelService readModelService = mock(ProductReadModelService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                publishCommandService,
                readModelService,
                null,
                null,
                null,
                null
        );
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR-A");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR-A")).thenReturn(store);
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR-A",
                "SAME-PARTNER"
        )).thenReturn(productIdentity(64001L, 50001L, "STR-A", "AE", "ZA", "SAME-PARTNER", null));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setTaskType("product-delete");
        task.setStatus("queued");
        task.setSkuParent("ZA");
        task.setPartnerSku("SAME-PARTNER");
        when(publishCommandService.createProductDeleteTask(any(ProductPublishTaskCreateCommand.class)))
                .thenReturn(ProductPublishCommandService.ProductPublishTaskCreateResult.created(task));
        ProductListDatasetView expected = new ProductListDatasetView();
        LocalDbStoreInitializationService.StoreInitializationProductListItemView storeAItem =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        storeAItem.setSkuParent("ZA");
        storeAItem.setPartnerSku("SAME-PARTNER");
        expected.setItems(java.util.List.of(storeAItem));
        when(readModelService.loadListDataset(any(ProductMasterFetchCommand.class))).thenReturn(expected);

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR-A");
        command.setPartnerSku("SAME-PARTNER");

        service.deleteProduct(command);

        verify(productManagementMapper).selectProductMasterIdentityByStorePartnerSku(10002L, "STR-A", "SAME-PARTNER");
        verify(productManagementMapper, never()).selectProductMasterIdentityByStorePartnerSku(10002L, "STR-B", "SAME-PARTNER");
    }

    @Test
    void productDeleteCompletionShouldWriteActionLogWhenLocalProductWasAlreadyDeleted() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                null,
                null,
                objectMapper,
                null,
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
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(64001L);
        task.setStoreCode("STR245027-NAE");
        task.setSkuParent("MILKYWAYA17");
        task.setPskuCode("PSKU-1");
        when(productManagementMapper.markProductMasterDeletedById(64001L, 10002L)).thenReturn(0);
        when(productManagementMapper.selectProductActionLogIdByIdempotency("product-delete-task:77001")).thenReturn(null);
        when(productManagementMapper.nextProductActionLogId()).thenReturn(58366L);

        invokeDeleteLocalProductAfterNoonDelete(service, task);

        ArgumentCaptor<String> idempotencyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
        verify(productManagementMapper).insertProductActionLog(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                idempotencyCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                summaryCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertEquals("product-delete-task:77001", idempotencyCaptor.getValue());
        assertTrue(summaryCaptor.getValue().contains("Noon 删除已确认，本地商品目录此前已清理。"));
    }

    @Test
    void publishCurrentIdempotencyKeyShouldUseStoreSitePartnerSkuInsteadOfProductMasterId() throws Exception {
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class),
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(ProductPublishCommandService.class),
                null,
                null,
                null,
                null,
                null
        );
        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("ZOLDPSKU001");
        command.setPartnerSku("SGGRB113");
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "ZNEWPSKU001");
        snapshot.getIdentity().put("partnerSku", "SGGRB113");
        snapshot.getIdentity().put("pskuCode", "new-noon-psku-code");

        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "publishCurrentTaskIdempotencyKey",
                ProductMasterActionCommand.class,
                ProductMasterSnapshotView.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        String idempotencyKey = (String) method.invoke(service, command, snapshot, "AE", "draft-hash");

        assertEquals("publish:10002:STR245027-NAE:AE:SGGRB113:draft-hash", idempotencyKey);
    }

    @Test
    void productDeleteBackgroundTaskShouldBeRecognizedFromDurableTaskPayload() throws Exception {
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class),
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(ProductPublishCommandService.class),
                null,
                null,
                null,
                null,
                null
        );
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setRequestJson("{\"action\":\"product-delete\"}");
        task.setChangedDomainsJson("[\"delete\"]");
        task.setDraftJson("{\"mode\":\"product-delete-task\"}");

        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "isProductDeleteBackgroundTask",
                ProductPublishTaskRecord.class
        );
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(service, task));
    }

    @Test
    void productDeleteTaskSnapshotMustNotRunThroughPublishNoopPathEvenWhenTaskTypeIsMissing() throws Exception {
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                mock(ProductManagementMapper.class),
                null,
                null,
                null,
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
        ProductPublishTaskRecord task = productDeleteTask();
        task.setTaskType(null);
        task.setRequestJson(null);
        task.setChangedDomainsJson(null);

        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "executeQueuedPublishTask",
                ProductPublishTaskRecord.class
        );
        method.setAccessible(true);

        InvocationTargetException exception = assertInstanceOf(
                InvocationTargetException.class,
                org.junit.jupiter.api.Assertions.assertThrows(InvocationTargetException.class, () -> method.invoke(service, task))
        );
        assertTrue(exception.getCause().getMessage().contains("product-delete task must not run through publish-current execution"));
        verify(publishCommandService, never()).updateStatus(
                any(),
                eq("synced"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void productDeleteTaskShouldSkipUnmapAndCallNoonDeleteBeforeCleaningLocalWhenSkuParentAlreadyInvalid() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        LocalDbBootstrapStatusService bootstrapStatusService = mock(LocalDbBootstrapStatusService.class);
        ProductNoonAdapter productNoonAdapter = mock(ProductNoonAdapter.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        NoonSessionGateway noonSessionGateway = testNoonSessionGateway();
        NoonSession session = testNoonSession(noonSessionGateway);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                bootstrapStatusService,
                objectMapper,
                productNoonAdapter,
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
        ProductPublishTaskRecord task = productDeleteTask();
        StoreSyncStoreRecord store = productStore();
        StoreSyncOwnerContext owner = productOwner();
        when(bootstrapStatusService.inspect()).thenReturn(new CoreTableInspection(
                "nuono_new_dev",
                List.of(),
                List.of(),
                List.of()
        ));
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(storeSyncMapper.selectOwnerContext(10002L)).thenReturn(owner);
        when(storeSyncMapper.listOwnerStores(10002L)).thenReturn(List.of(store));
        when(productNoonAdapter.openRequestCountScope()).thenReturn(noonSessionGateway.openRequestCountScope());
        when(productNoonAdapter.loginWithPersistedCookie(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(session);
        when(productNoonAdapter.getJson(any(), eq(NoonProductGateway.WHOAMI_URL), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode());
        when(productNoonAdapter.postJson(any(), anyString(), any(), anyBoolean())).thenAnswer(invocation -> {
            String url = invocation.getArgument(1);
            if (NoonProductGateway.PROJECT_LIST_URL.equals(url)) {
                return objectMapper.readTree("{\"projects\":[{\"projectCode\":\"PRJ245027\"}]}");
            }
            if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                throw new IllegalStateException("请求 Noon 失败：HTTP 400 {\"error\":\"Invalid sku_parents: {'ZSTALE'}\"}");
            }
            return objectMapper.createObjectNode();
        });
        when(productNoonAdapter.postWriteJson(any(), anyString(), any(JsonNode.class), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode().put("success", true));
        when(productNoonAdapter.userMessage(any())).thenAnswer(invocation -> {
            Throwable throwable = invocation.getArgument(0);
            return throwable.getMessage();
        });
        when(productManagementMapper.markProductMasterDeletedById(64001L, 10002L)).thenReturn(1);
        when(productManagementMapper.selectProductActionLogIdByIdempotency("product-delete-task:77001")).thenReturn(null);
        when(productManagementMapper.nextProductActionLogId()).thenReturn(58440L);
        when(publishCommandService.buildTaskView(any(), anyBoolean(), any(), any())).thenReturn(new ProductPublishTaskView());

        invokeExecuteProductDeleteTask(service, task, "queued");

        InOrder inOrder = inOrder(productNoonAdapter, productManagementMapper);
        inOrder.verify(productNoonAdapter).postWriteJson(
                any(),
                eq(NoonProductGateway.PSKU_DELETE_URL),
                argThat((JsonNode body) -> "PSKU-CURRENT".equals(body.path("pskuDelete").path(0).path("pskuCode").asText())),
                eq(true)
        );
        inOrder.verify(productManagementMapper).markProductMasterDeletedById(64001L, 10002L);
        verify(productNoonAdapter, never()).postWriteJson(
                any(),
                eq(NoonProductGateway.PSKU_UNMAP_URL),
                any(JsonNode.class),
                eq(true)
        );
    }

    @Test
    void productDeleteTaskShouldNotCleanLocalWhenNoonOfferListStillContainsPskuAfterDelete() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        LocalDbBootstrapStatusService bootstrapStatusService = mock(LocalDbBootstrapStatusService.class);
        ProductNoonAdapter productNoonAdapter = mock(ProductNoonAdapter.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        NoonSessionGateway noonSessionGateway = testNoonSessionGateway();
        NoonSession session = testNoonSession(noonSessionGateway);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                bootstrapStatusService,
                objectMapper,
                productNoonAdapter,
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
        ProductPublishTaskRecord task = productDeleteTask();
        StoreSyncStoreRecord store = productStore();
        StoreSyncOwnerContext owner = productOwner();
        when(bootstrapStatusService.inspect()).thenReturn(new CoreTableInspection(
                "nuono_new_dev",
                List.of(),
                List.of(),
                List.of()
        ));
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(storeSyncMapper.selectOwnerContext(10002L)).thenReturn(owner);
        when(storeSyncMapper.listOwnerStores(10002L)).thenReturn(List.of(store));
        when(productNoonAdapter.openRequestCountScope()).thenReturn(noonSessionGateway.openRequestCountScope());
        when(productNoonAdapter.loginWithPersistedCookie(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(session);
        when(productNoonAdapter.getJson(any(), eq(NoonProductGateway.WHOAMI_URL), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode());
        when(productNoonAdapter.postJson(any(), anyString(), any(), anyBoolean())).thenAnswer(invocation -> {
            String url = invocation.getArgument(1);
            if (NoonProductGateway.PROJECT_LIST_URL.equals(url)) {
                return objectMapper.readTree("{\"projects\":[{\"projectCode\":\"PRJ245027\"}]}");
            }
            if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                throw new IllegalStateException("请求 Noon 失败：HTTP 400 {\"error\":\"Invalid sku_parents: {'ZSTALE'}\"}");
            }
            if (NoonProductGateway.STORE_LIST_URL.equals(url)) {
                return objectMapper.readTree("{\"noonStores\":[{\"noonStoreCode\":\"STR245027-NAE\",\"countryCode\":\"AE\"}]}");
            }
            if (NoonProductGateway.OFFER_LIST_NOON_URL.equals(url)) {
                return objectMapper.readTree("{\"data\":{\"total\":1,\"hits\":[{\"psku_code\":\"PSKU-CURRENT\",\"partner_sku\":\"MILKYWAYA17\",\"zsku_parent\":\"ZSTALE\"}]}}");
            }
            return objectMapper.createObjectNode();
        });
        when(productNoonAdapter.postWriteJson(any(), anyString(), any(JsonNode.class), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode().put("success", true));
        when(productNoonAdapter.userMessage(any())).thenAnswer(invocation -> {
            Throwable throwable = invocation.getArgument(0);
            return throwable.getMessage();
        });
        when(publishCommandService.buildTaskView(any(), anyBoolean(), any(), any())).thenReturn(new ProductPublishTaskView());

        invokeExecuteProductDeleteTask(service, task, "queued");

        verify(productNoonAdapter).postWriteJson(
                any(),
                eq(NoonProductGateway.PSKU_DELETE_URL),
                argThat((JsonNode body) -> "PSKU-CURRENT".equals(body.path("pskuDelete").path(0).path("pskuCode").asText())),
                eq(true)
        );
        verify(productNoonAdapter, atLeastOnce()).postJson(
                any(),
                eq(NoonProductGateway.OFFER_LIST_NOON_URL),
                argThat((JsonNode body) -> "STR245027-NAE".equals(body.path("noon_store_code").asText())
                        && "noon".equals(body.path("noonChannelType").asText())),
                eq(true)
        );
        verify(productManagementMapper, never()).markProductMasterDeletedById(any(), any());
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(publishCommandService, atLeastOnce()).updateStatus(
                eq(task),
                statusCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertTrue(statusCaptor.getAllValues().contains(ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE));
    }

    @Test
    void productDeleteTaskShouldDeleteCurrentNoonPskuCodeResolvedByPartnerSkuWhenTaskPskuCodeIsStale() throws Exception {
        ProductManagementMapper productManagementMapper = mock(ProductManagementMapper.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        LocalDbBootstrapStatusService bootstrapStatusService = mock(LocalDbBootstrapStatusService.class);
        ProductNoonAdapter productNoonAdapter = mock(ProductNoonAdapter.class);
        ProductPublishCommandService publishCommandService = mock(ProductPublishCommandService.class);
        NoonSessionGateway noonSessionGateway = testNoonSessionGateway();
        NoonSession session = testNoonSession(noonSessionGateway);
        LocalDbProductMasterService service = new LocalDbProductMasterService(
                productManagementMapper,
                null,
                null,
                storeSyncMapper,
                bootstrapStatusService,
                objectMapper,
                productNoonAdapter,
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
        ProductPublishTaskRecord task = productDeleteTask();
        task.setPskuCode("PSKU-STALE");
        StoreSyncStoreRecord store = productStore();
        StoreSyncOwnerContext owner = productOwner();
        AtomicInteger offerListCalls = new AtomicInteger();
        when(bootstrapStatusService.inspect()).thenReturn(new CoreTableInspection(
                "nuono_new_dev",
                List.of(),
                List.of(),
                List.of()
        ));
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(storeSyncMapper.selectOwnerContext(10002L)).thenReturn(owner);
        when(storeSyncMapper.listOwnerStores(10002L)).thenReturn(List.of(store));
        when(productNoonAdapter.openRequestCountScope()).thenReturn(noonSessionGateway.openRequestCountScope());
        when(productNoonAdapter.loginWithPersistedCookie(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(session);
        when(productNoonAdapter.getJson(any(), eq(NoonProductGateway.WHOAMI_URL), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode());
        when(productNoonAdapter.postJson(any(), anyString(), any(), anyBoolean())).thenAnswer(invocation -> {
            String url = invocation.getArgument(1);
            if (NoonProductGateway.PROJECT_LIST_URL.equals(url)) {
                return objectMapper.readTree("{\"projects\":[{\"projectCode\":\"PRJ245027\"}]}");
            }
            if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                throw new IllegalStateException("请求 Noon 失败：HTTP 400 {\"error\":\"Invalid sku_parents: {'ZSTALE'}\"}");
            }
            if (NoonProductGateway.STORE_LIST_URL.equals(url)) {
                return objectMapper.readTree("{\"noonStores\":[{\"noonStoreCode\":\"STR245027-NAE\",\"countryCode\":\"AE\"}]}");
            }
            if (NoonProductGateway.OFFER_LIST_NOON_URL.equals(url)) {
                if (offerListCalls.incrementAndGet() == 1) {
                    return objectMapper.readTree("{\"data\":{\"total\":1,\"hits\":[{\"psku_code\":\"PSKU-CURRENT\",\"partner_sku\":\"MILKYWAYA17\",\"zsku_parent\":\"ZCURRENT\"}]}}");
                }
                return objectMapper.readTree("{\"data\":{\"total\":0,\"hits\":[]}}");
            }
            return objectMapper.createObjectNode();
        });
        when(productNoonAdapter.postWriteJson(any(), anyString(), any(JsonNode.class), anyBoolean()))
                .thenReturn(objectMapper.createObjectNode().put("success", true));
        when(productNoonAdapter.userMessage(any())).thenAnswer(invocation -> {
            Throwable throwable = invocation.getArgument(0);
            return throwable.getMessage();
        });
        when(productManagementMapper.markProductMasterDeletedById(64001L, 10002L)).thenReturn(1);
        when(productManagementMapper.selectProductActionLogIdByIdempotency("product-delete-task:77001")).thenReturn(null);
        when(productManagementMapper.nextProductActionLogId()).thenReturn(58441L);
        when(publishCommandService.buildTaskView(any(), anyBoolean(), any(), any())).thenReturn(new ProductPublishTaskView());

        invokeExecuteProductDeleteTask(
                service,
                task,
                ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE
        );

        InOrder inOrder = inOrder(productNoonAdapter, productManagementMapper);
        inOrder.verify(productNoonAdapter).postWriteJson(
                any(),
                eq(NoonProductGateway.PSKU_DELETE_URL),
                argThat((JsonNode body) -> "PSKU-STALE".equals(body.path("pskuDelete").path(0).path("pskuCode").asText())),
                eq(true)
        );
        inOrder.verify(productNoonAdapter).postWriteJson(
                any(),
                eq(NoonProductGateway.PSKU_DELETE_URL),
                argThat((JsonNode body) -> "PSKU-CURRENT".equals(body.path("pskuDelete").path(0).path("pskuCode").asText())),
                eq(true)
        );
        inOrder.verify(productManagementMapper).markProductMasterDeletedById(64001L, 10002L);
    }

    private void invokeExecuteProductDeleteTask(
            LocalDbProductMasterService service,
            ProductPublishTaskRecord task,
            String previousStatus
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "executeProductDeleteTask",
                ProductPublishTaskRecord.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(service, task, previousStatus);
    }

    private void invokeDeleteLocalProductAfterNoonDelete(
            LocalDbProductMasterService service,
            ProductPublishTaskRecord task
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "deleteLocalProductAfterNoonDelete",
                ProductPublishTaskRecord.class,
                ProductMasterSnapshotView.class,
                java.util.List.class
        );
        method.setAccessible(true);
        method.invoke(service, task, null, new java.util.ArrayList<String>());
    }

    private ProductPublishTaskRecord productDeleteTask() throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("product-delete-task");
        snapshot.setReady(true);
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getStoreContext().put("projectCode", "PRJ245027");
        snapshot.getIdentity().put("skuParent", "ZSTALE");
        snapshot.getIdentity().put("partnerSku", "MILKYWAYA17");
        snapshot.getIdentity().put("pskuCode", "PSKU-CURRENT");

        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(77001L);
        task.setOwnerUserId(10002L);
        task.setProductMasterId(64001L);
        task.setStoreCode("STR245027-NAE");
        task.setProjectCode("PRJ245027");
        task.setSkuParent("ZSTALE");
        task.setPartnerSku("MILKYWAYA17");
        task.setPskuCode("PSKU-CURRENT");
        task.setCurrentSiteCode("AE");
        task.setTaskType("product-delete");
        task.setStatus("running");
        task.setDraftHash("delete-hash");
        task.setChangedDomainsJson("[\"delete\"]");
        task.setRequestJson("{\"action\":\"product-delete\"}");
        task.setBaselineJson(objectMapper.writeValueAsString(snapshot));
        task.setDraftJson(objectMapper.writeValueAsString(snapshot));
        task.setRetryCount(0);
        task.setVerifyAttemptCount(0);
        task.setVersionNo(1);
        return task;
    }

    private static StoreSyncStoreRecord productStore() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STR245027-NAE");
        store.setSite("AE");
        store.setProjectCode("PRJ245027");
        store.setProjectName("xingyao");
        store.setNoonPartnerProjectUser("nuonuo@example.test");
        store.setNoonPartnerPwd("password");
        return store;
    }

    private static ProductMasterSnapshotView productRebuildSnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getIdentity().put("partnerSku", "MILKYWAYA17");
        snapshot.getIdentity().put("brand", "Generic");
        snapshot.getIdentity().put("brandCode", "generic");
        snapshot.getTaxonomy().put("idProductFullType", 3066);
        snapshot.getTaxonomy().put("productFulltype", "electronic_accessories-headphones-wired_headphones");
        snapshot.getTaxonomy().put("family", "electronic_accessories");
        snapshot.getTaxonomy().put("productType", "headphones");
        snapshot.getTaxonomy().put("productSubtype", "wired_headphones");
        snapshot.getContent().put("titleCn", "本地中文标题");
        snapshot.getContent().put("titleEn", "Wired headphones with microphone");
        snapshot.getContent().put("titleAr", "Arabic wired headphones title");
        snapshot.getContent().put("descriptionEn", "English description");
        snapshot.getContent().put("highlightsEn", List.of("Long cable", "Clear voice"));
        snapshot.getContent().put("images", List.of("https://example.test/image-1.jpg"));
        snapshot.setKeyAttributes(List.of(Map.of(
                "code", "barcode",
                "commonValue", "6290000000001"
        )));
        snapshot.getSiteOffers().add(Map.of(
                "storeCode", "STR245027-NAE",
                "price", "49.90",
                "idWarranty", 24,
                "isActive", true,
                "listingStartedAt", "2026-03-12 00:00:00",
                "listingStartedSource", "pv"
        ));
        return snapshot;
    }

    private static StoreSyncOwnerContext productOwner() {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(10002L);
        owner.setNoonPartnerProjectUser("nuonuo@example.test");
        owner.setNoonPartnerPwd("password");
        return owner;
    }

    private NoonSessionGateway testNoonSessionGateway() {
        return new NoonSessionGateway(
                objectMapper,
                null,
                false,
                0,
                true,
                "",
                "",
                "en-sa",
                "en",
                false,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                "HTTP",
                "",
                0,
                ""
        );
    }

    private NoonSession testNoonSession(NoonSessionGateway gateway) throws Exception {
        Class<?> stateClass = Class.forName("com.nuono.next.noon.NoonSessionGateway$AuthSessionState");
        Constructor<NoonSession> constructor = NoonSession.class.getDeclaredConstructor(
                NoonSessionGateway.class,
                Long.class,
                String.class,
                String.class,
                stateClass,
                String.class,
                String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(gateway, 307L, "nuonuo@example.test", "password", null, "PRJ245027", "STR245027-NAE");
    }

    private static ProductMasterIdentityRecord productIdentity(
            Long productMasterId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        ProductMasterIdentityRecord identity = new ProductMasterIdentityRecord();
        identity.setProductMasterId(productMasterId);
        identity.setLogicalStoreId(logicalStoreId);
        identity.setStoreCode(storeCode);
        identity.setSiteCode(siteCode);
        identity.setSkuParent(skuParent);
        identity.setPartnerSku(partnerSku);
        identity.setPskuCode(pskuCode);
        identity.setProductSourceType("SELF_BUILT");
        return identity;
    }
}

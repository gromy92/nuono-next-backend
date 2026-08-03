package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreInitializationSnapshotMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.product.ProductNoonCatalogContentService;
import com.nuono.next.product.ProductListSummaryView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbStoreInitializationServiceTest {

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private StoreInitializationSnapshotMapper storeInitializationSnapshotMapper;

    @Mock
    private LocalDbBootstrapStatusService bootstrapStatusService;

    @Mock
    private NoonAccountTaskQueue noonAccountTaskQueue;

    private NoonSessionGateway noonSessionGateway;

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    @Mock
    private ProductNoonCatalogContentService productNoonCatalogContentService;

    private ObjectMapper objectMapper;
    private LocalDbStoreInitializationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        noonSessionGateway = spy(new NoonSessionGateway(
                objectMapper,
                storeSyncMapper,
                0L,
                true,
                "",
                "",
                "en-sa",
                "en",
                false,
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
        ));
        service = new LocalDbStoreInitializationService(
                storeSyncMapper,
                storeInitializationSnapshotMapper,
                bootstrapStatusService,
                objectMapper,
                noonAccountTaskQueue,
                noonSessionGateway,
                productProjectionPersistenceService,
                productNoonCatalogContentService
        );
    }

    @Test
    void preflightUsesReferenceProjectCookieWithoutInteractiveCredentials() {
        StoreSyncOwnerContext owner = ownerContext();
        StoreSyncStoreRecord referenceStore = store(
                51004L,
                "xingyao",
                "STR245027-NAE",
                "AE",
                "PRJ245027",
                "xingyao-project-user",
                "xingyao-cookie"
        );
        StoreSyncStoreRecord siblingStore = store(
                51005L,
                "xingyao",
                "STR245027-NSA",
                "SA",
                "PRJ245027",
                "xingyao-project-user",
                "xingyao-cookie"
        );

        when(bootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuonuoai", List.of("user"), List.of("user"), List.of())
        );
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        when(storeSyncMapper.selectOwnerStore(307L, "PRJ245027")).thenReturn(referenceStore);
        when(storeSyncMapper.listOwnerStores(307L)).thenReturn(List.of(referenceStore, siblingStore));
        doThrow(new IllegalStateException("stop after credential capture"))
                .when(noonSessionGateway)
                .loginWithPersistedCookie(
                        nullable(Long.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()
                );
        LocalDbStoreInitializationService.StoreInitializationCommand command =
                new LocalDbStoreInitializationService.StoreInitializationCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("PRJ245027");

        service.preflight(command);

        verify(noonSessionGateway).loginWithPersistedCookie(
                eq(307L),
                eq("xingyao-project-user"),
                eq("xingyao-cookie"),
                eq("PRJ245027"),
                eq("STR245027-NAE")
        );
    }

    @Test
    void statusOverlayCarriesCurrentZCodeFromProjectionSummary() throws Exception {
        StoreSyncOwnerContext owner = ownerContext();
        StoreSyncStoreRecord referenceStore = store(
                51004L,
                "xingyao",
                "STR245027-NAE",
                "AE",
                "PRJ245027",
                "xingyao-project-user",
                "xingyao-cookie"
        );
        LocalDbStoreInitializationService.StoreInitializationStatusView persisted =
                new LocalDbStoreInitializationService.StoreInitializationStatusView();
        persisted.setStatus("READY");
        persisted.setProductItems(List.of());
        StoreInitializationSnapshotRecord snapshotRecord = new StoreInitializationSnapshotRecord();
        snapshotRecord.setSnapshotJson(objectMapper.writeValueAsString(persisted));

        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setSkuParent("ZNEW001");
        summary.setCurrentZCode("ZNEW001");
        summary.setPartnerSku("SGGRB113");
        summary.setProductSourceType("noon");

        when(bootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuonuoai", List.of("user"), List.of("user"), List.of())
        );
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        when(storeSyncMapper.selectOwnerStore(307L, "STR245027-NAE")).thenReturn(referenceStore);
        when(storeSyncMapper.listOwnerStores(307L)).thenReturn(List.of(referenceStore));
        when(storeInitializationSnapshotMapper.selectByOwnerAndStore(307L, "STR245027-NAE"))
                .thenReturn(snapshotRecord);
        when(productProjectionPersistenceService.loadProductListSummaries(
                eq(307L),
                eq("STR245027-NAE"),
                anyList()
        )).thenReturn(List.of(summary));

        LocalDbStoreInitializationService.StoreInitializationStatusView view =
                service.getStatus(307L, "STR245027-NAE");

        assertEquals(1, view.getProductItems().size());
        assertEquals("ZNEW001", view.getProductItems().get(0).getSkuParent());
        assertEquals("ZNEW001", view.getProductItems().get(0).getCurrentZCode());
        assertEquals("SGGRB113", view.getProductItems().get(0).getPartnerSku());
    }

    @Test
    void missingProjectCookieWaitsAndPreservesExactInitializationTaskForAutomaticResume() {
        StoreSyncOwnerContext owner = ownerContext();
        StoreSyncStoreRecord referenceStore = store(
                51004L,
                "xingyao",
                "STR245027-NAE",
                "AE",
                "PRJ245027",
                "xingyao-project-user",
                null
        );
        when(bootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuonuoai", List.of("user"), List.of("user"), List.of())
        );
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        when(storeSyncMapper.selectOwnerStore(307L, "PRJ245027")).thenReturn(referenceStore);
        when(storeSyncMapper.listOwnerStores(307L)).thenReturn(List.of(referenceStore));
        when(storeInitializationSnapshotMapper.nextId()).thenReturn(40001L);
        List<NoonAuthWaitRequest> requests = new ArrayList<>();
        service.setAuthWaitQueue(request -> {
            requests.add(request);
            return Optional.of(91L);
        });
        LocalDbStoreInitializationService.StoreInitializationCommand command =
                new LocalDbStoreInitializationService.StoreInitializationCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("PRJ245027");

        LocalDbStoreInitializationService.StoreInitializationStatusView result = service.start(command);

        assertEquals("WAITING_AUTHORIZATION", result.getStatus());
        assertEquals(1, requests.size());
        NoonAuthWaitRequest request = requests.get(0);
        assertEquals("STORE_INITIALIZATION", request.getSourceDomain());
        assertEquals(40001L, request.getSourceTaskId());
        assertEquals("PROJECT_SESSION", request.getCheckpoint());
        assertEquals(NoonAuthResumePolicy.AUTO_RESUME, request.getResumePolicy());
    }

    private StoreSyncOwnerContext ownerContext() {
        StoreSyncOwnerContext context = new StoreSyncOwnerContext();
        context.setId(307L);
        context.setAccountNo("owner-307");
        context.setRealName("owner");
        context.setNoonPartnerProjectUser("wrong-aggregate-user");
        context.setNoonPartnerCookie("wrong-aggregate-cookie");
        return context;
    }

    private StoreSyncStoreRecord store(
            Long id,
            String projectName,
            String storeCode,
            String site,
            String projectCode,
            String projectUser,
            String cookie
    ) {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setId(id);
        record.setProjectName(projectName);
        record.setStoreCode(storeCode);
        record.setSite(site);
        record.setProjectCode(projectCode);
        record.setOwnerAuthorized(true);
        record.setNoonPartnerProjectUser(projectUser);
        record.setNoonPartnerCookie(cookie);
        return record;
    }
}

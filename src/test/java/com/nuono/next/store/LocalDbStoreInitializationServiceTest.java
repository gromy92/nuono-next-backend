package com.nuono.next.store;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.nuono.next.product.ProductNoonCatalogContentService;
import com.nuono.next.product.ProductProjectionPersistenceService;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.List;
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

    private LocalDbStoreInitializationService service;

    @BeforeEach
    void setUp() {
        noonSessionGateway = spy(new NoonSessionGateway(
                new ObjectMapper(),
                storeSyncMapper,
                false,
                0L,
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
                new ObjectMapper(),
                noonAccountTaskQueue,
                noonSessionGateway,
                productProjectionPersistenceService,
                productNoonCatalogContentService
        );
    }

    @Test
    void preflightUsesReferenceProjectCredentialsInsteadOfOwnerAggregateCredentials() {
        StoreSyncOwnerContext owner = ownerContext();
        StoreSyncStoreRecord referenceStore = store(
                51004L,
                "xingyao",
                "STR245027-NAE",
                "AE",
                "PRJ245027",
                "xingyao-project-user",
                "xingyao-password",
                "xingyao-cookie"
        );
        StoreSyncStoreRecord siblingStore = store(
                51005L,
                "xingyao",
                "STR245027-NSA",
                "SA",
                "PRJ245027",
                "xingyao-project-user",
                "xingyao-password",
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
                .login(nullable(Long.class), anyString(), anyString(), anyString(), anyString(), anyString());
        LocalDbStoreInitializationService.StoreInitializationCommand command =
                new LocalDbStoreInitializationService.StoreInitializationCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("PRJ245027");

        service.preflight(command);

        verify(noonSessionGateway).login(
                isNull(),
                eq("xingyao-project-user"),
                eq("xingyao-password"),
                eq("xingyao-cookie"),
                eq("PRJ245027"),
                eq("STR245027-NAE")
        );
    }

    private StoreSyncOwnerContext ownerContext() {
        StoreSyncOwnerContext context = new StoreSyncOwnerContext();
        context.setId(307L);
        context.setAccountNo("owner-307");
        context.setRealName("owner");
        context.setNoonPartnerProjectUser("wrong-aggregate-user");
        context.setNoonPartnerPwd("wrong-aggregate-password");
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
            String password,
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
        record.setNoonPartnerPwd(password);
        record.setNoonPartnerCookie(cookie);
        return record;
    }
}

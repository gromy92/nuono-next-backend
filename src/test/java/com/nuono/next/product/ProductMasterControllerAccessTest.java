package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonProductDetailBaselineSyncRequest;
import com.nuono.next.noonpull.NoonProductDetailBaselineSyncResult;
import com.nuono.next.noonpull.NoonProductDetailBaselineSyncer;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductMasterControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProductMasterService> productMasterServiceProvider;

    @Mock
    private ObjectProvider<ProductContentTranslationService> translationServiceProvider;

    @Mock
    private LocalDbProductMasterService productMasterService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private ObjectProvider<ProductMasterAccessGuard> productMasterAccessGuardProvider;

    @Mock
    private ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;

    @Mock
    private ObjectProvider<NoonProductDetailBaselineSyncer> productDetailBaselineSyncerProvider;

    @Mock
    private NoonProductDetailBaselineSyncer productDetailBaselineSyncer;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductMasterController controller;

    @BeforeEach
    void setUp() {
        ProductMasterAccessGuard accessGuard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
        lenient().when(productMasterAccessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        lenient().when(businessAccessResolverProvider.getIfAvailable()).thenReturn(businessAccessResolver);
        controller = new ProductMasterController(
                productMasterServiceProvider,
                translationServiceProvider,
                sessionTokenService,
                productMasterAccessGuardProvider,
                businessAccessResolverProvider,
                productDetailBaselineSyncerProvider
        );
    }

    @Test
    void shouldOverwriteRequestOwnerWithAuthorizedOwnerBeforeOpeningWorkbench() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenReturn(productContext(10003L, 10002L, "STR245027-NAE"));
        when(productMasterService.openWorkbench(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductMasterWorkbenchView());

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        controller.open(command, request);

        ArgumentCaptor<ProductMasterFetchCommand> captor = ArgumentCaptor.forClass(ProductMasterFetchCommand.class);
        verify(productMasterService).openWorkbench(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
    }

    @Test
    void shouldRejectStoreDeniedByBusinessAccessBeforeCallingService() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.open(command, request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterService, never()).openWorkbench(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldResolvePublishTaskOwnerFromTaskInsteadOfTrustingQueryOwner() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");

        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(productManagementMapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);
        when(productMasterService.loadPublishTask(64001L, 10002L)).thenReturn(new ProductPublishTaskView());

        controller.publishTask(64001L, 10002L, request);

        verify(productMasterService).loadPublishTask(64001L, 10002L);
    }

    @Test
    void shouldResolveOwnerAndClampBatchBeforeSyncingMissingDetailBaselines() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(productDetailBaselineSyncerProvider.getIfAvailable()).thenReturn(productDetailBaselineSyncer);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenReturn(productContext(10003L, 10002L, "STR245027-NAE"));
        NoonProductDetailBaselineSyncResult syncResult = new NoonProductDetailBaselineSyncResult();
        syncResult.setAttemptedCount(50);
        syncResult.setRemainingCount(3);
        when(productDetailBaselineSyncer.sync(org.mockito.ArgumentMatchers.any())).thenReturn(syncResult);

        NoonProductDetailBaselineSyncRequest command = new NoonProductDetailBaselineSyncRequest();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSiteCode("AE");
        command.setMaxDetailFetches(999);
        command.setResumePosition("BEBI029");

        NoonProductDetailBaselineSyncResult result = controller.syncMissingDetailBaselines(command, request);

        ArgumentCaptor<NoonProductDetailBaselineSyncRequest> captor =
                ArgumentCaptor.forClass(NoonProductDetailBaselineSyncRequest.class);
        verify(productDetailBaselineSyncer).sync(captor.capture());
        assertEquals(50, result.getAttemptedCount());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-NAE", captor.getValue().getStoreCode());
        assertEquals("AE", captor.getValue().getSiteCode());
        assertEquals(50, captor.getValue().getMaxDetailFetches());
        assertEquals("BEBI029", captor.getValue().getResumePosition());
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        lenient().when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }

    private BusinessAccessContext productContext(Long sessionUserId, Long ownerUserId, String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(sessionUserId)
                .businessOwnerUserId(ownerUserId)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of(storeCode))
                .storeOwnerUserIds(Map.of(storeCode, ownerUserId))
                .menuPaths(Set.of("/api/sku/manage"))
                .build();
    }
}

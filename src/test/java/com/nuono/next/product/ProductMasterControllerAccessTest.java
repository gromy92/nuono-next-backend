package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.mock.web.MockMultipartFile;
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
    private ProductContentTranslationService translationService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private ObjectProvider<ProductMasterAccessGuard> productMasterAccessGuardProvider;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductMasterController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductMasterController(
                productMasterServiceProvider,
                translationServiceProvider,
                sessionTokenService,
                productMasterAccessGuardProvider,
                businessAccessResolver
        );
    }

    @Test
    void shouldOverwriteRequestOwnerWithAuthorizedOwnerBeforeOpeningWorkbench() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(productMasterService.openWorkbench(any()))
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
    void shouldNotCallServiceWhenSharedResolverRejectsProductAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.open(command, request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterService, never()).openWorkbench(any());
    }

    @Test
    void shouldRequireAccessBeforeReturningBootstrapOpenResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.open(command, request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterServiceProvider, never()).getIfAvailable();
    }

    @Test
    void shouldResolvePublishTaskOwnerFromTaskInsteadOfTrustingQueryOwner() {
        ProductMasterAccessGuard accessGuard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
        when(productMasterAccessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");

        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenReturn(productContext(307L, 10002L));
        when(productManagementMapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);
        when(productMasterService.loadPublishTask(64001L, 10002L)).thenReturn(new ProductPublishTaskView());

        controller.publishTask(64001L, 99999L, request);

        verify(productMasterService).loadPublishTask(64001L, 10002L);
    }

    @Test
    void shouldVerifyPublishTaskStoreAccessBeforeServiceAvailabilityCheck() {
        ProductMasterAccessGuard accessGuard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
        when(productMasterAccessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenReturn(productContext());
        when(productManagementMapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.publishTask(64001L, 99999L, request)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verify(productManagementMapper).selectProductPublishTaskById(64001L);
        verify(storeSyncMapper).selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE");
    }

    @Test
    void shouldResolveRetryAndCancelPublishTaskOwnerFromTask() {
        ProductMasterAccessGuard accessGuard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
        when(productMasterAccessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");

        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenReturn(productContext(307L, 10002L));
        when(productManagementMapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        controller.retryPublishTask(64001L, 99999L, request);
        controller.cancelPublishTask(64001L, 99999L, request);

        verify(productMasterService).retryPublishTask(64001L, 10002L);
        verify(productMasterService).cancelPublishTask(64001L, 10002L);
    }

    @Test
    void shouldOverwriteRequestOwnerBeforeLoadingClassificationOptions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(productMasterService.loadClassificationOptions(any()))
                .thenReturn(new ProductClassificationOptionsView());

        ProductClassificationOptionsCommand command = new ProductClassificationOptionsCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");

        controller.classificationOptions(command, request);

        ArgumentCaptor<ProductClassificationOptionsCommand> captor =
                ArgumentCaptor.forClass(ProductClassificationOptionsCommand.class);
        verify(productMasterService).loadClassificationOptions(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
    }

    @Test
    void shouldOverwriteRequestOwnerBeforeApplyingAction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(productMasterService.applyAction(any()))
                .thenReturn(new ProductMasterWorkbenchView());

        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");
        command.setAction("SAVE_DRAFT");
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setStoreContext(Map.of(
                "ownerUserId", 99999L,
                "projectCode", "STR-OTHER",
                "storeCode", "STR-OTHER"
        ));
        snapshot.setIdentity(Map.of("skuParent", "OTHER-SKU"));
        command.setSnapshot(snapshot);

        controller.action(command, request);

        ArgumentCaptor<ProductMasterActionCommand> captor = ArgumentCaptor.forClass(ProductMasterActionCommand.class);
        verify(productMasterService).applyAction(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(10002L, captor.getValue().getSnapshot().getStoreContext().get("ownerUserId"));
        assertEquals("STR245027-NAE", captor.getValue().getSnapshot().getStoreContext().get("projectCode"));
        assertEquals("STR245027-NAE", captor.getValue().getSnapshot().getStoreContext().get("storeCode"));
        assertEquals("MILKYWAYA17", captor.getValue().getSnapshot().getIdentity().get("skuParent"));
    }

    @Test
    void shouldRejectPublishCurrentWhenCurrentSiteIsNotAuthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR-OTHER"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setCurrentSiteCode("STR-OTHER");
        command.setSkuParent("MILKYWAYA17");
        command.setAction("publish-current");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.action(command, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterService, never()).applyAction(any());
    }

    @Test
    void shouldRejectPublishCurrentWhenCurrentSiteBelongsToDifferentOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR-OTHER"))
                .thenReturn(productContext(20002L, 20002L, "STR-OTHER"));

        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setCurrentSiteCode("STR-OTHER");
        command.setSkuParent("MILKYWAYA17");
        command.setAction("publish-current");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.action(command, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterService, never()).applyAction(any());
    }

    @Test
    void shouldUseSessionUserAsTranslateOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(translationServiceProvider.getIfAvailable()).thenReturn(translationService);
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenReturn(productContext());
        when(translationService.translate(any()))
                .thenReturn(new ProductContentTranslateView());

        ProductContentTranslateCommand command = new ProductContentTranslateCommand();
        command.setOperatorUserId(99999L);
        command.setText("milk powder");

        controller.translate(command, request);

        ArgumentCaptor<ProductContentTranslateCommand> captor =
                ArgumentCaptor.forClass(ProductContentTranslateCommand.class);
        verify(translationService).translate(captor.capture());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
    }

    @Test
    void shouldRequireBusinessContextBeforeTranslationServiceAvailabilityCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.translate(new ProductContentTranslateCommand(), request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(translationServiceProvider, never()).getIfAvailable();
    }

    @Test
    void shouldPersistUploadedImageWithAuthorizedOwner() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        byte[] content = imageContent();
        MockMultipartFile file = imageFile(content);
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenReturn(productContext());
        when(productMasterService.persistUploadedImageAsset(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("MILKYWAYA17"),
                anyString(),
                anyString(),
                eq("original.png"),
                eq("image/png"),
                eq((long) content.length),
                anyString(),
                anyList()
        )).thenReturn(70001L);

        Map<String, Object> response = null;
        try {
            response = controller.uploadImageAsset(
                    file,
                    99999L,
                    "STR245027-NAE",
                    "MILKYWAYA17",
                    request
            );

            verify(productMasterService).persistUploadedImageAsset(
                    eq(10002L),
                    eq("STR245027-NAE"),
                    eq("MILKYWAYA17"),
                    anyString(),
                    anyString(),
                    eq("original.png"),
                    eq("image/png"),
                    eq((long) content.length),
                    anyString(),
                    anyList()
            );
            assertEquals(70001L, response.get("assetId"));
        } finally {
            if (response != null) {
                Path uploaded = ProductImageAssetFileSupport.productImageUploadDir()
                        .resolve(String.valueOf(response.get("filename")));
                Files.deleteIfExists(uploaded);
            }
        }
    }

    @Test
    void shouldRequireBusinessContextBeforeTransientImageUploadWithoutStore() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadImageAsset(imageFile(imageContent()), null, null, null, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER);
    }

    @Test
    void shouldRequireStoreAccessBeforeImageUploadEvenWhenSkuParentIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "STR245027-NAE"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadImageAsset(imageFile(imageContent()), 99999L, "STR245027-NAE", null, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(productMasterService, never()).persistUploadedImageAsset(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString(),
                anyList()
        );
    }

    private BusinessAccessContext productContext() {
        return productContext(10002L, 10002L);
    }

    private BusinessAccessContext productContext(Long businessOwnerUserId, Long storeOwnerUserId) {
        return productContext(businessOwnerUserId, storeOwnerUserId, "STR245027-NAE");
    }

    private BusinessAccessContext productContext(Long businessOwnerUserId, Long storeOwnerUserId, String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(businessOwnerUserId)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of(storeCode))
                .storeOwnerUserIds(Map.of(storeCode, storeOwnerUserId))
                .menuPaths(Set.of("/product/manage"))
                .build();
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }

    private byte[] imageContent() {
        return "image-content".getBytes(StandardCharsets.UTF_8);
    }

    private MockMultipartFile imageFile(byte[] content) {
        return new MockMultipartFile(
                "file",
                "original.png",
                "image/png",
                content
        );
    }
}

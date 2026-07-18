package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductImageProfileControllerTest {

    @Mock
    private ObjectProvider<ProductImageProfileService> serviceProvider;
    @Mock
    private ObjectProvider<ProductImageMetadataService> metadataServiceProvider;
    @Mock
    private ObjectProvider<ProductMasterAccessGuard> accessGuardProvider;
    @Mock
    private ProductImageProfileService service;
    @Mock
    private ProductImageMetadataService metadataService;
    @Mock
    private ProductMasterAccessGuard accessGuard;
    @Mock
    private AuthSessionTokenService sessionTokenService;

    private ProductImageProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductImageProfileController(
                serviceProvider,
                metadataServiceProvider,
                sessionTokenService,
                accessGuardProvider
        );
    }

    @Test
    void shouldExposeStandaloneProductImagesRoute() {
        RequestMapping mapping = ProductImageProfileController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/product-images"}, mapping.value());
    }

    @Test
    void listShouldResolveOwnerFromSessionAndStoreScope() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.list(any())).thenReturn(new ProductImageProfileListView());

        controller.list(99999L, "STR108065-NAE", "PAPERSAY", request);

        ArgumentCaptor<ProductImageProfileListCommand> captor = ArgumentCaptor.forClass(ProductImageProfileListCommand.class);
        verify(service).list(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals("STR108065-NAE", captor.getValue().getStoreCode());
        assertEquals("PAPERSAY", captor.getValue().getKeyword());
    }

    @Test
    void profileSummariesShouldResolveOwnerFromSessionAndStoreScope() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.listSummaries(any())).thenReturn(new ProductImageProfileSummaryListView());

        controller.listSummaries(99999L, "STR108065-NAE", "PAPERSAY", request);

        ArgumentCaptor<ProductImageProfileListCommand> captor = ArgumentCaptor.forClass(ProductImageProfileListCommand.class);
        verify(service).listSummaries(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals("STR108065-NAE", captor.getValue().getStoreCode());
        assertEquals("PAPERSAY", captor.getValue().getKeyword());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
    }

    @Test
    void saveShouldResolveOwnerAndOperatorBeforeCallingService() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.save(any())).thenReturn(new ProductImageProfileDetailView());

        ProductImageProfileSaveCommand command = new ProductImageProfileSaveCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR108065-NAE");
        command.setPskuCode("PAPERSAYSB024");
        command.setProductIdentityKey("variant:53001");

        controller.save(command, request);

        ArgumentCaptor<ProductImageProfileSaveCommand> captor = ArgumentCaptor.forClass(ProductImageProfileSaveCommand.class);
        verify(service).save(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
        assertEquals("PAPERSAYSB024", captor.getValue().getPskuCode());
    }

    @Test
    void removeAssetShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.removeAsset(307L, "STR108065-NAE", 10L, 30L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.removeAsset(10L, 30L, 99999L, "STR108065-NAE", request);

        verify(service).removeAsset(307L, "STR108065-NAE", 10L, 30L, 10003L);
    }

    @Test
    void batchRemoveAssetsShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        ProductImageAssetBatchRemoveCommand command = new ProductImageAssetBatchRemoveCommand();
        when(service.removeAssets(307L, "STR108065-NAE", 10L, command, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.removeAssets(10L, 99999L, "STR108065-NAE", command, request);

        verify(service).removeAssets(307L, "STR108065-NAE", 10L, command, 10003L);
    }

    @Test
    void addAssetUsagesShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        ProductImageAssetUsageCreateCommand command = new ProductImageAssetUsageCreateCommand();
        when(service.addAssetUsages(307L, "STR108065-NAE", 10L, command, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.addAssetUsages(10L, 99999L, "STR108065-NAE", command, request);

        verify(service).addAssetUsages(307L, "STR108065-NAE", 10L, command, 10003L);
    }

    @Test
    void updateAssetUsageShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        ProductImageAssetUsageUpdateCommand command = new ProductImageAssetUsageUpdateCommand();
        when(service.updateAssetUsage(307L, "STR108065-NAE", 10L, 88L, command, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.updateAssetUsage(10L, 88L, 99999L, "STR108065-NAE", command, request);

        verify(service).updateAssetUsage(307L, "STR108065-NAE", 10L, 88L, command, 10003L);
    }

    @Test
    void removeAssetUsageShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.removeAssetUsage(307L, "STR108065-NAE", 10L, 88L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.removeAssetUsage(10L, 88L, 99999L, "STR108065-NAE", request);

        verify(service).removeAssetUsage(307L, "STR108065-NAE", 10L, 88L, 10003L);
    }

    @Test
    void importAssetUrlsShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        ProductImageAssetUrlImportCommand command = new ProductImageAssetUrlImportCommand();
        when(service.addAssetsFromUrls(307L, "STR108065-NAE", 10L, command, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.importAssetUrls(10L, 99999L, "STR108065-NAE", command, request);

        verify(service).addAssetsFromUrls(307L, "STR108065-NAE", 10L, command, 10003L);
    }

    @Test
    void assetMetadataShouldResolveOwnerAndScopeToProductImage() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(metadataServiceProvider.getIfAvailable()).thenReturn(metadataService);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(metadataService.assetMetadata(307L, "STR108065-NAE", 9001L, "https://example.test/product.jpg"))
                .thenReturn(new ProductImageAssetMetadataView());

        controller.assetMetadata(99999L, "STR108065-NAE", 9001L, "https://example.test/product.jpg", request);

        verify(metadataService).assetMetadata(307L, "STR108065-NAE", 9001L, "https://example.test/product.jpg");
    }

    @Test
    void adoptSuiteShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.adoptSuite(307L, "STR108065-NAE", 10L, 99L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.adoptSuite(10L, 99L, 99999L, "STR108065-NAE", request);

        verify(service).adoptSuite(307L, "STR108065-NAE", 10L, 99L, 10003L);
    }

    @Test
    void createAiSuiteDraftShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.createAiSuiteDraft(307L, "STR108065-NAE", 10L, null, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.createAiSuiteDraft(10L, 99999L, "STR108065-NAE", request);

        verify(service).createAiSuiteDraft(307L, "STR108065-NAE", 10L, null, 10003L);
    }

    @Test
    void approveSuiteShouldResolveOwnerAndUseSessionOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.approveSuite(307L, "STR108065-NAE", 10L, 99L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.approveSuite(10L, 99L, 99999L, "STR108065-NAE", request);

        verify(service).approveSuite(307L, "STR108065-NAE", 10L, 99L, 10003L);
    }

    @Test
    void deleteSuiteShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.deleteSuite(307L, "STR108065-NAE", 10L, 99L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.deleteSuite(10L, 99L, 99999L, "STR108065-NAE", request);

        verify(service).deleteSuite(307L, "STR108065-NAE", 10L, 99L, 10003L);
    }

    @Test
    void moveSuiteAssetShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        ProductImageSuiteAssetMoveCommand command = new ProductImageSuiteAssetMoveCommand();
        command.setTargetSuiteId(100L);
        command.setTargetIndex(1);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.moveSuiteAsset(307L, "STR108065-NAE", 10L, 99L, 501L, command, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.moveSuiteAsset(10L, 99L, 501L, 99999L, "STR108065-NAE", command, request);

        verify(service).moveSuiteAsset(307L, "STR108065-NAE", 10L, 99L, 501L, command, 10003L);
    }

    @Test
    void deleteSuiteAssetShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR108065-NAE")))
                .thenReturn(307L);
        when(service.deleteSuiteAsset(307L, "STR108065-NAE", 10L, 99L, 501L, 10003L))
                .thenReturn(new ProductImageProfileDetailView());

        controller.deleteSuiteAsset(10L, 99L, 501L, 99999L, "STR108065-NAE", request);

        verify(service).deleteSuiteAsset(307L, "STR108065-NAE", 10L, 99L, 501L, 10003L);
    }

    @Test
    void saveShouldMapValidationFailureToBadRequest() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(307L);
        when(service.save(any())).thenThrow(new IllegalArgumentException("PSKU 不能为空"));

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.save(null, request)
        );

        assertEquals(400, error.getStatus().value());
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }
}

package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
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

    private ProductMasterController controller;

    @BeforeEach
    void setUp() {
        ProductMasterAccessGuard accessGuard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
        when(productMasterAccessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        controller = new ProductMasterController(
                productMasterServiceProvider,
                translationServiceProvider,
                sessionTokenService,
                productMasterAccessGuardProvider
        );
    }

    @Test
    void shouldOverwriteRequestOwnerWithAuthorizedOwnerBeforeOpeningWorkbench() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);
        when(productMasterService.openWorkbench(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductMasterWorkbenchView());

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA17");

        controller.open(command, request);

        ArgumentCaptor<ProductMasterFetchCommand> captor = ArgumentCaptor.forClass(ProductMasterFetchCommand.class);
        verify(productMasterService).openWorkbench(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
    }

    @Test
    void shouldRejectCrossOwnerWorkbenchRequestBeforeCallingService() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(productMasterServiceProvider.getIfAvailable()).thenReturn(productMasterService);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(99999L);
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

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }
}

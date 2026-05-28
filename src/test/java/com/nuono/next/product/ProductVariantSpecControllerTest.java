package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.math.BigDecimal;
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
class ProductVariantSpecControllerTest {

    @Mock
    private ObjectProvider<ProductVariantSpecService> serviceProvider;
    @Mock
    private ObjectProvider<ProductMasterAccessGuard> accessGuardProvider;
    @Mock
    private ProductVariantSpecService service;
    @Mock
    private ProductMasterAccessGuard accessGuard;
    @Mock
    private AuthSessionTokenService sessionTokenService;

    private ProductVariantSpecController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductVariantSpecController(serviceProvider, sessionTokenService, accessGuardProvider);
    }

    @Test
    void shouldExposePlatformSpecRouteOutsideProductMasterDetailApi() {
        RequestMapping mapping = ProductVariantSpecController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/product-variant-specs"}, mapping.value());
    }

    @Test
    void listShouldResolveOwnerFromSessionAndStoreScope() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR245027-NAE")))
                .thenReturn(10002L);
        when(service.list(any())).thenReturn(new ProductVariantSpecListView());

        controller.list(99999L, "STR245027-NAE", "MILKYWAYA05P", request);

        ArgumentCaptor<ProductVariantSpecListCommand> captor = ArgumentCaptor.forClass(ProductVariantSpecListCommand.class);
        verify(service).list(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
    }

    @Test
    void saveShouldResolveOwnerAndOperatorBeforeCallingService() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(10002L), org.mockito.ArgumentMatchers.eq("STR245027-NAE")))
                .thenReturn(10002L);
        when(service.save(any())).thenReturn(new ProductVariantSpecView());

        ProductVariantSpecCommand command = new ProductVariantSpecCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA05P");
        command.setPartnerSku("MILKYWAYA05");
        command.setProductLengthCm(new BigDecimal("10"));

        controller.save(command, request);

        ArgumentCaptor<ProductVariantSpecCommand> captor = ArgumentCaptor.forClass(ProductVariantSpecCommand.class);
        verify(service).save(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
    }

    @Test
    void saveShouldLetServiceValidationHandleNullBody() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(10003L);
        when(service.save(any())).thenThrow(new IllegalArgumentException("店铺不能为空"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.save(null, request)
        );

        assertEquals(400, error.getStatus().value());
    }

    @Test
    void shouldReturnServiceUnavailableWhenServiceMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.list(10002L, "STR245027-NAE", "MILKYWAYA05P", request)
        );

        assertEquals(503, error.getStatus().value());
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }
}

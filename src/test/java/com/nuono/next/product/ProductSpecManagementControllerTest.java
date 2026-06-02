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

@ExtendWith(MockitoExtension.class)
class ProductSpecManagementControllerTest {

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

    private ProductSpecManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductSpecManagementController(serviceProvider, sessionTokenService, accessGuardProvider);
    }

    @Test
    void shouldExposeStandaloneProductSpecsRoute() {
        RequestMapping mapping = ProductSpecManagementController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/product-specs"}, mapping.value());
    }

    @Test
    void overviewShouldResolveOwnerFromSessionAndStoreScope() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR245027-NAE")))
                .thenReturn(10002L);
        when(service.overview(any())).thenReturn(new ProductVariantSpecOverviewView());

        controller.overview(99999L, "STR245027-NAE", "MILKYWAYA05", request);

        ArgumentCaptor<ProductVariantSpecOverviewCommand> captor =
                ArgumentCaptor.forClass(ProductVariantSpecOverviewCommand.class);
        verify(service).overview(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-NAE", captor.getValue().getStoreCode());
        assertEquals("MILKYWAYA05", captor.getValue().getKeyword());
    }

    @Test
    void saveSourceShouldOverrideOwnerSourceVariantAndOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR245027-NAE")))
                .thenReturn(10002L);
        when(service.saveSource(any())).thenReturn(new ProductVariantSpecSourceView());

        ProductVariantSpecSourceCommand command = new ProductVariantSpecSourceCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");

        controller.saveSource(53001L, ProductVariantSpecSourceType.ALI1688, command, request);

        ArgumentCaptor<ProductVariantSpecSourceCommand> captor =
                ArgumentCaptor.forClass(ProductVariantSpecSourceCommand.class);
        verify(service).saveSource(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(53001L, captor.getValue().getVariantId());
        assertEquals(ProductVariantSpecSourceType.ALI1688, captor.getValue().getSourceType());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
    }

    @Test
    void selectEffectiveSourceShouldResolveOwnerAndPassOperator() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessGuardProvider.getIfAvailable()).thenReturn(accessGuard);
        when(accessGuard.resolveOwnerUserId(any(), org.mockito.ArgumentMatchers.eq(99999L), org.mockito.ArgumentMatchers.eq("STR245027-NAE")))
                .thenReturn(10002L);
        when(service.selectEffectiveSource(10002L, "STR245027-NAE", 53001L, 120001L, 10003L))
                .thenReturn(new ProductVariantSpecDetailView());

        ProductVariantSpecEffectiveSourceCommand command = new ProductVariantSpecEffectiveSourceCommand();
        command.setOwnerUserId(99999L);
        command.setStoreCode("STR245027-NAE");
        command.setSourceId(120001L);

        controller.selectEffectiveSource(53001L, command, request);

        verify(service).selectEffectiveSource(10002L, "STR245027-NAE", 53001L, 120001L, 10003L);
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }
}

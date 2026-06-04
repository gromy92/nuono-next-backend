package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class ProductSpecManagementControllerTest {

    @Mock
    private ObjectProvider<ProductVariantSpecService> serviceProvider;
    @Mock
    private ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;
    @Mock
    private ProductVariantSpecService service;
    @Mock
    private BusinessAccessResolver businessAccessResolver;
    @Mock
    private AuthSessionTokenService sessionTokenService;

    private ProductSpecManagementController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductSpecManagementController(
                serviceProvider,
                sessionTokenService,
                businessAccessResolverProvider
        );
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
        when(businessAccessResolverProvider.getIfAvailable()).thenReturn(businessAccessResolver);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenReturn(productContext(10003L, 10002L, "STR245027-NAE"));
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
        when(businessAccessResolverProvider.getIfAvailable()).thenReturn(businessAccessResolver);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenReturn(productContext(10003L, 10002L, "STR245027-NAE"));
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
        when(businessAccessResolverProvider.getIfAvailable()).thenReturn(businessAccessResolver);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR245027-NAE"
        )).thenReturn(productContext(10003L, 10002L, "STR245027-NAE"));
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

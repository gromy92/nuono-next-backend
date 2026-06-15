package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class ProductLiteControllerTest {

    @Mock
    private ProductLiteQueryService service;
    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductLiteController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLiteController(service, businessAccessResolver);
    }

    @Test
    void shouldExposeProductLiteRoute() {
        RequestMapping mapping = ProductLiteController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/products/lite"}, mapping.value());
    }

    @Test
    void searchShouldResolveProductMasterStoreAccessAndPassQuery() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = productContext();
        ProductLiteView view = new ProductLiteView();
        view.setProductMasterId(90001L);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.search(eq(context), any())).thenReturn(List.of(view));

        List<ProductLiteView> result = controller.search(
                "STR108065-NSA",
                "sa",
                " Basket ",
                null,
                30,
                request
        );

        assertEquals(90001L, result.get(0).getProductMasterId());
        ArgumentCaptor<ProductLiteQuery> captor = ArgumentCaptor.forClass(ProductLiteQuery.class);
        verify(service).search(eq(context), captor.capture());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("sa", captor.getValue().getSiteCode());
        assertEquals(" Basket ", captor.getValue().getTitleKeyword());
        assertEquals(30, captor.getValue().getLimit());
    }

    @Test
    void searchShouldFallbackKeywordToTitleKeyword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = productContext();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.search(eq(context), any())).thenReturn(List.of());

        controller.search("STR108065-NSA", null, null, "中文标题", null, request);

        ArgumentCaptor<ProductLiteQuery> captor = ArgumentCaptor.forClass(ProductLiteQuery.class);
        verify(service).search(eq(context), captor.capture());
        assertEquals("中文标题", captor.getValue().getTitleKeyword());
    }

    private static BusinessAccessContext productContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/api/sku/manage"))
                .build();
    }
}

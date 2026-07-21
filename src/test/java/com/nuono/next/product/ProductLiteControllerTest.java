package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessStoreAccess;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import com.nuono.next.permission.access.BusinessStoreAccessTestFixture;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class ProductLiteControllerTest {

    @Mock
    private ProductLiteQueryService service;

    private ProductLiteController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLiteController(service);
    }

    @Test
    void searchDeclaresTypedProductMasterStoreScope() throws Exception {
        Method method = ProductLiteController.class.getDeclaredMethod(
                "search",
                String.class,
                String.class,
                String.class,
                Integer.class,
                BusinessStoreAccess.class
        );
        RequiredBusinessAccess requiredAccess = method.getParameters()[4]
                .getAnnotation(RequiredBusinessAccess.class);

        assertEquals(BusinessCapability.PRODUCT_MASTER, requiredAccess.capability());
        assertEquals("storeCode", requiredAccess.storeQueryParameter());
    }

    @Test
    void shouldExposeProductLiteRoute() {
        RequestMapping mapping = ProductLiteController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/products/lite"}, mapping.value());
    }

    @Test
    void searchShouldResolveProductMasterStoreAccessAndPassQuery() {
        BusinessStoreAccess storeAccess = storeAccess();
        ProductLiteView view = new ProductLiteView();
        view.setProductMasterId(90001L);
        when(service.search(eq(storeAccess), any())).thenReturn(List.of(view));

        List<ProductLiteView> result = controller.search(
                "sa",
                " Basket ",
                null,
                30,
                storeAccess
        );

        assertEquals(90001L, result.get(0).getProductMasterId());
        ArgumentCaptor<ProductLiteQuery> captor = ArgumentCaptor.forClass(ProductLiteQuery.class);
        verify(service).search(eq(storeAccess), captor.capture());
        assertEquals("sa", captor.getValue().getSiteCode());
        assertEquals(" Basket ", captor.getValue().getTitleKeyword());
        assertEquals(30, captor.getValue().getLimit());
    }

    @Test
    void searchShouldFallbackKeywordToTitleKeyword() {
        BusinessStoreAccess storeAccess = storeAccess();
        when(service.search(eq(storeAccess), any())).thenReturn(List.of());

        controller.search(null, null, "中文标题", null, storeAccess);

        ArgumentCaptor<ProductLiteQuery> captor = ArgumentCaptor.forClass(ProductLiteQuery.class);
        verify(service).search(eq(storeAccess), captor.capture());
        assertEquals("中文标题", captor.getValue().getTitleKeyword());
    }

    private static BusinessStoreAccess storeAccess() {
        return BusinessStoreAccessTestFixture.access(501L, "STR108065-NSA");
    }
}

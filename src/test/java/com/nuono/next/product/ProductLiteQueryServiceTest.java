package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductLiteQueryServiceTest {

    @Mock
    private ProductLiteMapper mapper;

    private ProductLiteQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProductLiteQueryService(mapper);
    }

    @Test
    void searchNormalizesScopeLimitAndMapsLightFields() throws Exception {
        ProductLiteRecord row = record();
        when(mapper.search(501L, "STR108065-NSA", "SA", "Laundry Basket", 50))
                .thenReturn(List.of(row));
        ProductLiteQuery query = new ProductLiteQuery();
        query.setStoreCode(" str108065-nsa ");
        query.setSiteCode(" sa ");
        query.setTitleKeyword(" Laundry Basket ");
        query.setLimit(500);

        List<ProductLiteView> views = service.search(operatorContext(), query);

        verify(mapper).search(501L, "STR108065-NSA", "SA", "Laundry Basket", 50);
        assertEquals(1, views.size());
        ProductLiteView view = views.get(0);
        assertEquals(90001L, view.getProductMasterId());
        assertEquals("STR108065-NSA", view.getStoreCode());
        assertEquals("SA", view.getSiteCode());
        assertEquals("Laundry Basket English", view.getTitle());
        assertEquals("脏衣篮", view.getTitleCn());
        assertEquals("Laundry Basket English", view.getTitleEn());
        assertEquals("Acme", view.getBrand());
        assertEquals("https://cdn.example/basket.jpg", view.getImageUrl());
        assertEquals("home-storage-basket", view.getProductFulltype());
        assertEquals("SELF_BUILT", view.getSourceType());
        assertNoForbiddenProductLiteGetters();
    }

    @Test
    void searchDefaultsInvalidLimitToTwenty() {
        ProductLiteQuery query = new ProductLiteQuery();
        query.setStoreCode("STR108065-NSA");
        query.setLimit(0);

        service.search(operatorContext(), query);

        verify(mapper).search(501L, "STR108065-NSA", null, null, 20);
    }

    @Test
    void searchRejectsMissingStoreCode() {
        ProductLiteQuery query = new ProductLiteQuery();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.search(operatorContext(), query)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("PRODUCT_LITE_STORE_REQUIRED", error.getReason());
    }

    @Test
    void searchRejectsStoreOutsideSessionScope() {
        ProductLiteQuery query = new ProductLiteQuery();
        query.setStoreCode("STR-OTHER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.search(operatorContext(), query)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("PRODUCT_LITE_STORE_SCOPE_REQUIRED", error.getReason());
    }

    private static void assertNoForbiddenProductLiteGetters() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getSkuParent"));
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getVariantId"));
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getOfferCode"));
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getEffectiveSpec"));
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getStock"));
        assertThrows(NoSuchMethodException.class, () -> ProductLiteView.class.getMethod("getPrice"));
    }

    private static ProductLiteRecord record() {
        ProductLiteRecord record = new ProductLiteRecord();
        record.setProductMasterId(90001L);
        record.setStoreCode("STR108065-NSA");
        record.setSiteCode("SA");
        record.setTitle("Laundry Basket English");
        record.setTitleCn("脏衣篮");
        record.setTitleEn("Laundry Basket English");
        record.setBrand("Acme");
        record.setImageUrl("https://cdn.example/basket.jpg");
        record.setProductFulltype("home-storage-basket");
        record.setSourceType("SELF_BUILT");
        return record;
    }

    private static BusinessAccessContext operatorContext() {
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

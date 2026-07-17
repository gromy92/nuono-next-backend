package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@ExtendWith(MockitoExtension.class)
class ProductVariantSpecPskuIdentityTest {

    @Mock
    private ProductManagementMapper mapper;

    private ProductVariantSpecService specService;
    private ProductVariantLogisticsProfileService logisticsService;

    @BeforeEach
    void setUp() {
        specService = new ProductVariantSpecService(mapper);
        logisticsService = new ProductVariantLogisticsProfileService(mapper);
    }

    @Test
    void specResolverPrefersStorePartnerSkuAndStillAcceptsLegacyVariantId() {
        when(mapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR69486-NSA")).thenReturn(51001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(51001L, "SGGRB113")).thenReturn(53001L);

        assertEquals(53001L, specService.resolveVariantId(10002L, "STR69486-NSA", "SGGRB113", null));
        assertEquals(53001L, specService.resolveVariantId(10002L, "STR69486-NSA", null, 53001L));
    }

    @Test
    void logisticsResolverPrefersStorePartnerSkuAndStillAcceptsLegacyVariantId() {
        when(mapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR69486-NSA")).thenReturn(51001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(51001L, "SGGRB113")).thenReturn(53001L);

        assertEquals(53001L, logisticsService.resolveVariantId(10002L, "STR69486-NSA", "SGGRB113", null));
        assertEquals(53001L, logisticsService.resolveVariantId(10002L, "STR69486-NSA", null, 53001L));
    }

    @Test
    void specResolverDoesNotFallbackToLegacyVariantIdWhenPartnerSkuIsPresent() {
        when(mapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR69486-NSA")).thenReturn(51001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(51001L, "SGGRB113")).thenReturn(null);

        assertNull(specService.resolveVariantId(10002L, "STR69486-NSA", "SGGRB113", 99999L));
    }

    @Test
    void logisticsResolverDoesNotFallbackToLegacyVariantIdWhenPartnerSkuIsPresent() {
        when(mapper.selectLogicalStoreIdByOwnerStoreCode(10002L, "STR69486-NSA")).thenReturn(51001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(51001L, "SGGRB113")).thenReturn(null);

        assertNull(logisticsService.resolveVariantId(10002L, "STR69486-NSA", "SGGRB113", 99999L));
    }

    @Test
    void controllersExposePskuRouteShapesAndKeepLegacyVariantRoutes() throws Exception {
        assertArrayEquals(
                new String[]{"/by-psku"},
                ProductSpecManagementController.class
                        .getMethod("detailByPsku", Long.class, String.class, String.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(GetMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-psku/sources/{sourceType}"},
                ProductSpecManagementController.class
                        .getMethod("saveSourceByPsku", String.class, String.class, String.class, ProductVariantSpecSourceCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-psku/effective-source"},
                ProductSpecManagementController.class
                        .getMethod("selectEffectiveSourceByPsku", String.class, String.class, ProductVariantSpecEffectiveSourceCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-identity"},
                ProductSpecManagementController.class
                        .getMethod("detailByIdentity", Long.class, String.class, String.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(GetMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-identity/sources/{sourceType}"},
                ProductSpecManagementController.class
                        .getMethod("saveSourceByIdentity", String.class, String.class, String.class, ProductVariantSpecSourceCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-identity/effective-source"},
                ProductSpecManagementController.class
                        .getMethod("selectEffectiveSourceByIdentity", String.class, String.class, ProductVariantSpecEffectiveSourceCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/{variantId}"},
                ProductVariantLogisticsProfileController.class
                        .getMethod("save", Long.class, ProductVariantLogisticsProfileCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-psku"},
                ProductVariantLogisticsProfileController.class
                        .getMethod("saveByPsku", ProductVariantLogisticsProfileCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
    }
}

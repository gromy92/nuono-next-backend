package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductCatalogContentFallbackApplierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductNoonCatalogContentService parser = new ProductNoonCatalogContentService(null);

    @Mock
    private ProductNoonCatalogContentService catalogContentService;

    private ProductCatalogContentFallbackApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ProductCatalogContentFallbackApplier(catalogContentService);
    }

    @Test
    void shouldFetchAndMergeCatalogContentWhenTitleOrImagesMissing() throws Exception {
        ProductNoonCatalogContentService.CatalogContent catalogContent = parser.parseCatalogContent(
                objectMapper.readTree("{\"product\":{"
                        + "\"sku\":\"N53437240A\","
                        + "\"brand\":\"Apple\","
                        + "\"product_title\":\"Catalog title\","
                        + "\"long_description\":\"Catalog description\","
                        + "\"feature_bullets\":[\"USB-C charging\"],"
                        + "\"image_keys\":[\"pnsku/N53437240A/45/_/1711111111/main\"]"
                        + "}}"),
                "en"
        );
        ProductNoonCatalogContentService.CatalogContent arCatalogContent = parser.parseCatalogContent(
                objectMapper.readTree("{\"product\":{"
                        + "\"sku\":\"N53437240A\","
                        + "\"product_title\":\"عنوان الكتالوج\","
                        + "\"long_description\":\"وصف الكتالوج\","
                        + "\"feature_bullets\":[\"شحن USB-C\"]"
                        + "}}"),
                "ar"
        );
        catalogContent.mergeMissing(arCatalogContent);
        when(catalogContentService.fetchFollowSellCatalogContent(
                isNull(),
                eq("N53437240A"),
                eq("AE"),
                eq("detail.default")
        )).thenReturn(Optional.of(catalogContent));
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("skuParent", "N53437240A");
        identity.put("childSku", "N53437240A");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleAr", "Existing Arabic title");
        content.put("descriptionEn", "Existing description");
        content.put("highlightsEn", new ArrayList<>(List.of("Existing highlight")));

        applier.applyIfNeeded(null, identity, content, "AE", "default");

        assertEquals("Apple", identity.get("brand"));
        assertEquals("N53437240A", identity.get("childSku"));
        assertEquals(ProductSourceTypeSupport.FOLLOW_SELL, identity.get("productSourceType"));
        assertEquals("Catalog title", content.get("titleEn"));
        assertEquals("Existing Arabic title", content.get("titleAr"));
        assertEquals("Existing description", content.get("descriptionEn"));
        assertEquals("وصف الكتالوج", content.get("descriptionAr"));
        assertEquals(List.of("Existing highlight"), content.get("highlightsEn"));
        assertEquals(List.of("شحن USB-C"), content.get("highlightsAr"));
        assertEquals(List.of("https://f.nooncdn.com/p/pnsku/N53437240A/45/_/1711111111/main.jpg"), content.get("images"));
        assertEquals(1, content.get("imageCount"));
    }

    @Test
    void shouldSkipCatalogFetchWhenContentAlreadyHasTitleAndImages() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("childSku", "N53437240A");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleEn", "Existing title");
        content.put("images", List.of("https://cdn.example/existing.jpg"));

        applier.applyIfNeeded(null, identity, content, "AE", "default");

        verifyNoInteractions(catalogContentService);
        assertEquals("Existing title", content.get("titleEn"));
        assertEquals(List.of("https://cdn.example/existing.jpg"), content.get("images"));
    }
}

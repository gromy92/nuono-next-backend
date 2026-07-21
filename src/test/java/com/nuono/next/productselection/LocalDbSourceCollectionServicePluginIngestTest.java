package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbSourceCollectionServicePluginIngestTest {

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPluginIngestMapper pluginIngestMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ProductSelectionSourceCollectionCollector sourceCollectionCollector;

    @Mock
    private ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;

    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private LocalDbSourceCollectionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        SourceCollectionCompletenessCalculator calculator = new SourceCollectionCompletenessCalculator();
        PluginSourceCollectionUpsertService upsertService = new PluginSourceCollectionUpsertService(
                productSelectionMapper,
                pluginIngestMapper,
                calculator,
                ali1688CollectionService,
                objectMapper
        );
        service = new LocalDbSourceCollectionService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                calculator,
                ali1688CollectionService,
                objectMapper,
                NoonRiskBackoffGuard.disabled(),
                upsertService
        );
    }

    @Test
    void pluginIngestCreatesSuccessfulSourceCollectionWithPluginSourceAndSiteScope() {
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setStoreCode("STR108065-NAE");
        command.setSourcePlatform("Amazon");
        command.setSourceUrl("https://www.amazon.ae/dp/B00006IFHD?th=1");
        command.setPageUrl("https://www.amazon.ae/dp/B00006IFHD?th=1");
        command.setSourceTitle("Sharpie Permanent Markers, Fine Point, Black, 12-Count");
        command.setSourceImageUrl("https://m.media-amazon.com/images/I/51O0epbavGL.jpg");
        command.setImageUrls(List.of(
                "https://m.media-amazon.com/images/I/51O0epbavGL.jpg",
                "https://m.media-amazon.com/images/I/41GTEBbDEVL._AC_US40_.jpg"
        ));
        command.setPriceSummary("12.80");
        command.setSpecHints(List.of("ASIN: B00006IFHD", "Brand: Sharpie"));
        command.setCategoryLinks(List.of(new ProductSelectionCompetitorCategoryLink(
                "Permanent Markers",
                "Office Products > Writing Supplies > Permanent Markers",
                "https://www.amazon.ae/s?rh=n%3A123456"
        )));
        command.setSourceSellingPointsEn(List.of("Fine point permanent marker"));
        command.setWarnings(List.of(new ProductSelectionPluginIngestCommand.PluginWarning(
                "field_missing",
                "sourceDescriptionAr",
                "Arabic content was not visible on the current page."
        )));
        command.setOperatorUserId(307L);
        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(storeScope("AE"));
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86488L);

        ProductSelectionPluginIngestResponse response = service.pluginIngestSourceCollection(command);

        ArgumentCaptor<ProductSelectionSourceCollectionRow> rowCaptor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(productSelectionMapper).insertSourceCollection(rowCaptor.capture());
        ProductSelectionSourceCollectionRow row = rowCaptor.getValue();
        assertEquals("marketplace-url", row.getSourceType());
        assertEquals("plugin", row.getCollectionSource());
        assertEquals("AE", row.getSiteCode());
        assertEquals("Amazon", row.getSourcePlatform());
        assertEquals("success", row.getStatus());
        assertEquals("Sharpie Permanent Markers, Fine Point, Black, 12-Count", row.getSourceTitle());
        assertTrue(row.getImageUrlsJson().contains("41GTEBbDEVL"));
        assertTrue(row.getCategoryLinksJson().contains("Permanent Markers"));
        assertTrue(row.getNotes().contains("field_missing:sourceDescriptionAr"));
        verify(ali1688CollectionService).ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L));

        assertEquals("86488", response.getSourceCollection().getId());
        assertEquals("plugin", response.getSourceCollection().getCollectionSource());
        assertEquals("AE", response.getSourceCollection().getSiteCode());
        assertEquals("Permanent Markers", response.getSourceCollection().getCategoryName());
        assertEquals(
                "Office Products > Writing Supplies > Permanent Markers",
                response.getSourceCollection().getCategoryPath()
        );
        assertEquals("https://www.amazon.ae/s?rh=n%3A123456", response.getSourceCollection().getCategoryUrl());
        assertEquals(1, response.getSourceCollection().getCategoryLinks().size());
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    void pluginIngestDropsNoonNavigationLabelsFromSellingPoints() throws Exception {
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setStoreCode("STR245027-NSA");
        command.setSourcePlatform("Noon");
        command.setSourceUrl("https://www.noon.com/saudi-en/48-colours-acrylic-paint-marker-set/ZF69057049A17BC4C8ED6Z/p/");
        command.setPageUrl("https://www.noon.com/saudi-en/48-colours-acrylic-paint-marker-set/ZF69057049A17BC4C8ED6Z/p/");
        command.setSourceTitle("48 Colours Acrylic Paint Marker Set with Brush Tip");
        command.setSourceSellingPointsEn(List.of(
                "Electronics Mobiles & Accessories Galaxy AI",
                "iPhone 17 Series",
                "›",
                "'> ' class='nav_a'>",
                "Product Overview Specifications",
                "Product Ratings & Reviews",
                "Quick-drying water-based acrylic ink works on wood, rock and canvas"
        ));
        command.setSourceSellingPointsAr(List.of(
                "الإلكترونيات الجوالات والاكسسوارات جالاكسي Al",
                "شواحن",
                "عن هذه السلعة",
                "عربة التسوق shift + opt + C",
                "نظرة عامة على المنتج المواصفات",
                "تقييمات المنتج والمراجعات",
                "حبر أكريليك مائي سريع الجفاف مناسب للخشب والصخور والقماش"
        ));
        command.setOperatorUserId(307L);
        when(permissionGuard.requireWritableStore(307L, "STR245027-NSA")).thenReturn(storeScope("SA"));
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86490L);

        ProductSelectionPluginIngestResponse response = service.pluginIngestSourceCollection(command);

        ArgumentCaptor<ProductSelectionSourceCollectionRow> rowCaptor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(productSelectionMapper).insertSourceCollection(rowCaptor.capture());
        ProductSelectionSourceCollectionRow row = rowCaptor.getValue();
        assertFalse(row.getSourceSellingPointsEnJson().contains("Electronics Mobiles"));
        assertFalse(row.getSourceSellingPointsEnJson().contains("iPhone 17 Series"));
        assertFalse(row.getSourceSellingPointsEnJson().contains("nav_a"));
        assertFalse(row.getSourceSellingPointsEnJson().contains("Product Overview Specifications"));
        assertFalse(row.getSourceSellingPointsEnJson().contains("Product Ratings & Reviews"));
        assertTrue(row.getSourceSellingPointsEnJson().contains("Quick-drying water-based acrylic ink"));
        assertFalse(row.getSourceSellingPointsArJson().contains("الإلكترونيات الجوالات والاكسسوارات"));
        assertFalse(row.getSourceSellingPointsArJson().contains("شواحن\""));
        assertFalse(row.getSourceSellingPointsArJson().contains("عن هذه السلعة"));
        assertFalse(row.getSourceSellingPointsArJson().contains("عربة التسوق"));
        assertFalse(row.getSourceSellingPointsArJson().contains("نظرة عامة على المنتج المواصفات"));
        assertFalse(row.getSourceSellingPointsArJson().contains("تقييمات المنتج والمراجعات"));
        assertTrue(row.getSourceSellingPointsArJson().contains("حبر أكريليك مائي سريع الجفاف"));

        assertEquals(
                List.of("Quick-drying water-based acrylic ink works on wood, rock and canvas"),
                response.getSourceCollection().getSourceSellingPointsEn()
        );
        assertEquals(
                List.of("حبر أكريليك مائي سريع الجفاف مناسب للخشب والصخور والقماش"),
                response.getSourceCollection().getSourceSellingPointsAr()
        );
    }

    @Test
    void pluginIngestAllowsTemuBecauseTemuOnlyArrivesThroughPlugin() {
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setStoreCode("STR108065-NAE");
        command.setSourcePlatform("Temu");
        command.setSourceUrl("https://www.temu.com/goods.html?goods_id=601099512345678");
        command.setPageUrl("https://www.temu.com/goods.html?goods_id=601099512345678");
        command.setSourceTitle("Temu cable organizer");
        command.setSourceImageUrl("https://img.temu.example/main.jpg");
        command.setOperatorUserId(307L);
        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(storeScope("AE"));
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86489L);

        ProductSelectionPluginIngestResponse response = service.pluginIngestSourceCollection(command);

        ArgumentCaptor<ProductSelectionSourceCollectionRow> rowCaptor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(productSelectionMapper).insertSourceCollection(rowCaptor.capture());
        assertEquals("Temu", rowCaptor.getValue().getSourcePlatform());
        assertEquals("plugin", rowCaptor.getValue().getCollectionSource());
        assertEquals("success", response.getSourceCollection().getStatus());
    }

    @Test
    void pluginIngestRejectsEmptyExtractionBeforeCreatingRecord() {
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setStoreCode("STR108065-NAE");
        command.setSourcePlatform("Temu");
        command.setSourceUrl("https://www.temu.com/goods.html?goods_id=601099512345678");
        command.setOperatorUserId(307L);
        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(storeScope("AE"));

        assertThrows(IllegalArgumentException.class, () -> service.pluginIngestSourceCollection(command));

        verify(productSelectionMapper, never()).insertSourceCollection(any(ProductSelectionSourceCollectionRow.class));
        verify(ali1688CollectionService, never()).ensureTaskForSourceCollection(any(), eq(307L));
    }

    @Test
    void pluginIngestRejectsClientPlatformWhenUrlBelongsToAnotherMarketplace() {
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setStoreCode("STR108065-NAE");
        command.setSourcePlatform("Temu");
        command.setSourceUrl("https://www.amazon.ae/dp/B00006IFHD?th=1");
        command.setPageUrl("https://www.amazon.ae/dp/B00006IFHD?th=1");
        command.setSourceTitle("Amazon product page");
        command.setOperatorUserId(307L);
        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(storeScope("AE"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.pluginIngestSourceCollection(command)
        );

        assertTrue(error.getMessage().contains("平台"));
        verify(productSelectionMapper, never()).insertSourceCollection(any(ProductSelectionSourceCollectionRow.class));
        verify(ali1688CollectionService, never()).ensureTaskForSourceCollection(any(), eq(307L));
    }

    private ProductSelectionStoreScope storeScope(String site) {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOperatorUserId(307L);
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        scope.setProjectName("canman");
        scope.setStoreCode("STR108065-NAE");
        scope.setSite(site);
        return scope;
    }

}

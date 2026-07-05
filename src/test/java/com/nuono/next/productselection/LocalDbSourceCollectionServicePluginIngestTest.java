package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
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
        service = new LocalDbSourceCollectionService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                new SourceCollectionCompletenessCalculator(),
                ali1688CollectionService,
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled()
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
        assertTrue(row.getNotes().contains("field_missing:sourceDescriptionAr"));
        verify(ali1688CollectionService).ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L));

        assertEquals("86488", response.getSourceCollection().getId());
        assertEquals("plugin", response.getSourceCollection().getCollectionSource());
        assertEquals("AE", response.getSourceCollection().getSiteCode());
        assertEquals(1, response.getWarnings().size());
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

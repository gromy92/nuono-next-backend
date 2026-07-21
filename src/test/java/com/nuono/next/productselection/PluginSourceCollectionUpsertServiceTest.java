package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PluginSourceCollectionUpsertServiceTest {

    @Mock
    private ProductSelectionMapper sourceMapper;
    @Mock
    private ProductSelectionPluginIngestMapper pluginMapper;
    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private PluginSourceCollectionUpsertService service;

    @BeforeEach
    void setUp() {
        service = new PluginSourceCollectionUpsertService(
                sourceMapper,
                pluginMapper,
                new SourceCollectionCompletenessCalculator(),
                ali1688CollectionService,
                new ObjectMapper()
        );
    }

    @Test
    void sameAmazonAsinAcrossArabicAndEnglishUrlsUpdatesExistingPsc() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setOperatorUserId(307L);
        command.setSourceTitle("English phone case title");
        command.setSourceTitleAr("عنوان حافظة الهاتف بالعربية");
        command.setSourceDescriptionEn("English product description collected by the repaired plugin.");

        ProductSelectionSourceCollectionRow existing = existingRow();
        when(pluginMapper.listRecentForDedupe(307L, 301L, "AE", "Amazon", 500))
                .thenReturn(List.of(existing));
        when(pluginMapper.update(any(ProductSelectionSourceCollectionRow.class))).thenReturn(1);

        PluginSourceCollectionUpsertService.Result result = service.upsert(
                scope,
                "AE",
                command,
                "Amazon",
                "https://www.amazon.ae/-/en/Phone-Case/dp/B0ABCDEF12?th=1",
                "https://www.amazon.ae/-/en/Phone-Case/dp/B0ABCDEF12?th=1",
                List.of(),
                List.of(),
                List.of(new ProductSelectionCompetitorCategoryLink(
                        "Phone Cases",
                        "Mobiles & Accessories > Phone Cases",
                        "https://www.amazon.ae/s?rh=n%3A123"
                )),
                List.of(),
                List.of(),
                ""
        );

        ArgumentCaptor<ProductSelectionSourceCollectionRow> captor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(pluginMapper).update(captor.capture());
        ProductSelectionSourceCollectionRow updated = captor.getValue();
        assertEquals(86480L, updated.getId());
        assertEquals("PSC-86480", updated.getCollectionNo());
        assertEquals("English phone case title", updated.getSourceTitle());
        assertEquals("عنوان حافظة الهاتف بالعربية", updated.getSourceTitleAr());
        assertEquals("English product description collected by the repaired plugin.", updated.getSourceDescriptionEn());
        assertEquals("https://m.media-amazon.com/images/I/existing.jpg", updated.getSourceImageUrl());
        assertTrue(updated.getCategoryLinksJson().contains("Phone Cases"));
        verify(sourceMapper, never()).nextSourceCollectionId();
        verify(sourceMapper, never()).insertSourceCollection(any(ProductSelectionSourceCollectionRow.class));
        assertTrue(result.deduped());
        assertEquals(86480L, result.row().getId());
    }

    @Test
    void duplicateUpdateClearsStoredNoonCustomerSupportPlaceholdersWhenIncomingDescriptionsAreEmpty() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setOperatorUserId(307L);
        command.setSourceTitle("Vintage paper set");

        ProductSelectionSourceCollectionRow existing = existingRow();
        String noonUrl = "https://www.noon.com/saudi-en/vintage-paper/Z161C92DA0421DCAF2786Z/p/";
        existing.setSourceUrl(noonUrl);
        existing.setPageUrl(noonUrl);
        existing.setSourceDescriptionEn("We're Always Here To Help");
        existing.setSourceDescriptionAr("نحن دائماً جاهزون لمساعدتك");
        when(pluginMapper.listRecentForDedupe(307L, 301L, "SA", "Noon", 500))
                .thenReturn(List.of(existing));
        when(pluginMapper.update(any(ProductSelectionSourceCollectionRow.class))).thenReturn(1);

        service.upsert(
                scope,
                "SA",
                command,
                "Noon",
                noonUrl,
                noonUrl,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        ArgumentCaptor<ProductSelectionSourceCollectionRow> captor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(pluginMapper).update(captor.capture());
        assertEquals("", captor.getValue().getSourceDescriptionEn());
        assertEquals("", captor.getValue().getSourceDescriptionAr());
    }

    private ProductSelectionSourceCollectionRow existingRow() {
        String url = "https://www.amazon.ae/-/ar/Phone-Case/dp/B0ABCDEF12?psc=1";
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86480L);
        row.setCollectionNo("PSC-86480");
        row.setSourceUrl(url);
        row.setPageUrl(url);
        row.setSourceTitle("旧阿语标题");
        row.setSourceTitleAr("旧阿语标题");
        row.setSourceImageUrl("https://m.media-amazon.com/images/I/existing.jpg");
        row.setImageUrlsJson("[\"https://m.media-amazon.com/images/I/existing.jpg\"]");
        row.setSpecHintsJson("[]");
        row.setCategoryLinksJson("[]");
        row.setSourceSellingPointsEnJson("[]");
        row.setSourceSellingPointsArJson("[]");
        row.setCreatedBy(307L);
        return row;
    }
}

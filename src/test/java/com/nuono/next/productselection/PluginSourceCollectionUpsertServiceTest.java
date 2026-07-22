package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void sameAmazonAsinFromANewBatchCreatesANewPscInsteadOfUpdatingHistory() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setOperatorUserId(307L);
        command.setSourceTitle("English phone case title");
        command.setSourceTitleAr("عنوان حافظة الهاتف بالعربية");
        command.setSourceDescriptionEn("English product description collected by the repaired plugin.");
        command.setCollectionBatchId("batch-20260721-new");
        command.setCollectionItemKey("tab:52");
        command.setExtractorVersion("20260721-marketplace-source-new-batch-r5");

        when(pluginMapper.selectByBatchItem(307L, 301L, "batch-20260721-new", "tab:52"))
                .thenReturn(null);
        when(sourceMapper.nextSourceCollectionId()).thenReturn(86481L);

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
        verify(pluginMapper).insert(
                captor.capture(),
                eq("batch-20260721-new"),
                eq("tab:52"),
                eq("20260721-marketplace-source-new-batch-r5")
        );
        ProductSelectionSourceCollectionRow inserted = captor.getValue();
        assertEquals(86481L, inserted.getId());
        assertEquals("PSC-86481", inserted.getCollectionNo());
        assertEquals("English phone case title", inserted.getSourceTitle());
        assertEquals("عنوان حافظة الهاتف بالعربية", inserted.getSourceTitleAr());
        assertEquals("English product description collected by the repaired plugin.", inserted.getSourceDescriptionEn());
        assertTrue(inserted.getCategoryLinksJson().contains("Phone Cases"));
        assertFalse(result.deduped());
        assertEquals(86481L, result.row().getId());
    }

    @Test
    void exactBatchItemRetryReturnsExistingPscWithoutCreatingAnotherRecordOrTask() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setOperatorUserId(307L);
        command.setSourceTitle("Vintage paper set");
        command.setCollectionBatchId("batch-20260721-retry");
        command.setCollectionItemKey("tab:31");

        ProductSelectionSourceCollectionRow existing = existingRow();
        String noonUrl = "https://www.noon.com/saudi-en/vintage-paper/Z161C92DA0421DCAF2786Z/p/";
        existing.setSourceUrl(noonUrl);
        existing.setPageUrl(noonUrl);
        when(pluginMapper.selectByBatchItem(307L, 301L, "batch-20260721-retry", "tab:31"))
                .thenReturn(existing);

        PluginSourceCollectionUpsertService.Result result = service.upsert(
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

        verify(sourceMapper, never()).nextSourceCollectionId();
        verify(pluginMapper, never()).insert(any(), any(), any(), any());
        verify(ali1688CollectionService, never()).ensureTaskForSourceCollection(any(), any());
        assertTrue(result.deduped());
        assertEquals(86480L, result.row().getId());
    }

    @Test
    void duplicateProductsInOneBatchCreateSeparatePscsWhenItemKeysDiffer() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        ProductSelectionPluginIngestCommand command = new ProductSelectionPluginIngestCommand();
        command.setOperatorUserId(307L);
        command.setSourceTitle("Same Amazon product in two browser tabs");
        command.setCollectionBatchId("batch-20260721-duplicates");
        command.setCollectionItemKey("tab:51");
        when(sourceMapper.nextSourceCollectionId()).thenReturn(86481L, 86482L);

        service.upsert(
                scope, "SA", command, "Amazon",
                "https://www.amazon.sa/dp/B0DVC6VKRD", "https://www.amazon.sa/dp/B0DVC6VKRD",
                List.of(), List.of(), List.of(), List.of(), List.of(), ""
        );
        command.setCollectionItemKey("tab:52");
        service.upsert(
                scope, "SA", command, "Amazon",
                "https://www.amazon.sa/dp/B0DVC6VKRD", "https://www.amazon.sa/dp/B0DVC6VKRD",
                List.of(), List.of(), List.of(), List.of(), List.of(), ""
        );

        ArgumentCaptor<ProductSelectionSourceCollectionRow> rowCaptor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        ArgumentCaptor<String> itemKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(pluginMapper, org.mockito.Mockito.times(2)).insert(
                rowCaptor.capture(), eq("batch-20260721-duplicates"), itemKeyCaptor.capture(), any()
        );
        assertEquals(86481L, rowCaptor.getAllValues().get(0).getId());
        assertEquals(86482L, rowCaptor.getAllValues().get(1).getId());
        assertEquals(List.of("tab:51", "tab:52"), itemKeyCaptor.getAllValues());
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

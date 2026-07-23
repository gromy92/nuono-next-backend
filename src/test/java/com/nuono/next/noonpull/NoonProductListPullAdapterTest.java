package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonProductListPullAdapterTest {

    @Test
    void shouldConvertProductListPayloadIntoProjectionSeedsWithoutDraftOrPublishSideEffects() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ245027")
                .projectName("Xingyao")
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .sourceBatchId("noon-interface-product-130000")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZPARENT-1",
                                "sku", "ZCHILD-1",
                                "partner_sku", "PARTNER-1",
                                "barcode", "BARCODE-1",
                                "content", Map.of("title", "Milkyway bottle", "brand", "Nuono", "image", "img/p1.jpg"),
                                "currency", "AED",
                                "price", "39.90",
                                "sale_price", "35.90",
                                "fbn_stock", 12,
                                "live_status", "active"
                        )
                ))
                .build();

        NoonProductListApplyResult result = adapter.apply(command);

        assertEquals(1, result.getAcceptedCount());
        assertEquals("noon-interface-product-130000", writer.command.getSourceBatchId());
        assertTrue(writer.command.isPreserveDrafts());
        assertFalse(writer.command.isPublishFlowTriggered());
        assertFalse(writer.command.isCompleteSiteScope());
        assertEquals("STR245027-NAE", writer.command.getSiteSeeds().get(0).getStoreCode());
        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertEquals("ZPARENT-1", seed.getSkuParent());
        assertEquals("ZCHILD-1", seed.getChildSku());
        assertEquals("Milkyway bottle", seed.getTitleCache());
        assertEquals("Nuono", seed.getBrandCache());
        assertEquals("synced", seed.getSyncStatus());
        assertEquals("STR245027-NAE", seed.getReferenceStoreCode());
        assertEquals("BARCODE-1", seed.getBarcode());
        assertEquals(List.of("BARCODE-1"), seed.getBarcodes());
        assertEquals("39.90", seed.getFinalPrice());
        assertEquals(1, seed.getSiteOffers().size());
    }

    @Test
    void shouldNotDoublePrefixAlreadyProductScopedNoonImagePath() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ245027")
                .projectName("Xingyao")
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .sourceBatchId("noon-interface-product-130001")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZPARENT-2",
                                "sku", "ZCHILD-2",
                                "content", Map.of("title", "Noon item", "image", "p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b")
                        )
                ))
                .build();

        adapter.apply(command);

        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertEquals(
                "https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg",
                seed.getCoverImageUrl()
        );
    }

    @Test
    void shouldNormalizeProtocolRelativeNoonImagePath() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ245027")
                .projectName("Xingyao")
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .sourceBatchId("noon-interface-product-130002")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZPARENT-3",
                                "sku", "ZCHILD-3",
                                "content", Map.of("title", "Noon item", "image", "//f.nooncdn.com/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b")
                        )
                ))
                .build();

        adapter.apply(command);

        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertEquals(
                "https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg",
                seed.getCoverImageUrl()
        );
    }

    @Test
    void shouldPreserveCamelCasePskuCodeFromProductListPayload() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ245027")
                .projectName("Xingyao")
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .sourceBatchId("noon-interface-product-130003")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZPARENT-4",
                                "sku", "ZCHILD-4",
                                "partner_sku", "PARTNER-4",
                                "pskuCode", "PSKU-CAMEL-4"
                        )
                ))
                .build();

        adapter.apply(command);

        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertEquals("PSKU-CAMEL-4", seed.getPskuCode());
        assertEquals("PSKU-CAMEL-4", seed.getSiteOffers().get(0).getPskuCode());
    }

    @Test
    void shouldTreatBooleanLikeLiveStatusAsActive() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ69486")
                .projectName("SGGR")
                .storeCode("STR69486-NSA")
                .siteCode("SA")
                .sourceBatchId("noon-interface-product-sggr")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZE8DD09EA5F05C485D0ADZ",
                                "sku", "ZE8DD09EA5F05C485D0ADZ-1",
                                "partner_sku", "SGGRB273",
                                "live_status", true
                        )
                ))
                .build();

        adapter.apply(command);

        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertTrue(seed.getIsActive());
        assertTrue(seed.getSiteOffers().get(0).getIsActive());
    }

    @Test
    void shouldReadOfficialPartnerBarcodesFromProductListPayload() {
        CapturingProjectionWriter writer = new CapturingProjectionWriter();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(writer);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ108065")
                .projectName("canman")
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .sourceBatchId("noon-interface-product-partner-barcodes")
                .items(List.of(
                        Map.of(
                                "csku_parent", "Z1A8091E26D63C096412BZ",
                                "zsku_child", "Z1A8091E26D63C096412BZ-1",
                                "partner_sku", "PAPERSAYS440",
                                "partner_barcodes", List.of("PAPERSAYS440", "PAPERSAYSB440")
                        ),
                        Map.of(
                                "csku_parent", "Z199E40249DF52804B4D4Z",
                                "zsku_child", "Z199E40249DF52804B4D4Z-1",
                                "partner_sku", "PAPERSAYSB442",
                                "partner_barcodes", List.of("PAPERSAYSB442")
                        ),
                        Map.of(
                                "csku_parent", "ZB39E8C4EF147B2246BFDZ",
                                "zsku_child", "ZB39E8C4EF147B2246BFDZ-1",
                                "partner_sku", "PAPERSAYSB445",
                                "partner_barcodes", List.of("PAPERSAYSB445")
                        )
                ))
                .build();

        NoonProductListApplyResult result = adapter.apply(command);

        assertEquals(3, result.getAcceptedCount());
        assertEquals(
                List.of("PAPERSAYS440", "PAPERSAYSB440"),
                writer.command.getProductSeeds().get(0).getBarcodes()
        );
        assertEquals("PAPERSAYS440", writer.command.getProductSeeds().get(0).getBarcode());
        assertEquals(
                List.of("PAPERSAYSB442"),
                writer.command.getProductSeeds().get(1).getBarcodes()
        );
        assertEquals(
                List.of("PAPERSAYSB445"),
                writer.command.getProductSeeds().get(2).getBarcodes()
        );
    }

    private static final class CapturingProjectionWriter implements NoonProductProjectionWriter {
        private NoonProductProjectionWriteCommand command;

        @Override
        public void write(NoonProductProjectionWriteCommand command) {
            this.command = command;
            this.command.setWarnings(new ArrayList<>(command.getWarnings()));
        }
    }
}

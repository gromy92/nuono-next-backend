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
                                "offer_code", "OFFER-1",
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
        assertEquals("STR245027-NAE", writer.command.getSiteSeeds().get(0).getStoreCode());
        ProductProjectionPersistenceService.ProductMasterSeed seed = writer.command.getProductSeeds().get(0);
        assertEquals("ZPARENT-1", seed.getSkuParent());
        assertEquals("ZCHILD-1", seed.getChildSku());
        assertEquals("Milkyway bottle", seed.getTitleCache());
        assertEquals("Nuono", seed.getBrandCache());
        assertEquals("synced", seed.getSyncStatus());
        assertEquals("STR245027-NAE", seed.getReferenceStoreCode());
        assertEquals("39.90", seed.getFinalPrice());
        assertEquals(1, seed.getSiteOffers().size());
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

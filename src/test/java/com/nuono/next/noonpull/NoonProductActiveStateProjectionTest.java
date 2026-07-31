package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NoonProductActiveStateProjectionTest {
    @Test
    void productListStatusCodeResolvesActiveWhileUnrecognizedStatusStaysUnknown() {
        AtomicReference<NoonProductProjectionWriteCommand> written = new AtomicReference<>();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(written::set);
        NoonProductListApplyCommand command = NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ108065")
                .projectName("canman")
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .sourceBatchId("noon-interface-product-active-state")
                .items(List.of(
                        Map.of(
                                "sku_parent", "ZACTIVE",
                                "sku", "ZACTIVE-1",
                                "partner_sku", "PAPERSAYS-ACTIVE",
                                "status_code", "ACTIVE"
                        ),
                        Map.of(
                                "sku_parent", "ZINACTIVE",
                                "sku", "ZINACTIVE-1",
                                "partner_sku", "PAPERSAYS-INACTIVE",
                                "live_status", "inactive"
                        ),
                        Map.of(
                                "sku_parent", "ZUNKNOWN",
                                "sku", "ZUNKNOWN-1",
                                "partner_sku", "PAPERSAYS-UNKNOWN",
                                "status_code", "PENDING_REVIEW"
                        )
                ))
                .build();

        adapter.apply(command);

        assertTrue(written.get().isCompleteProductScope());
        ProductProjectionPersistenceService.ProductMasterSeed active =
                written.get().getProductSeeds().get(0);
        assertEquals(Boolean.TRUE, active.getIsActive());
        assertEquals("NOON_PRODUCT_LIST_STATUS_CODE", active.getActiveStateSource());
        assertNotNull(active.getActiveStateSyncedAt());
        assertEquals(Boolean.TRUE, active.getSiteOffers().get(0).getIsActive());

        ProductProjectionPersistenceService.ProductMasterSeed inactive =
                written.get().getProductSeeds().get(1);
        assertEquals(Boolean.FALSE, inactive.getIsActive());
        assertEquals("NOON_PRODUCT_LIST_LIVE_STATUS", inactive.getActiveStateSource());
        assertNotNull(inactive.getActiveStateSyncedAt());
        assertEquals(Boolean.FALSE, inactive.getSiteOffers().get(0).getIsActive());
        assertEquals(
                "NOON_PRODUCT_LIST_LIVE_STATUS",
                inactive.getSiteOffers().get(0).getActiveStateSource()
        );

        ProductProjectionPersistenceService.ProductMasterSeed unknown =
                written.get().getProductSeeds().get(2);
        assertNull(unknown.getIsActive());
        assertNull(unknown.getActiveStateSource());
        assertNull(unknown.getActiveStateSyncedAt());
        assertNull(unknown.getSiteOffers().get(0).getIsActive());
    }

    @Test
    void productListWithoutPartnerSkuFailsClosedForAbsenceReconciliation() {
        AtomicReference<NoonProductProjectionWriteCommand> written = new AtomicReference<>();
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(written::set);

        adapter.apply(NoonProductListApplyCommand.builder()
                .ownerUserId(307L)
                .projectCode("PRJ108065")
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .items(List.of(Map.of("sku_parent", "ZINCOMPLETE", "status_code", "ACTIVE")))
                .build());

        assertFalse(written.get().isCompleteProductScope());
    }
}

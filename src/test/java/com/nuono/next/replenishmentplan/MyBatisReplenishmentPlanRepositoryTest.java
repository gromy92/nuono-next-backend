package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundLineRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.ProductIdentityRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.StockRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisReplenishmentPlanRepositoryTest {

    @Test
    void pskuMatchWinsOverSkuAndMskuAndLineContributesOnce() {
        FakeMapper mapper = new FakeMapper();
        mapper.productIdentities = List.of(
                identity("P1", "P1", "P1-PSKU", "P1-OFFER", "P1-CHILD", "PARENT-1", "BAR-P1"),
                identity("P2", "P2", "SKU-MATCH-P2", "MSKU-MATCH-P2", "SKU-MATCH-P2", "PARENT-2", "BAR-P2")
        );
        mapper.inboundLines = List.of(line(
                10L,
                " P1 ",
                "SKU-MATCH-P2",
                "MSKU-MATCH-P2",
                100L,
                "B-100",
                "sea",
                "in_transit",
                LocalDate.of(2026, 8, 1),
                "5"
        ));

        List<InboundRow> rows = repository(mapper).listActiveInbound(1L, "noon", "ae");

        assertEquals(1, rows.size());
        assertEquals("P1", rows.get(0).getPartnerSku());
        assertEquals(new BigDecimal("5"), rows.get(0).getRemainingQuantity());
    }

    @Test
    void ambiguousCodeWithoutHigherPriorityUniqueMatchIsSkipped() {
        FakeMapper mapper = new FakeMapper();
        mapper.productIdentities = List.of(
                identity("P1", null, null, null, null, "SHARED-PARENT", null),
                identity("P2", null, null, null, null, "SHARED-PARENT", null)
        );
        mapper.inboundLines = List.of(line(
                20L,
                null,
                null,
                "shared-parent",
                200L,
                "B-200",
                "air",
                "in_transit",
                LocalDate.of(2026, 8, 2),
                "7"
        ));

        List<InboundRow> rows = repository(mapper).listActiveInbound(1L, "noon", "ae");

        assertTrue(rows.isEmpty());
    }

    @Test
    void nullEtaRowsAreReturnedAndSameBatchRowsAreAggregated() {
        FakeMapper mapper = new FakeMapper();
        mapper.productIdentities = List.of(
                identity("P1", "P1", "P1-PSKU", "P1-OFFER", "P1-CHILD", "PARENT-1", "BAR-P1")
        );
        mapper.inboundLines = List.of(
                line(30L, "P1", null, null, 300L, "B-300", "sea", "in_transit", null, "3"),
                line(31L, "p1", null, null, 300L, "B-300", "sea", "in_transit", null, "4")
        );

        List<InboundRow> rows = repository(mapper).listActiveInbound(1L, "noon", "ae");

        assertEquals(1, rows.size());
        assertEquals("P1", rows.get(0).getPartnerSku());
        assertEquals(300L, rows.get(0).getBatchId());
        assertNull(rows.get(0).getEtaDate());
        assertEquals(new BigDecimal("7"), rows.get(0).getRemainingQuantity());
    }

    private static MyBatisReplenishmentPlanRepository repository(FakeMapper mapper) {
        return new MyBatisReplenishmentPlanRepository(mapper);
    }

    private static ProductIdentityRow identity(
            String partnerSku,
            String psoPartnerSku,
            String psoPskuCode,
            String psoOfferCode,
            String childSku,
            String skuParent,
            String barcode
    ) {
        return new ProductIdentityRow(
                partnerSku,
                psoPartnerSku,
                psoPskuCode,
                psoOfferCode,
                childSku,
                skuParent,
                barcode
        );
    }

    private static InboundLineRow line(
            Long lineId,
            String psku,
            String sku,
            String msku,
            Long batchId,
            String batchReferenceNo,
            String transportMode,
            String batchStatus,
            LocalDate etaDate,
            String remainingQuantity
    ) {
        return new InboundLineRow(
                lineId,
                psku,
                sku,
                msku,
                batchId,
                batchReferenceNo,
                transportMode,
                batchStatus,
                etaDate,
                new BigDecimal(remainingQuantity)
        );
    }

    private static final class FakeMapper implements ReplenishmentPlanMapper {
        private List<StockRow> stockRows = List.of();
        private List<InboundLineRow> inboundLines = List.of();
        private List<ProductIdentityRow> productIdentities = List.of();

        @Override
        public List<StockRow> selectFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode) {
            return stockRows;
        }

        @Override
        public List<InboundLineRow> selectActiveInboundLines(Long ownerUserId, String storeCode, String siteCode) {
            return inboundLines;
        }

        @Override
        public List<ProductIdentityRow> selectProductIdentities(Long ownerUserId, String storeCode, String siteCode) {
            return productIdentities;
        }
    }
}

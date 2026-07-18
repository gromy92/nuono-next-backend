package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundLineRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.InboundRow;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRepository.StockRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisReplenishmentPlanRepositoryTest {

    @Test
    void barcodeResolvedPartnerSkuContributesOnceAndPreservesDestination() {
        FakeMapper mapper = new FakeMapper();
        mapper.inboundLines = List.of(line(
                10L,
                " P1 ",
                "DB",
                "AE",
                "MATCHED",
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
        assertEquals("DB", rows.get(0).getDestinationCode());
        assertEquals(true, rows.get(0).isScopeResolved());
        assertEquals(new BigDecimal("5"), rows.get(0).getRemainingQuantity());
    }

    @Test
    void unresolvedDestinationIsReturnedAsBlockingEvidence() {
        FakeMapper mapper = new FakeMapper();
        mapper.inboundLines = List.of(line(
                20L,
                "P1",
                null,
                null,
                "SITE_UNRESOLVED",
                200L,
                "B-200",
                "air",
                "in_transit",
                LocalDate.of(2026, 8, 2),
                "7"
        ));

        List<InboundRow> rows = repository(mapper).listActiveInbound(1L, "noon", "ae");

        assertEquals(1, rows.size());
        assertEquals("P1", rows.get(0).getPartnerSku());
        assertEquals(false, rows.get(0).isScopeResolved());
        assertNull(rows.get(0).getDestinationCode());
    }

    @Test
    void nullEtaRowsAreReturnedAndSameBatchRowsAreAggregated() {
        FakeMapper mapper = new FakeMapper();
        mapper.inboundLines = List.of(
                line(30L, "P1", "RUH", "SA", "MATCHED", 300L, "B-300", "sea", "in_transit", null, "3"),
                line(31L, "P1", "RUH", "SA", "MATCHED", 300L, "B-300", "sea", "in_transit", null, "4")
        );

        List<InboundRow> rows = repository(mapper).listActiveInbound(1L, "noon", "ae");

        assertEquals(1, rows.size());
        assertEquals("P1", rows.get(0).getPartnerSku());
        assertEquals(300L, rows.get(0).getBatchId());
        assertEquals("RUH", rows.get(0).getDestinationCode());
        assertNull(rows.get(0).getEtaDate());
        assertEquals(new BigDecimal("7"), rows.get(0).getRemainingQuantity());
    }

    private static MyBatisReplenishmentPlanRepository repository(FakeMapper mapper) {
        return new MyBatisReplenishmentPlanRepository(mapper);
    }

    private static InboundLineRow line(
            Long lineId,
            String partnerSku,
            String destinationCode,
            String resolvedSiteCode,
            String scopeStatus,
            Long batchId,
            String batchReferenceNo,
            String transportMode,
            String batchStatus,
            LocalDate etaDate,
            String remainingQuantity
    ) {
        return new InboundLineRow(
                lineId,
                partnerSku,
                destinationCode,
                resolvedSiteCode,
                scopeStatus,
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

        @Override
        public List<StockRow> selectFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode) {
            return stockRows;
        }

        @Override
        public List<InboundLineRow> selectActiveInboundLines(Long ownerUserId, String storeCode, String siteCode) {
            return inboundLines;
        }
    }
}

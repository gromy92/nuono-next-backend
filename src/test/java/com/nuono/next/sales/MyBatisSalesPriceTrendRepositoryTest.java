package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import com.nuono.next.infrastructure.mapper.NoonOrderPriceTrendBucketRow;
import com.nuono.next.nooncompleteness.NoonSalesOrderCompletenessAudit;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisSalesPriceTrendRepositoryTest {

    @Test
    void aggregatesOrderTimeOfferPriceBucketsFromOrderLineFacts() {
        RecordingNoonOrderFactMapper mapper = new RecordingNoonOrderFactMapper();
        mapper.rows = List.of(new NoonOrderPriceTrendBucketRow(
                LocalDate.of(2026, 5, 1),
                new BigDecimal("49.990000"),
                new BigDecimal("49.990000"),
                new BigDecimal("49.990000"),
                1,
                "SAR"
        ));
        MyBatisSalesPriceTrendRepository repository = new MyBatisSalesPriceTrendRepository(mapper);

        SalesPriceTrendResult result = repository.getPriceTrend(new SalesFactQuery(
                1L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "MILKYWAYA09",
                "Z580978E7ED8F9491B50BZ-1"
        ), "day");

        assertEquals(SalesPriceTrendState.READY, result.getState().getState());
        assertEquals(1, result.getBuckets().size());
        SalesPriceTrendBucket bucket = result.getBuckets().get(0);
        assertEquals(LocalDate.of(2026, 5, 1), bucket.getBucketStart());
        assertEquals("2026-05-01", bucket.getBucketLabel());
        assertEquals(new BigDecimal("49.990000"), bucket.getAvgOfferPrice());
        assertEquals(new BigDecimal("49.990000"), bucket.getMinOfferPrice());
        assertEquals(new BigDecimal("49.990000"), bucket.getMaxOfferPrice());
        assertEquals(1, bucket.getOrderLineCount());
        assertEquals("SAR", bucket.getCurrencyCode());
        assertEquals(1L, mapper.ownerUserId);
        assertEquals("STR245027-NAE", mapper.storeCode);
        assertEquals("AE", mapper.siteCode);
        assertEquals("MILKYWAYA09", mapper.partnerSku);
        assertEquals("Z580978E7ED8F9491B50BZ-1", mapper.sku);
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), mapper.dateFromStart);
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), mapper.dateToExclusive);
    }

    private static final class RecordingNoonOrderFactMapper implements NoonOrderFactMapper {
        private List<NoonOrderPriceTrendBucketRow> rows = List.of();
        private int candidateRows = 1;
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private String partnerSku;
        private String sku;
        private LocalDateTime dateFromStart;
        private LocalDateTime dateToExclusive;

        @Override
        public void ensureNoonOrderIdSequence() {
        }

        @Override
        public void ensureOrderLineFactSequence() {
        }

        @Override
        public void ensureNoonOrderLineFactTable() {
        }

        @Override
        public void nextId(IdSequenceCommand command) {
            command.setAllocatedId(200001L);
        }

        @Override
        public int upsertOrderLineFact(Long id, NoonOrderLineFact fact) {
            return 1;
        }

        @Override
        public int markProductSiteOfferLogisticsHistoryByOrderLineFact(NoonOrderLineFact fact) {
            return 0;
        }

        @Override
        public NoonSalesOrderCompletenessAudit auditSalesOrderCompleteness(Long ownerUserId, String storeCode, String siteCode) {
            return NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated");
        }

        @Override
        public List<NoonOrderPriceTrendBucketRow> selectPriceTrendBuckets(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String sku,
                LocalDateTime dateFromStart,
                LocalDateTime dateToExclusive
        ) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.dateFromStart = dateFromStart;
            this.dateToExclusive = dateToExclusive;
            return rows;
        }

        @Override
        public int countPriceTrendCandidateRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String sku,
                LocalDateTime dateFromStart,
                LocalDateTime dateToExclusive,
                LocalDate reportDateFrom,
                LocalDate reportDateTo
        ) {
            return candidateRows;
        }
    }
}

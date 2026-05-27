package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import com.nuono.next.infrastructure.mapper.NoonOrderPriceTrendBucketRow;
import com.nuono.next.nooncompleteness.NoonSalesOrderCompletenessAudit;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class SalesPriceTrendPolicyTest {

    @Test
    void mixedCurrencyRowsReturnQualityStateWithoutMergingPrices() {
        RecordingNoonOrderFactMapper mapper = new RecordingNoonOrderFactMapper();
        mapper.candidateRows = 2;
        mapper.rows = List.of(
                row("SAR", "49.990000"),
                row("AED", "52.500000")
        );
        MyBatisSalesPriceTrendRepository repository = new MyBatisSalesPriceTrendRepository(mapper);

        SalesPriceTrendResult result = repository.getPriceTrend(defaultQuery(), "day");

        assertEquals(SalesPriceTrendState.MIXED_CURRENCY, result.getState().getState());
        assertEquals(List.of(), result.getBuckets());
    }

    @Test
    void candidateRowsWithNoValidBucketsReturnInvalidOrderPriceFacts() {
        RecordingNoonOrderFactMapper mapper = new RecordingNoonOrderFactMapper();
        mapper.candidateRows = 3;
        mapper.rows = List.of();
        MyBatisSalesPriceTrendRepository repository = new MyBatisSalesPriceTrendRepository(mapper);

        SalesPriceTrendResult result = repository.getPriceTrend(defaultQuery(), "day");

        assertEquals(SalesPriceTrendState.INVALID_ORDER_PRICE_FACTS, result.getState().getState());
        assertEquals(List.of(), result.getBuckets());
    }

    @Test
    void noCandidateRowsReturnNoOrderPriceFacts() {
        RecordingNoonOrderFactMapper mapper = new RecordingNoonOrderFactMapper();
        mapper.candidateRows = 0;
        mapper.rows = List.of();
        MyBatisSalesPriceTrendRepository repository = new MyBatisSalesPriceTrendRepository(mapper);

        SalesPriceTrendResult result = repository.getPriceTrend(defaultQuery(), "day");

        assertEquals(SalesPriceTrendState.NO_ORDER_PRICE_FACTS, result.getState().getState());
        assertEquals(List.of(), result.getBuckets());
    }

    @Test
    void mapperQueryExcludesStatusesAndMissingPriceTimestampCurrency() throws Exception {
        Method method = NoonOrderFactMapper.class.getMethod(
                "selectPriceTrendBuckets",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class
        );
        String sql = String.join("\n", method.getAnnotation(Select.class).value()).toLowerCase();

        assertTrue(sql.contains("offer_price is not null"));
        assertTrue(sql.contains("order_timestamp is not null"));
        assertTrue(sql.contains("nullif(trim(currency_code), '') is not null"));
        assertTrue(sql.contains("not like '%cancel%'"));
        assertTrue(sql.contains("not like '%failed%'"));
        assertTrue(sql.contains("not like '%could_not_be_delivered%'"));
        assertTrue(sql.contains("not like '%rejected%'"));
    }

    private static SalesFactQuery defaultQuery() {
        return new SalesFactQuery(
                1L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "MILKYWAYA09",
                "Z580978E7ED8F9491B50BZ-1"
        );
    }

    private static NoonOrderPriceTrendBucketRow row(String currencyCode, String price) {
        BigDecimal value = new BigDecimal(price);
        return new NoonOrderPriceTrendBucketRow(LocalDate.of(2026, 5, 1), value, value, value, 1, currencyCode);
    }

    private static final class RecordingNoonOrderFactMapper implements NoonOrderFactMapper {
        private List<NoonOrderPriceTrendBucketRow> rows = List.of();
        private int candidateRows;

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

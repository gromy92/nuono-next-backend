package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogisticsQuoteBasePriceFactLandingTest {

    @Test
    void shouldLandBasePriceFactWithCalculationFieldsAndSource() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(priceItem(
                "et|service|A|kg|normal",
                88010L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", "et|SA|air|headhaul|RUH",
                        "cargoCategoryKey", "et|SA|air|headhaul|RUH|A",
                        "unitPrice", "64",
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price",
                        "minimumBillableUnit", "10",
                        "minimumBillableUnitType", "kg",
                        "minimumCharge", "640",
                        "volumeDivisor", "6000",
                        "roundingRule", "ceil_1kg",
                        "priceStatus", "NORMAL",
                        "effectiveDate", "2026-04-14"
                )
        )));

        assertEquals(1, result.getInsertedCount());
        List<LogisticsPriceRuleFact> prices = repository.findPriceRulesByServiceLineKey("et|SA|air|headhaul|RUH");
        assertEquals(1, prices.size());
        LogisticsPriceRuleFact fact = prices.get(0);
        assertEquals("et", fact.getForwarderCode());
        assertEquals("et|SA|air|headhaul|RUH", fact.getServiceLineKey());
        assertEquals("et|SA|air|headhaul|RUH|A", fact.getCargoCategoryKey());
        assertEquals(new BigDecimal("64"), fact.getUnitPrice());
        assertEquals("SAR", fact.getCurrency());
        assertEquals("kg", fact.getBillingUnit());
        assertEquals("unit_price", fact.getPricingModel());
        assertEquals(new BigDecimal("10"), fact.getMinimumBillableUnit());
        assertEquals("kg", fact.getMinimumBillableUnitType());
        assertEquals(new BigDecimal("640"), fact.getMinimumCharge());
        assertEquals(new BigDecimal("6000"), fact.getVolumeDivisor());
        assertEquals("ceil_1kg", fact.getRoundingRule());
        assertTrue(fact.isComparable());
        assertEquals(88010L, fact.getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldKeepAskQuotePriceOutOfAutomaticComparison() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        publisher.land(List.of(priceItem(
                "et|service|A|kg|ask",
                88011L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", "et|SA|air|headhaul|RUH",
                        "cargoCategoryKey", "et|SA|air|headhaul|RUH|A",
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price",
                        "priceStatus", "ASK_QUOTE"
                )
        )));

        LogisticsPriceRuleFact fact = repository.findPriceRulesByServiceLineKey("et|SA|air|headhaul|RUH").get(0);
        assertFalse(fact.isComparable());
    }

    private static LogisticsQuotePublishedItem priceItem(String naturalKey, Long sourceVersionItemId, Map<String, Object> payload) {
        return new LogisticsQuotePublishedItem(
                "logistics_base_price",
                naturalKey,
                payload,
                new LogisticsQuoteFactSourceLineage(
                        "file_management",
                        20112L,
                        40104L,
                        70024L,
                        sourceVersionItemId,
                        "ET物流报价-20260414入仓生效.pdf",
                        "page 3 row 5"
                )
        );
    }

    static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    static class InMemoryFactRepository implements LogisticsQuoteFactRepository {

        final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        final List<LogisticsCargoCategoryFact> categories = new ArrayList<>();
        final List<LogisticsPriceRuleFact> prices = new ArrayList<>();

        @Override
        public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return serviceLines.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            serviceLines.add(fact);
        }

        @Override
        public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
            List<LogisticsServiceLineFact> matches = new ArrayList<>();
            for (LogisticsServiceLineFact fact : serviceLines) {
                if (LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus()) && query.matches(fact)) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        @Override
        public Optional<LogisticsCargoCategoryFact> findCargoCategoryBySourceVersionItemId(Long sourceVersionItemId) {
            return categories.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertCargoCategory(LogisticsCargoCategoryFact fact) {
            categories.add(fact);
        }

        @Override
        public List<LogisticsCargoCategoryFact> findActiveCargoCategories(String forwarderCode, String serviceLineKey) {
            return categories;
        }

        @Override
        public Optional<LogisticsPriceRuleFact> findPriceRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return prices.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertPriceRule(LogisticsPriceRuleFact fact) {
            prices.add(fact);
        }

        @Override
        public List<LogisticsPriceRuleFact> findPriceRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsPriceRuleFact> matches = new ArrayList<>();
            for (LogisticsPriceRuleFact fact : prices) {
                if (serviceLineKey.equals(fact.getServiceLineKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }
    }
}

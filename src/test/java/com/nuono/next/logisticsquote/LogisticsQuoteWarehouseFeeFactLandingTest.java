package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogisticsQuoteWarehouseFeeFactLandingTest {

    @Test
    void shouldLandWarehouseFeeExamplesWithoutCreatingTransportPrices() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        publisher.land(List.of(priceItem(88301L, "64", "et|SA|air|headhaul|RUH")));

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(
                warehouseFeeItem(null, 88302L, mapOf(
                        "forwarderCode", "et",
                        "country", "AE",
                        "warehouseNode", "DXB-FBN",
                        "serviceName", "inbound receiving",
                        "serviceType", "inbound",
                        "processingScope", "carton",
                        "feeType", "handling",
                        "pricingModel", "fixed_amount",
                        "amount", "2.5",
                        "currency", "AED",
                        "billingUnit", "carton",
                        "conditionText", "charged per inbound carton",
                        "freeCondition", "first 20 cartons free"
                )),
                warehouseFeeItem(null, 88303L, mapOf(
                        "forwarderCode", "et",
                        "country", "AE",
                        "warehouseNode", "DXB-FBN",
                        "serviceName", "picking",
                        "serviceType", "picking",
                        "processingScope", "order line",
                        "feeType", "operation",
                        "pricingModel", "fixed_amount",
                        "amount", "1.2",
                        "currency", "AED",
                        "billingUnit", "line"
                )),
                warehouseFeeItem(null, 88304L, mapOf(
                        "forwarderCode", "et",
                        "country", "AE",
                        "warehouseNode", "DXB-FBN",
                        "serviceName", "labeling",
                        "serviceType", "labeling",
                        "processingScope", "unit",
                        "feeType", "value_added_service",
                        "pricingModel", "fixed_amount",
                        "amount", "0.4",
                        "currency", "AED",
                        "billingUnit", "piece"
                )),
                warehouseFeeItem(null, 88305L, mapOf(
                        "forwarderCode", "et",
                        "country", "AE",
                        "warehouseNode", "DXB-FBN",
                        "serviceName", "monthly storage",
                        "serviceType", "storage",
                        "processingScope", "cbm",
                        "feeType", "storage",
                        "pricingModel", "fixed_plus_rate",
                        "amount", "35",
                        "rate", "0.05",
                        "currency", "AED",
                        "billingUnit", "cbm_month",
                        "conditionText", "charged after free storage period",
                        "freeCondition", "free for first 14 days"
                ))
        ));

        assertEquals(4, result.getInsertedCount());
        assertEquals(1, repository.findPriceRulesByServiceLineKey("et|SA|air|headhaul|RUH").size());
        List<LogisticsWarehouseFeeRuleFact> fees = repository.findWarehouseFeeRulesByWarehouseNode("AE", "DXB-FBN");
        assertEquals(4, fees.size());

        LogisticsWarehouseFeeRuleFact inbound = fees.stream()
                .filter(fee -> "inbound".equals(fee.getServiceType()))
                .findFirst()
                .orElseThrow();
        assertEquals("et|AE|DXB-FBN|inbound receiving|inbound", inbound.getNaturalKey());
        assertEquals("et", inbound.getForwarderCode());
        assertEquals("AE", inbound.getCountry());
        assertEquals("DXB-FBN", inbound.getWarehouseNode());
        assertEquals("inbound receiving", inbound.getServiceName());
        assertEquals("carton", inbound.getProcessingScope());
        assertEquals("handling", inbound.getFeeType());
        assertEquals("fixed_amount", inbound.getPricingModel());
        assertEquals(new BigDecimal("2.5"), inbound.getAmount());
        assertEquals("AED", inbound.getCurrency());
        assertEquals("carton", inbound.getBillingUnit());
        assertEquals("charged per inbound carton", inbound.getConditionText());
        assertEquals("first 20 cartons free", inbound.getFreeCondition());
        assertEquals(88302L, inbound.getSourceLineage().getSourceVersionItemId());

        LogisticsWarehouseFeeRuleFact storage = fees.stream()
                .filter(fee -> "storage".equals(fee.getServiceType()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("0.05"), storage.getRate());
        assertEquals("free for first 14 days", storage.getFreeCondition());
    }

    @Test
    void shouldSkipInvalidWarehouseFeeRows() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(warehouseFeeItem(
                "invalid-warehouse-fee",
                88311L,
                mapOf(
                        "forwarderCode", "et",
                        "country", "AE",
                        "warehouseNode", "DXB-FBN",
                        "amount", "1.2",
                        "currency", "AED",
                        "billingUnit", "piece"
                )
        )));

        assertEquals(1, result.getSkippedCount());
        assertTrue(repository.findWarehouseFeeRulesByWarehouseNode("AE", "DXB-FBN").isEmpty());
    }

    private static LogisticsQuotePublishedItem priceItem(Long sourceVersionItemId, String unitPrice, String serviceLineKey) {
        return new LogisticsQuotePublishedItem(
                "logistics_base_price",
                serviceLineKey + "|general|kg|unit_price",
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "cargoCategoryKey", serviceLineKey + "|general",
                        "unitPrice", unitPrice,
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price"
                ),
                lineage(sourceVersionItemId)
        );
    }

    private static LogisticsQuotePublishedItem warehouseFeeItem(
            String naturalKey,
            Long sourceVersionItemId,
            Map<String, Object> payload
    ) {
        return new LogisticsQuotePublishedItem(
                "logistics_warehouse_service_fee",
                naturalKey,
                payload,
                lineage(sourceVersionItemId)
        );
    }

    private static LogisticsQuoteFactSourceLineage lineage(Long sourceVersionItemId) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20112L,
                40104L,
                70024L,
                sourceVersionItemId,
                "ET物流报价-20260414入仓生效.pdf",
                "page 6 row " + sourceVersionItemId
        );
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class InMemoryFactRepository implements LogisticsQuoteFactRepository {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsPriceRuleFact> prices = new ArrayList<>();
        private final List<LogisticsWarehouseFeeRuleFact> warehouseFees = new ArrayList<>();

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
            return serviceLines;
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

        @Override
        public Optional<LogisticsWarehouseFeeRuleFact> findWarehouseFeeRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return warehouseFees.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertWarehouseFeeRule(LogisticsWarehouseFeeRuleFact fact) {
            warehouseFees.add(fact);
        }

        @Override
        public List<LogisticsWarehouseFeeRuleFact> findWarehouseFeeRulesByWarehouseNode(String country, String warehouseNode) {
            List<LogisticsWarehouseFeeRuleFact> matches = new ArrayList<>();
            for (LogisticsWarehouseFeeRuleFact fact : warehouseFees) {
                if (country.equals(fact.getCountry()) && warehouseNode.equals(fact.getWarehouseNode())) {
                    matches.add(fact);
                }
            }
            return matches;
        }
    }
}

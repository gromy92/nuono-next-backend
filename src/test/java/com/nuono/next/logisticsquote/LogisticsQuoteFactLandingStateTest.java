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

class LogisticsQuoteFactLandingStateTest {

    @Test
    void shouldKeepSamePublishedBatchIdempotentAcrossServiceLineCategoryAndPrice() {
        StateAwareFactRepository repository = new StateAwareFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        List<LogisticsQuotePublishedItem> batch = List.of(
                serviceLineItem("yite|SA|air|headhaul|RUH", 88001L),
                categoryItem("yite|SA|air|headhaul|RUH|general", 88002L),
                priceItem("yite|SA|air|headhaul|RUH|general|kg|unit_price", 88003L, "64", "yite|SA|air|headhaul|RUH")
        );

        LogisticsQuoteFactLandingResult first = publisher.land(batch);
        LogisticsQuoteFactLandingResult second = publisher.land(batch);

        assertEquals(3, first.getInsertedCount());
        assertEquals(0, first.getUnchangedCount());
        assertEquals(0, second.getInsertedCount());
        assertEquals(3, second.getUnchangedCount());
        assertEquals(1, repository.activeServiceLinesByKey("yite|SA|air|headhaul|RUH").size());
        assertEquals(1, repository.activeCategoriesByKey("yite|SA|air|headhaul|RUH|general").size());
        assertEquals(1, repository.activePricesByKey("yite|SA|air|headhaul|RUH|general|kg|unit_price").size());
    }

    @Test
    void shouldSupersedeSameNaturalKeyWithoutTouchingOtherForwardersOrServiceLines() {
        StateAwareFactRepository repository = new StateAwareFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String yiteKey = "yite|SA|air|headhaul|RUH|general|kg|unit_price";
        String etKey = "et|SA|air|headhaul|RUH|general|kg|unit_price";
        String yiteSeaKey = "yite|SA|sea|headhaul|JED|general|kg|unit_price";

        publisher.land(List.of(
                priceItem(yiteKey, 88010L, "64", "yite|SA|air|headhaul|RUH"),
                priceItem(etKey, 88011L, "63", "et|SA|air|headhaul|RUH"),
                priceItem(yiteSeaKey, 88012L, "28", "yite|SA|sea|headhaul|JED")
        ));

        LogisticsQuoteFactLandingResult update = publisher.land(List.of(
                priceItem(yiteKey, 88020L, "61", "yite|SA|air|headhaul|RUH")
        ));

        assertEquals(1, update.getInsertedCount());
        assertEquals(1, update.getSupersededCount());
        assertEquals(1, repository.activePricesByKey(yiteKey).size());
        assertEquals(new BigDecimal("61"), repository.activePricesByKey(yiteKey).get(0).getUnitPrice());
        assertEquals(2, repository.allPricesByKey(yiteKey).size());
        assertTrue(repository.allPricesByKey(yiteKey).stream()
                .anyMatch(fact -> LogisticsQuoteFactStatus.SUPERSEDED.value().equals(fact.getStatus())
                        && new BigDecimal("64").compareTo(fact.getUnitPrice()) == 0));
        assertEquals(1, repository.activePricesByKey(etKey).size());
        assertEquals(new BigDecimal("63"), repository.activePricesByKey(etKey).get(0).getUnitPrice());
        assertEquals(1, repository.activePricesByKey(yiteSeaKey).size());
        assertEquals(new BigDecimal("28"), repository.activePricesByKey(yiteSeaKey).get(0).getUnitPrice());
    }

    @Test
    void shouldRecordConflictForIncompatibleSameNaturalKeyInOneLandingOperation() {
        StateAwareFactRepository repository = new StateAwareFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String naturalKey = "et|SA|air|headhaul|RUH|general|kg|unit_price";

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(
                priceItem(naturalKey, 88031L, "64", "et|SA|air|headhaul|RUH"),
                priceItem(naturalKey, 88032L, "62", "et|SA|air|headhaul|RUH")
        ));

        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getConflictCount());
        assertEquals(1, repository.activePricesByKey(naturalKey).size());
        assertEquals(new BigDecimal("64"), repository.activePricesByKey(naturalKey).get(0).getUnitPrice());
        assertEquals(1, repository.conflictPricesByKey(naturalKey).size());
        assertEquals(new BigDecimal("62"), repository.conflictPricesByKey(naturalKey).get(0).getUnitPrice());
    }

    @Test
    void shouldSkipInvalidFactsBeforeChangingExistingActiveFacts() {
        StateAwareFactRepository repository = new StateAwareFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String naturalKey = "qike|SA|air|headhaul|RUH|general|kg|unit_price";
        publisher.land(List.of(priceItem(naturalKey, 88041L, "63", "qike|SA|air|headhaul|RUH")));

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(new LogisticsQuotePublishedItem(
                "logistics_base_price",
                naturalKey,
                mapOf(
                        "serviceLineKey", "qike|SA|air|headhaul|RUH",
                        "cargoCategoryKey", "qike|SA|air|headhaul|RUH|general",
                        "unitPrice", "60",
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price"
                ),
                lineage(88042L)
        )));

        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getSupersededCount());
        assertEquals(1, repository.activePricesByKey(naturalKey).size());
        assertEquals(new BigDecimal("63"), repository.activePricesByKey(naturalKey).get(0).getUnitPrice());
    }

    @Test
    void shouldSkipServiceLineWhenCoreDimensionsAreMissingEvenIfNaturalKeyIsProvided() {
        StateAwareFactRepository repository = new StateAwareFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(new LogisticsQuotePublishedItem(
                "logistics_service_line",
                "missing-forwarder|SA|air|headhaul|RUH",
                mapOf(
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "destinationNode", "RUH"
                ),
                lineage(88051L)
        )));

        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getInsertedCount());
        assertTrue(repository.serviceLines.isEmpty());
    }

    private static LogisticsQuotePublishedItem serviceLineItem(String naturalKey, Long sourceVersionItemId) {
        return new LogisticsQuotePublishedItem(
                "logistics_service_line",
                naturalKey,
                mapOf(
                        "forwarderCode", "yite",
                        "forwarderName", "义特物流",
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "destinationNode", "RUH"
                ),
                lineage(sourceVersionItemId)
        );
    }

    private static LogisticsQuotePublishedItem categoryItem(String naturalKey, Long sourceVersionItemId) {
        return new LogisticsQuotePublishedItem(
                "logistics_cargo_category",
                naturalKey,
                mapOf(
                        "forwarderCode", "yite",
                        "serviceLineKey", "yite|SA|air|headhaul|RUH",
                        "categoryCode", "general",
                        "categoryName", "普货"
                ),
                lineage(sourceVersionItemId)
        );
    }

    private static LogisticsQuotePublishedItem priceItem(
            String naturalKey,
            Long sourceVersionItemId,
            String unitPrice,
            String serviceLineKey
    ) {
        return new LogisticsQuotePublishedItem(
                "logistics_base_price",
                naturalKey,
                mapOf(
                        "forwarderCode", naturalKey.substring(0, naturalKey.indexOf('|')),
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

    private static LogisticsQuoteFactSourceLineage lineage(Long sourceVersionItemId) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20112L,
                40104L,
                70024L,
                sourceVersionItemId,
                "ET物流报价-20260414入仓生效.pdf",
                "page 1 row " + sourceVersionItemId
        );
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class StateAwareFactRepository implements LogisticsQuoteFactRepository {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsCargoCategoryFact> categories = new ArrayList<>();
        private final List<LogisticsPriceRuleFact> prices = new ArrayList<>();

        @Override
        public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return serviceLines.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public Optional<LogisticsCargoCategoryFact> findCargoCategoryBySourceVersionItemId(Long sourceVersionItemId) {
            return categories.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public Optional<LogisticsPriceRuleFact> findPriceRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return prices.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(LogisticsQuoteFactType factType, String naturalKey) {
            if (LogisticsQuoteFactType.SERVICE_LINE == factType) {
                return !activeServiceLinesByKey(naturalKey).isEmpty();
            }
            if (LogisticsQuoteFactType.CARGO_CATEGORY == factType) {
                return !activeCategoriesByKey(naturalKey).isEmpty();
            }
            if (LogisticsQuoteFactType.PRICE_RULE == factType) {
                return !activePricesByKey(naturalKey).isEmpty();
            }
            return false;
        }

        @Override
        public void supersedeActiveFacts(LogisticsQuoteFactType factType, String naturalKey) {
            if (LogisticsQuoteFactType.SERVICE_LINE == factType) {
                replaceServiceLines(naturalKey, LogisticsQuoteFactStatus.ACTIVE.value(), LogisticsQuoteFactStatus.SUPERSEDED.value());
            }
            if (LogisticsQuoteFactType.CARGO_CATEGORY == factType) {
                replaceCategories(naturalKey, LogisticsQuoteFactStatus.ACTIVE.value(), LogisticsQuoteFactStatus.SUPERSEDED.value());
            }
            if (LogisticsQuoteFactType.PRICE_RULE == factType) {
                replacePrices(naturalKey, LogisticsQuoteFactStatus.ACTIVE.value(), LogisticsQuoteFactStatus.SUPERSEDED.value());
            }
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            serviceLines.add(fact);
        }

        @Override
        public void insertCargoCategory(LogisticsCargoCategoryFact fact) {
            categories.add(fact);
        }

        @Override
        public void insertPriceRule(LogisticsPriceRuleFact fact) {
            prices.add(fact);
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

        List<LogisticsServiceLineFact> activeServiceLinesByKey(String naturalKey) {
            List<LogisticsServiceLineFact> matches = new ArrayList<>();
            for (LogisticsServiceLineFact fact : serviceLines) {
                if (naturalKey.equals(fact.getNaturalKey()) && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        List<LogisticsCargoCategoryFact> activeCategoriesByKey(String naturalKey) {
            List<LogisticsCargoCategoryFact> matches = new ArrayList<>();
            for (LogisticsCargoCategoryFact fact : categories) {
                if (naturalKey.equals(fact.getNaturalKey()) && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        List<LogisticsPriceRuleFact> activePricesByKey(String naturalKey) {
            List<LogisticsPriceRuleFact> matches = new ArrayList<>();
            for (LogisticsPriceRuleFact fact : prices) {
                if (naturalKey.equals(fact.getNaturalKey()) && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        List<LogisticsPriceRuleFact> conflictPricesByKey(String naturalKey) {
            List<LogisticsPriceRuleFact> matches = new ArrayList<>();
            for (LogisticsPriceRuleFact fact : prices) {
                if (naturalKey.equals(fact.getNaturalKey()) && LogisticsQuoteFactStatus.CONFLICT.value().equals(fact.getStatus())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        List<LogisticsPriceRuleFact> allPricesByKey(String naturalKey) {
            List<LogisticsPriceRuleFact> matches = new ArrayList<>();
            for (LogisticsPriceRuleFact fact : prices) {
                if (naturalKey.equals(fact.getNaturalKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        private void replaceServiceLines(String naturalKey, String fromStatus, String toStatus) {
            for (int i = 0; i < serviceLines.size(); i++) {
                LogisticsServiceLineFact fact = serviceLines.get(i);
                if (naturalKey.equals(fact.getNaturalKey()) && fromStatus.equals(fact.getStatus())) {
                    serviceLines.set(i, new LogisticsServiceLineFact(
                            fact.getNaturalKey(),
                            fact.getForwarderCode(),
                            fact.getForwarderName(),
                            fact.getCountry(),
                            fact.getFulfillmentMode(),
                            fact.getDestinationNode(),
                            fact.getTransportMode(),
                            fact.getServiceScope(),
                            fact.getChannelName(),
                            fact.getOriginWarehouse(),
                            fact.getDestinationWarehouse(),
                            fact.getDepartureFrequency(),
                            fact.getEstimatedDaysMin(),
                            fact.getEstimatedDaysMax(),
                            fact.getEffectiveFrom(),
                            toStatus,
                            fact.getSourceLineage()
                    ));
                }
            }
        }

        private void replaceCategories(String naturalKey, String fromStatus, String toStatus) {
            for (int i = 0; i < categories.size(); i++) {
                LogisticsCargoCategoryFact fact = categories.get(i);
                if (naturalKey.equals(fact.getNaturalKey()) && fromStatus.equals(fact.getStatus())) {
                    categories.set(i, new LogisticsCargoCategoryFact(
                            fact.getNaturalKey(),
                            fact.getForwarderCode(),
                            fact.getServiceLineKey(),
                            fact.getCategoryCode(),
                            fact.getCategoryName(),
                            fact.getSourceCategoryName(),
                            fact.getProductExamples(),
                            fact.getKeywords(),
                            fact.getElectricType(),
                            fact.getSensitiveTags(),
                            fact.getPackingPolicy(),
                            fact.isManualConfirmRequired(),
                            toStatus,
                            fact.getSourceLineage()
                    ));
                }
            }
        }

        private void replacePrices(String naturalKey, String fromStatus, String toStatus) {
            for (int i = 0; i < prices.size(); i++) {
                LogisticsPriceRuleFact fact = prices.get(i);
                if (naturalKey.equals(fact.getNaturalKey()) && fromStatus.equals(fact.getStatus())) {
                    prices.set(i, new LogisticsPriceRuleFact(
                            fact.getNaturalKey(),
                            fact.getForwarderCode(),
                            fact.getServiceLineKey(),
                            fact.getCargoCategoryKey(),
                            fact.getUnitPrice(),
                            fact.getCurrency(),
                            fact.getBillingUnit(),
                            fact.getPricingModel(),
                            fact.getMinimumBillableUnit(),
                            fact.getMinimumBillableUnitType(),
                            fact.getMinimumCharge(),
                            fact.getVolumeDivisor(),
                            fact.getSeaWeightRatio(),
                            fact.getRoundingRule(),
                            fact.getPriceStatus(),
                            fact.getEffectiveFrom(),
                            toStatus,
                            fact.getSourceLineage()
                    ));
                }
            }
        }
    }
}

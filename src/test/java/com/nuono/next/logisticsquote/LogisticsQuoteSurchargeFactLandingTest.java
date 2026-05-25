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

class LogisticsQuoteSurchargeFactLandingTest {

    @Test
    void shouldLandSurchargeFactAndKeepBasePriceUnchanged() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String serviceLineKey = "et|SA|FBN|air|headhaul|RUH";
        publisher.land(List.of(priceItem(88101L, "64", serviceLineKey)));

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(surchargeItem(
                null,
                88102L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "surchargeName", "fuel surcharge",
                        "surchargeType", "fuel",
                        "triggerCondition", "fuel index adjustment",
                        "pricingModel", "fixed_plus_rate",
                        "amount", "12.5",
                        "rate", "0.04",
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "minimumCharge", "50",
                        "includedInBasePrice", true
                )
        )));

        assertEquals(1, result.getInsertedCount());
        assertEquals(1, repository.findPriceRulesByServiceLineKey(serviceLineKey).size());
        assertEquals(new BigDecimal("64"), repository.findPriceRulesByServiceLineKey(serviceLineKey).get(0).getUnitPrice());

        List<LogisticsSurchargeRuleFact> surcharges = repository.findSurchargeRulesByServiceLineKey(serviceLineKey);
        assertEquals(1, surcharges.size());
        LogisticsSurchargeRuleFact fact = surcharges.get(0);
        assertEquals("et|et|SA|FBN|air|headhaul|RUH|fuel surcharge", fact.getNaturalKey());
        assertEquals("et", fact.getForwarderCode());
        assertEquals(serviceLineKey, fact.getServiceLineKey());
        assertEquals("fuel surcharge", fact.getSurchargeName());
        assertEquals("fuel", fact.getSurchargeType());
        assertEquals("fuel index adjustment", fact.getTriggerCondition());
        assertEquals("fixed_plus_rate", fact.getPricingModel());
        assertEquals(new BigDecimal("12.5"), fact.getAmount());
        assertEquals(new BigDecimal("0.04"), fact.getRate());
        assertEquals("SAR", fact.getCurrency());
        assertEquals("kg", fact.getBillingUnit());
        assertEquals(new BigDecimal("50"), fact.getMinimumCharge());
        assertTrue(fact.isIncludedInBasePrice());
        assertEquals(LogisticsQuoteFactStatus.ACTIVE.value(), fact.getStatus());
        assertEquals(88102L, fact.getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldSkipSurchargeWhenFeeIdentityOrTriggerConditionIsMissing() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String serviceLineKey = "et|SA|FBN|air|headhaul|RUH";
        publisher.land(List.of(surchargeItem(
                "et|SA|fuel",
                88111L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "surchargeName", "fuel surcharge",
                        "triggerCondition", "fuel index adjustment",
                        "amount", "12.5",
                        "currency", "SAR",
                        "billingUnit", "kg"
                )
        )));

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(surchargeItem(
                "et|SA|fuel",
                88112L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "surchargeName", "fuel surcharge",
                        "amount", "13",
                        "currency", "SAR",
                        "billingUnit", "kg"
                )
        )));

        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getSupersededCount());
        assertEquals(1, repository.findSurchargeRulesByServiceLineKey(serviceLineKey).size());
        assertEquals(new BigDecimal("12.5"), repository.findSurchargeRulesByServiceLineKey(serviceLineKey).get(0).getAmount());
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

    private static LogisticsQuotePublishedItem surchargeItem(
            String naturalKey,
            Long sourceVersionItemId,
            Map<String, Object> payload
    ) {
        return new LogisticsQuotePublishedItem(
                "logistics_surcharge",
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
                "page 4 row " + sourceVersionItemId
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
        private final List<LogisticsSurchargeRuleFact> surcharges = new ArrayList<>();

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
        public Optional<LogisticsSurchargeRuleFact> findSurchargeRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return surcharges.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertSurchargeRule(LogisticsSurchargeRuleFact fact) {
            surcharges.add(fact);
        }

        @Override
        public List<LogisticsSurchargeRuleFact> findSurchargeRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsSurchargeRuleFact> matches = new ArrayList<>();
            for (LogisticsSurchargeRuleFact fact : surcharges) {
                if (serviceLineKey.equals(fact.getServiceLineKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(LogisticsQuoteFactType factType, String naturalKey) {
            if (LogisticsQuoteFactType.SURCHARGE_RULE != factType) {
                return false;
            }
            return surcharges.stream()
                    .anyMatch(fact -> naturalKey.equals(fact.getNaturalKey())
                            && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus()));
        }

        @Override
        public void supersedeActiveFacts(LogisticsQuoteFactType factType, String naturalKey) {
            if (LogisticsQuoteFactType.SURCHARGE_RULE != factType) {
                return;
            }
            for (int i = 0; i < surcharges.size(); i++) {
                LogisticsSurchargeRuleFact fact = surcharges.get(i);
                if (naturalKey.equals(fact.getNaturalKey()) && LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    surcharges.set(i, new LogisticsSurchargeRuleFact(
                            fact.getNaturalKey(),
                            fact.getForwarderCode(),
                            fact.getServiceLineKey(),
                            fact.getSurchargeName(),
                            fact.getSurchargeType(),
                            fact.getTriggerCondition(),
                            fact.getPricingModel(),
                            fact.getAmount(),
                            fact.getRate(),
                            fact.getCurrency(),
                            fact.getBillingUnit(),
                            fact.getMinimumCharge(),
                            fact.isIncludedInBasePrice(),
                            LogisticsQuoteFactStatus.SUPERSEDED.value(),
                            fact.getSourceLineage()
                    ));
                }
            }
        }
    }
}

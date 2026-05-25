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

class LogisticsQuoteBillingAndRestrictionFactLandingTest {

    @Test
    void shouldLandBillingRuleWithStructuredConditionAndSource() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String serviceLineKey = "et|SA|FBN|air|headhaul|RUH";

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(billingRuleItem(
                null,
                88201L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "cargoCategoryKey", serviceLineKey + "|general",
                        "ruleName", "volume weight divisor",
                        "ruleType", "volume_weight",
                        "conditionText", "volumetric weight = L*W*H/6000",
                        "structuredField", "volumeDivisor",
                        "operator", "=",
                        "thresholdValue", "6000",
                        "thresholdUnit", "cm3/kg",
                        "actionText", "charge by max(actual weight, volumetric weight)",
                        "severity", "info"
                )
        )));

        assertEquals(1, result.getInsertedCount());
        List<LogisticsBillingRuleFact> rules = repository.findBillingRulesByServiceLineKey(serviceLineKey);
        assertEquals(1, rules.size());
        LogisticsBillingRuleFact fact = rules.get(0);
        assertEquals("et|et|SA|FBN|air|headhaul|RUH|et|SA|FBN|air|headhaul|RUH|general|volume weight divisor", fact.getNaturalKey());
        assertEquals("et", fact.getForwarderCode());
        assertEquals(serviceLineKey, fact.getServiceLineKey());
        assertEquals(serviceLineKey + "|general", fact.getCargoCategoryKey());
        assertEquals("volume weight divisor", fact.getRuleName());
        assertEquals("volume_weight", fact.getRuleType());
        assertEquals("volumetric weight = L*W*H/6000", fact.getConditionText());
        assertEquals("volumeDivisor", fact.getStructuredField());
        assertEquals("=", fact.getOperator());
        assertEquals(new BigDecimal("6000"), fact.getThresholdValue());
        assertEquals("cm3/kg", fact.getThresholdUnit());
        assertEquals("charge by max(actual weight, volumetric weight)", fact.getActionText());
        assertEquals("info", fact.getSeverity());
        assertEquals(LogisticsQuoteFactStatus.ACTIVE.value(), fact.getStatus());
        assertEquals(88201L, fact.getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldLandRestrictionsAndDistinguishHardWarningAndInfoSeverity() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String serviceLineKey = "et|SA|FBN|air|headhaul|RUH";

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(
                restrictionItem(null, 88211L, mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "restrictionType", "prohibited_goods",
                        "itemText", "battery liquid",
                        "requirementText", "not accepted by air service",
                        "applicabilityScope", "air",
                        "severity", "hard",
                        "manualConfirmRequired", true
                )),
                restrictionItem(null, 88212L, mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "restrictionType", "dimension_limit",
                        "itemText", "single side over 120cm",
                        "requirementText", "manual confirmation before booking",
                        "applicabilityScope", "oversize cargo",
                        "severity", "warning"
                )),
                restrictionItem(null, 88213L, mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "restrictionType", "document_note",
                        "itemText", "commercial invoice required",
                        "requirementText", "provide invoice with shipment",
                        "applicabilityScope", "all cargo",
                        "severity", "info"
                ))
        ));

        assertEquals(3, result.getInsertedCount());
        List<LogisticsRestrictionRuleFact> restrictions = repository.findRestrictionRulesByServiceLineKey(serviceLineKey);
        assertEquals(3, restrictions.size());
        assertEquals(1, restrictions.stream().filter(LogisticsRestrictionRuleFact::isHardRestriction).count());
        assertEquals(1, restrictions.stream().filter(LogisticsRestrictionRuleFact::isWarning).count());
        assertEquals(1, restrictions.stream().filter(LogisticsRestrictionRuleFact::isInformational).count());

        LogisticsRestrictionRuleFact hard = restrictions.stream()
                .filter(LogisticsRestrictionRuleFact::isHardRestriction)
                .findFirst()
                .orElseThrow();
        assertEquals("prohibited_goods", hard.getRestrictionType());
        assertEquals("battery liquid", hard.getItemText());
        assertEquals("not accepted by air service", hard.getRequirementText());
        assertEquals("air", hard.getApplicabilityScope());
        assertTrue(hard.isManualConfirmRequired());
        assertEquals(88211L, hard.getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldSkipInvalidBillingAndRestrictionRows() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        String serviceLineKey = "et|SA|FBN|air|headhaul|RUH";

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(
                billingRuleItem("invalid-billing", 88221L, mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "conditionText", "missing rule name"
                )),
                restrictionItem("invalid-restriction", 88222L, mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", serviceLineKey,
                        "restrictionType", "prohibited_goods",
                        "severity", "hard"
                ))
        ));

        assertEquals(2, result.getSkippedCount());
        assertTrue(repository.findBillingRulesByServiceLineKey(serviceLineKey).isEmpty());
        assertTrue(repository.findRestrictionRulesByServiceLineKey(serviceLineKey).isEmpty());
    }

    private static LogisticsQuotePublishedItem billingRuleItem(String naturalKey, Long sourceVersionItemId, Map<String, Object> payload) {
        return new LogisticsQuotePublishedItem(
                "logistics_billing_rule",
                naturalKey,
                payload,
                lineage(sourceVersionItemId)
        );
    }

    private static LogisticsQuotePublishedItem restrictionItem(String naturalKey, Long sourceVersionItemId, Map<String, Object> payload) {
        return new LogisticsQuotePublishedItem(
                "logistics_restriction",
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
                "page 5 row " + sourceVersionItemId
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
        private final List<LogisticsBillingRuleFact> billingRules = new ArrayList<>();
        private final List<LogisticsRestrictionRuleFact> restrictions = new ArrayList<>();

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
        public Optional<LogisticsBillingRuleFact> findBillingRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return billingRules.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertBillingRule(LogisticsBillingRuleFact fact) {
            billingRules.add(fact);
        }

        @Override
        public List<LogisticsBillingRuleFact> findBillingRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsBillingRuleFact> matches = new ArrayList<>();
            for (LogisticsBillingRuleFact fact : billingRules) {
                if (serviceLineKey.equals(fact.getServiceLineKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        @Override
        public Optional<LogisticsRestrictionRuleFact> findRestrictionRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return restrictions.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertRestrictionRule(LogisticsRestrictionRuleFact fact) {
            restrictions.add(fact);
        }

        @Override
        public List<LogisticsRestrictionRuleFact> findRestrictionRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsRestrictionRuleFact> matches = new ArrayList<>();
            for (LogisticsRestrictionRuleFact fact : restrictions) {
                if (serviceLineKey.equals(fact.getServiceLineKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }
    }
}

package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeFactPublisherTest {

    @Test
    void shouldLandStructuredOutboundFactsWithLineage() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(
                publishedItem("outbound_size_classification_rule", "KSA|NOON|FBN|Small Envelope|2026-05-24",
                        Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN", "classificationName", "Small Envelope",
                                "longestSideMaxCm", 20, "medianSideMaxCm", 15, "shortestSideMaxCm", 2,
                                "maxShippingWeightGrams", 250, "packagingWeightGrams", 20), 88061L),
                publishedItem("outbound_fee_weight_slab_rule", "KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24",
                        Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN", "classificationName", "Small Envelope",
                                "weightMinGrams", 0, "weightMinInclusive", true, "weightMaxGrams", 250, "weightMaxInclusive", true,
                                "standardFeeAmount", 6.25, "currency", "SAR"), 88062L),
                publishedItem("outbound_fee_calculation_policy", "KSA|NOON|FBN|2026-05-24",
                        Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN",
                                "shippingWeightFormula", "physical_weight_plus_packaging_weight",
                                "salesPriceThresholdAmount", 25, "thresholdCurrency", "SAR"), 88063L)
        ));

        assertEquals(3, result.getInsertedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, repository.classifications.size());
        assertEquals(1, repository.slabs.size());
        assertEquals(1, repository.policies.size());
        assertEquals("ACTIVE", repository.classifications.get(0).getStatus());
        assertEquals(88061L, repository.classifications.get(0).getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldLandUnboundedBulkySizeClassification() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", "Bulky");
        payload.put("longestSideMaxCm", null);
        payload.put("medianSideMaxCm", null);
        payload.put("shortestSideMaxCm", null);
        payload.put("maxShippingWeightGrams", null);
        payload.put("packagingWeightGrams", 400);
        payload.put("priority", 7);
        payload.put("dimensionUnit", "cm");
        payload.put("weightUnit", "grams");

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(
                publishedItem("outbound_size_classification_rule", "KSA|NOON|FBN|Bulky|2025-09-01", payload, 88064L)
        ));

        assertEquals(1, result.getInsertedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, repository.classifications.size());
        assertEquals("Bulky", repository.classifications.get(0).getClassificationName());
        assertNull(repository.classifications.get(0).getMaxShippingWeightGrams());
    }

    @Test
    void shouldBeIdempotentBySourceVersionItemAndSkipInvalidItems() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);
        OfficialOutboundFeePublishedItem valid = publishedItem(
                "outbound_fee_weight_slab_rule",
                "KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24",
                Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN", "classificationName", "Small Envelope",
                        "weightMaxGrams", 250, "standardFeeAmount", 6.25, "currency", "SAR"),
                88062L
        );
        OfficialOutboundFeePublishedItem invalid = publishedItem(
                "outbound_fee_weight_slab_rule",
                "missing-fee",
                Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN", "classificationName", "Small Envelope"),
                88064L
        );

        OfficialOutboundFeeFactLandingResult first = publisher.land(List.of(valid, invalid));
        OfficialOutboundFeeFactLandingResult second = publisher.land(List.of(valid));

        assertEquals(1, first.getInsertedCount());
        assertEquals(1, first.getSkippedCount());
        assertEquals(1, second.getUnchangedCount());
        assertEquals(1, repository.slabs.size());
    }

    @Test
    void shouldMarkSameOperationNaturalKeyConflictWhenPayloadDiffers() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(
                publishedItem("outbound_fee_calculation_policy", "KSA|NOON|FBN|2026-05-24",
                        Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN",
                                "shippingWeightFormula", "physical_weight_plus_packaging_weight"), 88071L),
                publishedItem("outbound_fee_calculation_policy", "KSA|NOON|FBN|2026-05-24",
                        Map.of("country", "KSA", "platform", "NOON", "fulfillmentType", "FBN",
                                "shippingWeightFormula", "unknown"), 88072L)
        ));

        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getConflictCount());
        assertEquals("ACTIVE", repository.policies.get(0).getStatus());
        assertEquals("CONFLICT", repository.policies.get(1).getStatus());
    }

    private static OfficialOutboundFeePublishedItem publishedItem(
            String itemType,
            String naturalKey,
            Map<String, Object> payload,
            Long sourceVersionItemId
    ) {
        return new OfficialOutboundFeePublishedItem(
                itemType,
                naturalKey,
                payload,
                new OfficialOutboundFeeSourceLineage(
                        "file_management",
                        20114L,
                        40114L,
                        70014L,
                        sourceVersionItemId,
                        "fbn-fees.pdf",
                        "version_item:" + sourceVersionItemId
                )
        );
    }

    private static final class InMemoryRepository implements OfficialOutboundFeeFactRepository {

        private final List<OutboundSizeClassificationRuleFact> classifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> slabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
            return classifications.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
            classifications.add(fact);
        }

        @Override
        public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
            return slabs.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
            slabs.add(fact);
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
            return policies.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
            policies.add(fact);
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
            return facts(factType).stream().anyMatch(fact -> naturalKey.equals(fact.naturalKey()) && "ACTIVE".equals(fact.status()));
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
            facts(factType).stream()
                    .filter(fact -> naturalKey.equals(fact.naturalKey()) && "ACTIVE".equals(fact.status()))
                    .forEach(OfficialFactView::supersede);
        }

        private List<OfficialFactView> facts(OfficialOutboundFeeFactType factType) {
            List<OfficialFactView> facts = new ArrayList<>();
            if (OfficialOutboundFeeFactType.SIZE_CLASSIFICATION == factType) {
                classifications.forEach(fact -> facts.add(new OfficialFactView(fact)));
            } else if (OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB == factType) {
                slabs.forEach(fact -> facts.add(new OfficialFactView(fact)));
            } else if (OfficialOutboundFeeFactType.CALCULATION_POLICY == factType) {
                policies.forEach(fact -> facts.add(new OfficialFactView(fact)));
            }
            return facts;
        }
    }

    private static final class OfficialFactView {
        private final Object fact;

        private OfficialFactView(Object fact) {
            this.fact = fact;
        }

        private String naturalKey() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getNaturalKey();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getNaturalKey();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getNaturalKey();
        }

        private String status() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getStatus();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getStatus();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getStatus();
        }

        private void supersede() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                ((OutboundSizeClassificationRuleFact) fact).setStatus("SUPERSEDED");
            } else if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                ((OutboundFeeWeightSlabRuleFact) fact).setStatus("SUPERSEDED");
            } else {
                ((OutboundFeeCalculationPolicyFact) fact).setStatus("SUPERSEDED");
            }
        }
    }
}

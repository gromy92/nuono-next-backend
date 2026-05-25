package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeFactLandingStateTest {

    @Test
    void publishingUaeVersionShouldNotSupersedeKsaActiveFacts() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.slabs.add(slab("KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24", "ACTIVE", 87001L));
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(publishedSlab(
                "UAE|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:AED|2026-05-24",
                "UAE",
                "AED",
                88001L
        )));

        assertEquals(1, result.getInsertedCount());
        assertEquals("ACTIVE", repository.slabs.get(0).getStatus());
        assertEquals("ACTIVE", repository.slabs.get(1).getStatus());
    }

    @Test
    void sameNaturalKeyShouldSupersedePreviousActiveFactAndKeepHistory() {
        InMemoryRepository repository = new InMemoryRepository();
        String naturalKey = "KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24";
        repository.slabs.add(slab(naturalKey, "ACTIVE", 87001L));
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(publishedSlab(naturalKey, "KSA", "SAR", 88001L)));

        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getSupersededCount());
        assertEquals("SUPERSEDED", repository.slabs.get(0).getStatus());
        assertEquals("ACTIVE", repository.slabs.get(1).getStatus());
    }

    @Test
    void conflictRowsShouldNotBeBusinessReadable() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult result = publisher.land(List.of(
                publishedPolicy("KSA|NOON|FBN|2026-05-24", "physical_weight_plus_packaging_weight", 88011L),
                publishedPolicy("KSA|NOON|FBN|2026-05-24", "unknown", 88012L)
        ));

        OfficialOutboundFeeFactStatusPolicy policy = new OfficialOutboundFeeFactStatusPolicy();

        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getConflictCount());
        assertEquals("ACTIVE", repository.policies.get(0).getStatus());
        assertEquals("CONFLICT", repository.policies.get(1).getStatus());
        assertEquals(1, repository.activePolicyCount(policy));
    }

    private static OfficialOutboundFeePublishedItem publishedSlab(
            String naturalKey,
            String country,
            String currency,
            Long sourceVersionItemId
    ) {
        return new OfficialOutboundFeePublishedItem(
                "outbound_fee_weight_slab_rule",
                naturalKey,
                Map.of(
                        "country", country,
                        "platform", "NOON",
                        "fulfillmentType", "FBN",
                        "classificationName", "Small Envelope",
                        "weightMaxGrams", 250,
                        "standardFeeAmount", 6.25,
                        "currency", currency
                ),
                lineage(sourceVersionItemId)
        );
    }

    private static OfficialOutboundFeePublishedItem publishedPolicy(String naturalKey, String formula, Long sourceVersionItemId) {
        return new OfficialOutboundFeePublishedItem(
                "outbound_fee_calculation_policy",
                naturalKey,
                Map.of(
                        "country", "KSA",
                        "platform", "NOON",
                        "fulfillmentType", "FBN",
                        "shippingWeightFormula", formula
                ),
                lineage(sourceVersionItemId)
        );
    }

    private static OutboundFeeWeightSlabRuleFact slab(String naturalKey, String status, Long sourceVersionItemId) {
        return new OutboundFeeWeightSlabRuleFact(
                naturalKey,
                "KSA",
                "NOON",
                "FBN",
                "Small Envelope",
                null,
                null,
                java.math.BigDecimal.valueOf(250),
                true,
                java.math.BigDecimal.valueOf(6.25),
                null,
                null,
                null,
                null,
                null,
                "SAR",
                "2026-05-24",
                status,
                lineage(sourceVersionItemId)
        );
    }

    private static OfficialOutboundFeeSourceLineage lineage(Long sourceVersionItemId) {
        return new OfficialOutboundFeeSourceLineage(
                "file_management",
                20114L,
                40114L,
                70014L,
                sourceVersionItemId,
                "fbn-fees.pdf",
                "version_item:" + sourceVersionItemId
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
            return slabs.stream().anyMatch(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()))
                    || policies.stream().anyMatch(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()))
                    || classifications.stream().anyMatch(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()));
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
            slabs.stream()
                    .filter(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()))
                    .forEach(fact -> fact.setStatus("SUPERSEDED"));
            policies.stream()
                    .filter(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()))
                    .forEach(fact -> fact.setStatus("SUPERSEDED"));
            classifications.stream()
                    .filter(fact -> naturalKey.equals(fact.getNaturalKey()) && "ACTIVE".equals(fact.getStatus()))
                    .forEach(fact -> fact.setStatus("SUPERSEDED"));
        }

        private long activePolicyCount(OfficialOutboundFeeFactStatusPolicy policy) {
            return policies.stream()
                    .filter(fact -> policy.isBusinessReadable(fact.getStatus()))
                    .count();
        }
    }
}

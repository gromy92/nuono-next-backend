package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeSamplePublishE2ETest {

    @Test
    void shouldPublishKsaAndUaeSampleFactsThenCalculateWithoutEgyptContamination() {
        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);

        OfficialOutboundFeeFactLandingResult landing = publisher.land(List.of(
                classification("KSA", "SAR", "Small Envelope", 250, 20, 88001L),
                slab("KSA", "SAR", "Small Envelope", 250, 6.25, 7.75, 88002L),
                policy("KSA", "SAR", 88003L),
                classification("UAE", "AED", "Small Envelope", 250, 15, 88011L),
                slab("UAE", "AED", "Small Envelope", 250, 5.50, 6.25, 88012L),
                policy("UAE", "AED", 88013L),
                classification("EGY", "EGP", "Small Envelope", 250, 15, 88021L)
        ));

        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);
        OfficialOutboundFeeCalculationResult ksa = calculator.calculate(request("KSA", "SAR", 30, 200, 20, 15, 2));
        OfficialOutboundFeeCalculationResult uae = calculator.calculate(request("UAE", "AED", 20, 200, 20, 15, 2));
        OfficialOutboundFeeCalculationResult egypt = calculator.calculate(request("EGY", "EGP", 20, 200, 20, 15, 2));

        assertEquals(7, landing.getInsertedCount());
        assertTrue(ksa.isSuccess());
        assertEquals(new BigDecimal("7.75"), ksa.getFinalFeeAmount());
        assertEquals("SAR", ksa.getCurrency());
        assertTrue(uae.isSuccess());
        assertEquals(new BigDecimal("5.5"), uae.getFinalFeeAmount());
        assertEquals("AED", uae.getCurrency());
        assertEquals(OfficialOutboundFeeCalculationFailure.POLICY_NOT_FOUND, egypt.getFailure());
        assertEquals(3, repository.activeFactCount("KSA"));
        assertEquals(3, repository.activeFactCount("UAE"));
    }

    private static OfficialOutboundFeeCalculationRequest request(
            String country,
            String currency,
            int salePrice,
            int weight,
            int length,
            int width,
            int height
    ) {
        return new OfficialOutboundFeeCalculationRequest(
                country,
                "NOON",
                "FBN",
                currency,
                BigDecimal.valueOf(salePrice),
                BigDecimal.valueOf(weight),
                BigDecimal.valueOf(length),
                BigDecimal.valueOf(width),
                BigDecimal.valueOf(height),
                LocalDate.of(2026, 5, 24)
        );
    }

    private static OfficialOutboundFeePublishedItem classification(
            String country,
            String currency,
            String classificationName,
            int maxWeight,
            int packagingWeight,
            Long sourceVersionItemId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", country);
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", classificationName);
        payload.put("longestSideMaxCm", 20);
        payload.put("medianSideMaxCm", 15);
        payload.put("shortestSideMaxCm", 2);
        payload.put("maxShippingWeightGrams", maxWeight);
        payload.put("packagingWeightGrams", packagingWeight);
        payload.put("currency", currency);
        payload.put("effectiveDate", "2026-05-24");
        return new OfficialOutboundFeePublishedItem(
                "outbound_size_classification_rule",
                country + "|NOON|FBN|" + classificationName + "|2026-05-24",
                payload,
                lineage(sourceVersionItemId)
        );
    }

    private static OfficialOutboundFeePublishedItem slab(
            String country,
            String currency,
            String classificationName,
            int maxWeight,
            double standardFee,
            double highAspFee,
            Long sourceVersionItemId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", country);
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        payload.put("classificationName", classificationName);
        payload.put("weightMinGrams", 0);
        payload.put("weightMinInclusive", true);
        payload.put("weightMaxGrams", maxWeight);
        payload.put("weightMaxInclusive", true);
        payload.put("standardFeeAmount", standardFee);
        payload.put("highAspFeeAmount", highAspFee);
        payload.put("currency", currency);
        payload.put("effectiveDate", "2026-05-24");
        return new OfficialOutboundFeePublishedItem(
                "outbound_fee_weight_slab_rule",
                country + "|NOON|FBN|" + classificationName + "|MIN:0:1|MAX:" + maxWeight + ":1|CUR:" + currency + "|2026-05-24",
                payload,
                lineage(sourceVersionItemId)
        );
    }

    private static OfficialOutboundFeePublishedItem policy(String country, String currency, Long sourceVersionItemId) {
        return new OfficialOutboundFeePublishedItem(
                "outbound_fee_calculation_policy",
                country + "|NOON|FBN|2026-05-24",
                Map.of(
                        "country", country,
                        "platform", "NOON",
                        "fulfillmentType", "FBN",
                        "shippingWeightFormula", "physical_weight_plus_packaging_weight",
                        "salesPriceThresholdAmount", 25,
                        "thresholdCurrency", currency,
                        "effectiveDate", "2026-05-24"
                ),
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
                "official-sample.pdf",
                "version_item:" + sourceVersionItemId
        );
    }

    private static final class InMemoryRepository implements OfficialOutboundFeeFactRepository {

        private final List<OutboundSizeClassificationRuleFact> classifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> slabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(String country, String platform, String fulfillmentType, LocalDate calculationDate) {
            return classifications;
        }

        @Override
        public List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(String country, String platform, String fulfillmentType, LocalDate calculationDate) {
            return slabs;
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(String country, String platform, String fulfillmentType, LocalDate calculationDate) {
            return policies.stream()
                    .filter(policy -> country.equals(policy.getCountry()))
                    .findFirst();
        }

        @Override
        public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
            classifications.add(fact);
        }

        @Override
        public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
            slabs.add(fact);
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
            policies.add(fact);
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
            return false;
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
        }

        private long activeFactCount(String country) {
            return classifications.stream().filter(fact -> country.equals(fact.getCountry()) && "ACTIVE".equals(fact.getStatus())).count()
                    + slabs.stream().filter(fact -> country.equals(fact.getCountry()) && "ACTIVE".equals(fact.getStatus())).count()
                    + policies.stream().filter(fact -> country.equals(fact.getCountry()) && "ACTIVE".equals(fact.getStatus())).count();
        }
    }
}

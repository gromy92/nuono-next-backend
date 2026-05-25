package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeCalculatorTest {

    @Test
    void shouldSortDimensionsMatchClassificationAndApplyStandardFee() {
        InMemoryRepository repository = baselineRepository();
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);

        OfficialOutboundFeeCalculationResult result = calculator.calculate(request("KSA", "SAR", 20, 200, 15, 2, 20));

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("6.25"), result.getFinalFeeAmount());
        assertEquals("SAR", result.getCurrency());
        assertEquals("Small Envelope", result.getMatchedClassificationName());
        assertEquals("KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24", result.getMatchedSlabNaturalKey());
        assertEquals(70014L, result.getSourceVersionId());
    }

    @Test
    void shouldUsePackagingWeightForShippingWeightAndRejectOverweightClassification() {
        InMemoryRepository repository = baselineRepository();
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);

        OfficialOutboundFeeCalculationResult boundary = calculator.calculate(request("KSA", "SAR", 20, 230, 15, 2, 20));
        OfficialOutboundFeeCalculationResult overweight = calculator.calculate(request("KSA", "SAR", 20, 231, 15, 2, 20));

        assertTrue(boundary.isSuccess());
        assertEquals(new BigDecimal("250"), boundary.getEvidence().get("shippingWeightGrams"));
        assertFalse(overweight.isSuccess());
        assertEquals(OfficialOutboundFeeCalculationFailure.CLASSIFICATION_NOT_FOUND, overweight.getFailure());
    }

    @Test
    void shouldApplyHighAspFeeWhenSalePriceCrossesThreshold() {
        InMemoryRepository repository = baselineRepository();
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);

        OfficialOutboundFeeCalculationResult result = calculator.calculate(request("KSA", "SAR", 30, 200, 15, 2, 20));

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("7.75"), result.getFinalFeeAmount());
        assertEquals(new BigDecimal("25"), result.getEvidence().get("salesPriceThresholdAmount"));
    }

    @Test
    void shouldApplyExtraWeightStepFeeAboveBaseSlab() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.policies.add(policy("KSA|NOON|FBN|2026-05-24", "KSA", "SAR"));
        repository.classifications.add(classification(
                "KSA|NOON|FBN|Large Parcel|2026-05-24",
                "KSA",
                "Large Parcel",
                80,
                60,
                40,
                2000,
                100
        ));
        repository.slabs.add(slab(
                "KSA|NOON|FBN|Large Parcel|MIN:0:1|MAX:1000:1|CUR:SAR|2026-05-24",
                "KSA",
                "Large Parcel",
                0,
                1000,
                10,
                null,
                "SAR",
                500,
                2
        ));
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);

        OfficialOutboundFeeCalculationResult result = calculator.calculate(request("KSA", "SAR", 20, 1150, 60, 40, 80));

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("12"), result.getFinalFeeAmount());
        assertEquals(new BigDecimal("1250"), result.getEvidence().get("shippingWeightGrams"));
        assertEquals(1, result.getEvidence().get("extraSteps"));
    }

    @Test
    void shouldReturnTypedFailuresInsteadOfFakeZero() {
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(baselineRepository());

        assertEquals(OfficialOutboundFeeCalculationFailure.MISSING_DIMENSIONS,
                calculator.calculate(request("KSA", "SAR", 20, 200, null, 2, 20)).getFailure());
        assertEquals(OfficialOutboundFeeCalculationFailure.MISSING_WEIGHT,
                calculator.calculate(request("KSA", "SAR", 20, null, 15, 2, 20)).getFailure());
        assertEquals(OfficialOutboundFeeCalculationFailure.MISSING_SALE_PRICE,
                calculator.calculate(request("KSA", "SAR", null, 200, 15, 2, 20)).getFailure());
        assertEquals(OfficialOutboundFeeCalculationFailure.POLICY_NOT_FOUND,
                calculator.calculate(request("UAE", "AED", 20, 200, 15, 2, 20)).getFailure());
        assertEquals(OfficialOutboundFeeCalculationFailure.CURRENCY_MISMATCH,
                calculator.calculate(request("KSA", "AED", 20, 200, 15, 2, 20)).getFailure());
    }

    private static InMemoryRepository baselineRepository() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.policies.add(policy("KSA|NOON|FBN|2026-05-24", "KSA", "SAR"));
        repository.classifications.add(classification(
                "KSA|NOON|FBN|Small Envelope|2026-05-24",
                "KSA",
                "Small Envelope",
                20,
                15,
                2,
                250,
                20
        ));
        repository.slabs.add(slab(
                "KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24",
                "KSA",
                "Small Envelope",
                0,
                250,
                6.25,
                7.75,
                "SAR",
                null,
                null
        ));
        return repository;
    }

    private static OfficialOutboundFeeCalculationRequest request(
            String country,
            String currency,
            Integer salePrice,
            Integer weight,
            Integer length,
            Integer width,
            Integer height
    ) {
        return new OfficialOutboundFeeCalculationRequest(
                country,
                "NOON",
                "FBN",
                currency,
                salePrice == null ? null : BigDecimal.valueOf(salePrice),
                weight == null ? null : BigDecimal.valueOf(weight),
                length == null ? null : BigDecimal.valueOf(length),
                width == null ? null : BigDecimal.valueOf(width),
                height == null ? null : BigDecimal.valueOf(height),
                LocalDate.of(2026, 5, 24)
        );
    }

    private static OutboundSizeClassificationRuleFact classification(
            String naturalKey,
            String country,
            String classificationName,
            int longest,
            int median,
            int shortest,
            int maxWeight,
            int packaging
    ) {
        return new OutboundSizeClassificationRuleFact(
                naturalKey,
                country,
                "NOON",
                "FBN",
                classificationName,
                BigDecimal.valueOf(longest),
                BigDecimal.valueOf(median),
                BigDecimal.valueOf(shortest),
                BigDecimal.valueOf(maxWeight),
                BigDecimal.valueOf(packaging),
                1,
                "cm",
                "grams",
                "2026-05-24",
                "ACTIVE",
                lineage(70014L, naturalKey.hashCode() & 0xffffL)
        );
    }

    private static OutboundFeeWeightSlabRuleFact slab(
            String naturalKey,
            String country,
            String classificationName,
            int min,
            int max,
            double standardFee,
            Double highAspFee,
            String currency,
            Integer extraStep,
            Integer extraFee
    ) {
        return new OutboundFeeWeightSlabRuleFact(
                naturalKey,
                country,
                "NOON",
                "FBN",
                classificationName,
                BigDecimal.valueOf(min),
                true,
                BigDecimal.valueOf(max),
                true,
                BigDecimal.valueOf(standardFee).stripTrailingZeros(),
                highAspFee == null ? null : BigDecimal.valueOf(highAspFee).stripTrailingZeros(),
                null,
                null,
                extraStep == null ? null : BigDecimal.valueOf(extraStep),
                extraFee == null ? null : BigDecimal.valueOf(extraFee),
                currency,
                "2026-05-24",
                "ACTIVE",
                lineage(70014L, naturalKey.hashCode() & 0xffffL)
        );
    }

    private static OutboundFeeCalculationPolicyFact policy(String naturalKey, String country, String currency) {
        return new OutboundFeeCalculationPolicyFact(
                naturalKey,
                country,
                "NOON",
                "FBN",
                "default",
                "physical_weight_plus_packaging_weight",
                "sort_descending",
                "inclusive_max",
                "ceil_extra_step",
                BigDecimal.valueOf(25),
                currency,
                "cm",
                "grams",
                "2026-05-24",
                "ACTIVE",
                lineage(70014L, naturalKey.hashCode() & 0xffffL)
        );
    }

    private static OfficialOutboundFeeSourceLineage lineage(Long sourceVersionId, Long sourceVersionItemId) {
        return new OfficialOutboundFeeSourceLineage(
                "file_management",
                20114L,
                40114L,
                sourceVersionId,
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
        public List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return classifications;
        }

        @Override
        public List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return slabs;
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
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
    }
}

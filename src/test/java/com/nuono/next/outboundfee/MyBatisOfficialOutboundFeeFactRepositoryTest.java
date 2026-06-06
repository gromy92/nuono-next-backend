package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.OfficialOutboundFeeMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MyBatisOfficialOutboundFeeFactRepositoryTest {

    @Test
    void readsActiveFactsForCalculatorConsumption() {
        CapturingOfficialOutboundFeeMapper mapper = new CapturingOfficialOutboundFeeMapper();
        MyBatisOfficialOutboundFeeFactRepository repository = new MyBatisOfficialOutboundFeeFactRepository(mapper);
        repository.insertSizeClassification(new OutboundSizeClassificationRuleFact(
                "KSA|NOON|FBN|Small|2026-05-01",
                "KSA",
                "NOON",
                "FBN",
                "Small",
                new BigDecimal("20"),
                new BigDecimal("15"),
                new BigDecimal("10"),
                new BigDecimal("500"),
                new BigDecimal("20"),
                1,
                "cm",
                "g",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1001L)
        ));
        repository.insertWeightSlab(new OutboundFeeWeightSlabRuleFact(
                "KSA|NOON|FBN|Small|MIN:0|MAX:500|CUR:SAR|2026-05-01",
                "KSA",
                "NOON",
                "FBN",
                "Small",
                new BigDecimal("0"),
                false,
                new BigDecimal("500"),
                true,
                new BigDecimal("6.5"),
                new BigDecimal("8.5"),
                new BigDecimal("50"),
                "SAR",
                new BigDecimal("500"),
                new BigDecimal("2"),
                "SAR",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1002L)
        ));
        repository.insertCalculationPolicy(new OutboundFeeCalculationPolicyFact(
                "KSA|NOON|FBN|2026-05-01",
                "KSA",
                "NOON",
                "FBN",
                "Policy",
                "product_weight_plus_packaging_weight",
                "sort_dimensions_desc",
                "min_exclusive_max_inclusive",
                "no_rounding",
                new BigDecimal("50"),
                "SAR",
                "cm",
                "g",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1003L)
        ));

        assertEquals(1, repository.findActiveSizeClassifications("KSA", "NOON", "FBN", LocalDate.parse("2026-05-24")).size());
        assertEquals(1, repository.findActiveWeightSlabs("KSA", "NOON", "FBN", LocalDate.parse("2026-05-24")).size());
        assertTrue(repository.findActiveCalculationPolicy("KSA", "NOON", "FBN", LocalDate.parse("2026-05-24")).isPresent());
    }

    private OfficialOutboundFeeSourceLineage lineage(Long sourceVersionItemId) {
        return new OfficialOutboundFeeSourceLineage(
                "file_management",
                7001L,
                8001L,
                9001L,
                sourceVersionItemId,
                "fulfilled-by-noon-fbn-fees-in-ksa.pdf",
                "page 1"
        );
    }

    private static class CapturingOfficialOutboundFeeMapper implements OfficialOutboundFeeMapper {

        private final List<OutboundSizeClassificationRuleFact> sizeClassifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> weightSlabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public Long nextFactId(String tableName, long initialValue) {
            return initialValue + 1;
        }

        @Override
        public void nextId(IdSequenceCommand command) {
            command.setAllocatedId(720001L);
        }

        @Override
        public int countBySourceVersionItemId(String tableName, Long sourceVersionItemId) {
            return 0;
        }

        @Override
        public int countActiveByNaturalKey(String tableName, String naturalKey) {
            return 0;
        }

        @Override
        public int supersedeActiveByNaturalKey(String tableName, String naturalKey) {
            return 0;
        }

        @Override
        public int supersedeActiveMissingFromSnapshot(
                String tableName,
                String country,
                String platform,
                String fulfillmentType,
                List<String> snapshotNaturalKeys
        ) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> selectActiveSizeClassificationRows(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (OutboundSizeClassificationRuleFact fact : sizeClassifications) {
                rows.add(sizeClassificationRow(fact));
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectActiveWeightSlabRows(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (OutboundFeeWeightSlabRuleFact fact : weightSlabs) {
                rows.add(weightSlabRow(fact));
            }
            return rows;
        }

        @Override
        public List<Map<String, Object>> selectActiveCalculationPolicyRows(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (OutboundFeeCalculationPolicyFact fact : policies) {
                rows.add(policyRow(fact));
            }
            return rows;
        }

        @Override
        public int insertSizeClassification(Long id, OutboundSizeClassificationRuleFact fact, Long operatorUserId) {
            sizeClassifications.add(fact);
            return 1;
        }

        @Override
        public int insertWeightSlab(Long id, OutboundFeeWeightSlabRuleFact fact, Long operatorUserId) {
            weightSlabs.add(fact);
            return 1;
        }

        @Override
        public int insertCalculationPolicy(Long id, OutboundFeeCalculationPolicyFact fact, Long operatorUserId) {
            policies.add(fact);
            return 1;
        }

        @Override
        public int insertCalculationFact(Long id, OfficialOutboundFeeCalculationFact fact, Long operatorUserId) {
            return 1;
        }

        @Override
        public List<OfficialOutboundFeeCalculationView> selectLatestCalculationViewsBySkuIds(
                Long ownerUserId,
                String storeCode,
                String site,
                List<String> skuIds,
                String specSourceType
        ) {
            return List.of();
        }

        private Map<String, Object> sizeClassificationRow(OutboundSizeClassificationRuleFact fact) {
            Map<String, Object> row = baseRow(fact.getNaturalKey(), fact.getCountry(), fact.getPlatform(), fact.getFulfillmentType(), fact.getEffectiveFrom(), fact.getStatus(), fact.getSourceLineage());
            row.put("classificationName", fact.getClassificationName());
            row.put("longestSideMaxCm", fact.getLongestSideMaxCm());
            row.put("medianSideMaxCm", fact.getMedianSideMaxCm());
            row.put("shortestSideMaxCm", fact.getShortestSideMaxCm());
            row.put("maxShippingWeightGrams", fact.getMaxShippingWeightGrams());
            row.put("packagingWeightGrams", fact.getPackagingWeightGrams());
            row.put("priority", fact.getPriority());
            row.put("dimensionUnit", fact.getDimensionUnit());
            row.put("weightUnit", fact.getWeightUnit());
            return row;
        }

        private Map<String, Object> weightSlabRow(OutboundFeeWeightSlabRuleFact fact) {
            Map<String, Object> row = baseRow(fact.getNaturalKey(), fact.getCountry(), fact.getPlatform(), fact.getFulfillmentType(), fact.getEffectiveFrom(), fact.getStatus(), fact.getSourceLineage());
            row.put("classificationName", fact.getClassificationName());
            row.put("weightMinGrams", fact.getWeightMinGrams());
            row.put("weightMinInclusive", fact.getWeightMinInclusive());
            row.put("weightMaxGrams", fact.getWeightMaxGrams());
            row.put("weightMaxInclusive", fact.getWeightMaxInclusive());
            row.put("standardFeeAmount", fact.getStandardFeeAmount());
            row.put("highAspFeeAmount", fact.getHighAspFeeAmount());
            row.put("salesPriceThresholdAmount", fact.getSalesPriceThresholdAmount());
            row.put("thresholdCurrency", fact.getThresholdCurrency());
            row.put("extraWeightStepGrams", fact.getExtraWeightStepGrams());
            row.put("extraFeeAmount", fact.getExtraFeeAmount());
            row.put("currency", fact.getCurrency());
            return row;
        }

        private Map<String, Object> policyRow(OutboundFeeCalculationPolicyFact fact) {
            Map<String, Object> row = baseRow(fact.getNaturalKey(), fact.getCountry(), fact.getPlatform(), fact.getFulfillmentType(), fact.getEffectiveFrom(), fact.getStatus(), fact.getSourceLineage());
            row.put("policyName", fact.getPolicyName());
            row.put("shippingWeightFormula", fact.getShippingWeightFormula());
            row.put("dimensionSortRule", fact.getDimensionSortRule());
            row.put("weightBoundaryRule", fact.getWeightBoundaryRule());
            row.put("roundingRule", fact.getRoundingRule());
            row.put("salesPriceThresholdAmount", fact.getSalesPriceThresholdAmount());
            row.put("thresholdCurrency", fact.getThresholdCurrency());
            row.put("dimensionUnit", fact.getDimensionUnit());
            row.put("weightUnit", fact.getWeightUnit());
            return row;
        }

        private Map<String, Object> baseRow(
                String naturalKey,
                String country,
                String platform,
                String fulfillmentType,
                String effectiveFrom,
                String status,
                OfficialOutboundFeeSourceLineage lineage
        ) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("naturalKey", naturalKey);
            row.put("country", country);
            row.put("platform", platform);
            row.put("fulfillmentType", fulfillmentType);
            row.put("effectiveFrom", effectiveFrom);
            row.put("status", status);
            row.put("sourceType", lineage.getSourceType());
            row.put("sourceTaskId", lineage.getSourceTaskId());
            row.put("sourceResultId", lineage.getSourceResultId());
            row.put("sourceVersionId", lineage.getSourceVersionId());
            row.put("sourceVersionItemId", lineage.getSourceVersionItemId());
            row.put("sourceFileName", lineage.getSourceFileName());
            row.put("sourceLocator", lineage.getSourceLocator());
            return row;
        }
    }
}

package com.nuono.next.outboundfee;

import com.nuono.next.infrastructure.mapper.OfficialOutboundFeeMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOfficialOutboundFeeFactRepository implements OfficialOutboundFeeFactRepository {

    private static final Long SYSTEM_OPERATOR_USER_ID = 10001L;

    private final OfficialOutboundFeeMapper mapper;

    public MyBatisOfficialOutboundFeeFactRepository(OfficialOutboundFeeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(OfficialOutboundFeeFactType.SIZE_CLASSIFICATION, sourceVersionItemId)
                ? Optional.of(dummySizeClassification(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return mapper.selectActiveSizeClassificationRows(country, platform, fulfillmentType, calculationDate)
                .stream()
                .map(this::toSizeClassification)
                .collect(Collectors.toList());
    }

    @Override
    public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
        mapper.insertSizeClassification(nextId(OfficialOutboundFeeFactType.SIZE_CLASSIFICATION), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB, sourceVersionItemId)
                ? Optional.of(dummyWeightSlab(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return mapper.selectActiveWeightSlabRows(country, platform, fulfillmentType, calculationDate)
                .stream()
                .map(this::toWeightSlab)
                .collect(Collectors.toList());
    }

    @Override
    public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
        mapper.insertWeightSlab(nextId(OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(OfficialOutboundFeeFactType.CALCULATION_POLICY, sourceVersionItemId)
                ? Optional.of(dummyCalculationPolicy(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return mapper.selectActiveCalculationPolicyRows(country, platform, fulfillmentType, calculationDate)
                .stream()
                .findFirst()
                .map(this::toCalculationPolicy);
    }

    @Override
    public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
        mapper.insertCalculationPolicy(nextId(OfficialOutboundFeeFactType.CALCULATION_POLICY), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
        return mapper.countActiveByNaturalKey(factType.tableName(), naturalKey) > 0;
    }

    @Override
    public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
        mapper.supersedeActiveByNaturalKey(factType.tableName(), naturalKey);
    }

    @Override
    public int supersedeActiveFactsMissingFromSnapshot(
            OfficialOutboundFeeFactType factType,
            String country,
            String platform,
            String fulfillmentType,
            List<String> snapshotNaturalKeys
    ) {
        if (snapshotNaturalKeys == null || snapshotNaturalKeys.isEmpty()) {
            return 0;
        }
        return mapper.supersedeActiveMissingFromSnapshot(
                factType.tableName(),
                country,
                platform,
                fulfillmentType,
                snapshotNaturalKeys
        );
    }

    private boolean existsBySource(OfficialOutboundFeeFactType factType, Long sourceVersionItemId) {
        return sourceVersionItemId != null
                && mapper.countBySourceVersionItemId(factType.tableName(), sourceVersionItemId) > 0;
    }

    private Long nextId(OfficialOutboundFeeFactType factType) {
        return mapper.nextFactId(factType.tableName(), 500000L + factType.ordinal() * 100000L);
    }

    private OfficialOutboundFeeSourceLineage dummyLineage(Long sourceVersionItemId) {
        return new OfficialOutboundFeeSourceLineage(
                "file_management",
                null,
                null,
                null,
                sourceVersionItemId,
                null,
                null
        );
    }

    private OutboundSizeClassificationRuleFact dummySizeClassification(Long sourceVersionItemId) {
        return new OutboundSizeClassificationRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, OfficialOutboundFeeFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private OutboundFeeWeightSlabRuleFact dummyWeightSlab(Long sourceVersionItemId) {
        return new OutboundFeeWeightSlabRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, OfficialOutboundFeeFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private OutboundFeeCalculationPolicyFact dummyCalculationPolicy(Long sourceVersionItemId) {
        return new OutboundFeeCalculationPolicyFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, OfficialOutboundFeeFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private OutboundSizeClassificationRuleFact toSizeClassification(Map<String, Object> row) {
        return new OutboundSizeClassificationRuleFact(
                text(row, "naturalKey"),
                text(row, "country"),
                text(row, "platform"),
                text(row, "fulfillmentType"),
                text(row, "classificationName"),
                decimal(row, "longestSideMaxCm"),
                decimal(row, "medianSideMaxCm"),
                decimal(row, "shortestSideMaxCm"),
                decimal(row, "maxShippingWeightGrams"),
                decimal(row, "packagingWeightGrams"),
                integer(row, "priority"),
                text(row, "dimensionUnit"),
                text(row, "weightUnit"),
                text(row, "effectiveFrom"),
                text(row, "status"),
                lineage(row)
        );
    }

    private OutboundFeeWeightSlabRuleFact toWeightSlab(Map<String, Object> row) {
        return new OutboundFeeWeightSlabRuleFact(
                text(row, "naturalKey"),
                text(row, "country"),
                text(row, "platform"),
                text(row, "fulfillmentType"),
                text(row, "classificationName"),
                decimal(row, "weightMinGrams"),
                bool(row, "weightMinInclusive"),
                decimal(row, "weightMaxGrams"),
                bool(row, "weightMaxInclusive"),
                decimal(row, "standardFeeAmount"),
                decimal(row, "highAspFeeAmount"),
                decimal(row, "salesPriceThresholdAmount"),
                text(row, "thresholdCurrency"),
                decimal(row, "extraWeightStepGrams"),
                decimal(row, "extraFeeAmount"),
                text(row, "currency"),
                text(row, "effectiveFrom"),
                text(row, "status"),
                lineage(row)
        );
    }

    private OutboundFeeCalculationPolicyFact toCalculationPolicy(Map<String, Object> row) {
        return new OutboundFeeCalculationPolicyFact(
                text(row, "naturalKey"),
                text(row, "country"),
                text(row, "platform"),
                text(row, "fulfillmentType"),
                text(row, "policyName"),
                text(row, "shippingWeightFormula"),
                text(row, "dimensionSortRule"),
                text(row, "weightBoundaryRule"),
                text(row, "roundingRule"),
                decimal(row, "salesPriceThresholdAmount"),
                text(row, "thresholdCurrency"),
                text(row, "dimensionUnit"),
                text(row, "weightUnit"),
                text(row, "effectiveFrom"),
                text(row, "status"),
                lineage(row)
        );
    }

    private OfficialOutboundFeeSourceLineage lineage(Map<String, Object> row) {
        return new OfficialOutboundFeeSourceLineage(
                text(row, "sourceType"),
                longValue(row, "sourceTaskId"),
                longValue(row, "sourceResultId"),
                longValue(row, "sourceVersionId"),
                longValue(row, "sourceVersionItemId"),
                text(row, "sourceFileName"),
                text(row, "sourceLocator")
        );
    }

    private String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    private Integer integer(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Boolean bool(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.valueOf(value.toString());
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null || row.containsKey(key)) {
            return value;
        }
        String underscoreKey = key.replaceAll("([A-Z])", "_$1").toLowerCase();
        value = row.get(underscoreKey);
        if (value != null || row.containsKey(underscoreKey)) {
            return value;
        }
        return row.get(key.toUpperCase());
    }
}

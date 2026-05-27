package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteFactMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLogisticsQuoteFactRepository implements LogisticsQuoteFactRepository {

    private static final Long SYSTEM_OPERATOR_USER_ID = 10001L;

    private final LogisticsQuoteFactMapper mapper;

    public MyBatisLogisticsQuoteFactRepository(LogisticsQuoteFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.SERVICE_LINE, sourceVersionItemId)
                ? Optional.of(dummyServiceLine(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertServiceLine(LogisticsServiceLineFact fact) {
        mapper.insertServiceLine(nextId(LogisticsQuoteFactType.SERVICE_LINE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        return mapper.selectActiveServiceLineRows(
                        query.getForwarderCode(),
                        query.getCountry(),
                        query.getTransportMode(),
                        query.getServiceScope(),
                        query.getDestinationNode()
                )
                .stream()
                .map(this::toServiceLine)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LogisticsCargoCategoryFact> findCargoCategoryBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.CARGO_CATEGORY, sourceVersionItemId)
                ? Optional.of(dummyCargoCategory(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertCargoCategory(LogisticsCargoCategoryFact fact) {
        mapper.insertCargoCategory(nextId(LogisticsQuoteFactType.CARGO_CATEGORY), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public List<LogisticsCargoCategoryFact> findActiveCargoCategories(String forwarderCode, String serviceLineKey) {
        return mapper.selectActiveCargoCategoryRows(forwarderCode, serviceLineKey)
                .stream()
                .map(this::toCargoCategory)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LogisticsPriceRuleFact> findPriceRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.PRICE_RULE, sourceVersionItemId)
                ? Optional.of(dummyPriceRule(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertPriceRule(LogisticsPriceRuleFact fact) {
        mapper.insertPriceRule(nextId(LogisticsQuoteFactType.PRICE_RULE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public List<LogisticsPriceRuleFact> findPriceRulesByServiceLineKey(String serviceLineKey) {
        return mapper.selectActivePriceRuleRowsByServiceLineKey(serviceLineKey)
                .stream()
                .map(this::toPriceRule)
                .collect(Collectors.toList());
    }

    @Override
    public List<LogisticsPriceRuleFact> findComparablePriceRules(LogisticsQuoteComparisonQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        return mapper.selectComparablePriceRuleRows(
                        query.getCountry(),
                        query.getTransportMode(),
                        query.getServiceScope(),
                        query.getCargoCategoryName(),
                        query.getBillingUnit()
                )
                .stream()
                .map(this::toPriceRule)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LogisticsSurchargeRuleFact> findSurchargeRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.SURCHARGE_RULE, sourceVersionItemId)
                ? Optional.of(dummySurcharge(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertSurchargeRule(LogisticsSurchargeRuleFact fact) {
        mapper.insertSurchargeRule(nextId(LogisticsQuoteFactType.SURCHARGE_RULE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public Optional<LogisticsBillingRuleFact> findBillingRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.BILLING_RULE, sourceVersionItemId)
                ? Optional.of(dummyBillingRule(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertBillingRule(LogisticsBillingRuleFact fact) {
        mapper.insertBillingRule(nextId(LogisticsQuoteFactType.BILLING_RULE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public Optional<LogisticsRestrictionRuleFact> findRestrictionRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.RESTRICTION_RULE, sourceVersionItemId)
                ? Optional.of(dummyRestriction(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertRestrictionRule(LogisticsRestrictionRuleFact fact) {
        mapper.insertRestrictionRule(nextId(LogisticsQuoteFactType.RESTRICTION_RULE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public List<LogisticsRestrictionRuleFact> findRestrictionRulesByServiceLineKey(String serviceLineKey) {
        return mapper.selectActiveRestrictionRuleRowsByServiceLineKey(serviceLineKey)
                .stream()
                .map(this::toRestrictionRule)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LogisticsWarehouseFeeRuleFact> findWarehouseFeeRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return existsBySource(LogisticsQuoteFactType.WAREHOUSE_FEE_RULE, sourceVersionItemId)
                ? Optional.of(dummyWarehouseFee(sourceVersionItemId))
                : Optional.empty();
    }

    @Override
    public void insertWarehouseFeeRule(LogisticsWarehouseFeeRuleFact fact) {
        mapper.insertWarehouseFeeRule(nextId(LogisticsQuoteFactType.WAREHOUSE_FEE_RULE), fact, SYSTEM_OPERATOR_USER_ID);
    }

    @Override
    public boolean hasActiveFactWithNaturalKey(LogisticsQuoteFactType factType, String naturalKey) {
        return mapper.countActiveByNaturalKey(factType.tableName(), naturalKey) > 0;
    }

    @Override
    public void supersedeActiveFacts(LogisticsQuoteFactType factType, String naturalKey) {
        mapper.supersedeActiveByNaturalKey(factType.tableName(), naturalKey);
    }

    private boolean existsBySource(LogisticsQuoteFactType factType, Long sourceVersionItemId) {
        return sourceVersionItemId != null
                && mapper.countBySourceVersionItemId(factType.tableName(), sourceVersionItemId) > 0;
    }

    private Long nextId(LogisticsQuoteFactType factType) {
        return mapper.nextFactId(factType.tableName(), initialValue(factType));
    }

    private long initialValue(LogisticsQuoteFactType factType) {
        return 300000L + factType.ordinal() * 100000L;
    }

    private LogisticsQuoteFactSourceLineage dummyLineage(Long sourceVersionItemId) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                null,
                null,
                null,
                sourceVersionItemId,
                null,
                null
        );
    }

    private LogisticsServiceLineFact dummyServiceLine(Long sourceVersionItemId) {
        return new LogisticsServiceLineFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsCargoCategoryFact dummyCargoCategory(Long sourceVersionItemId) {
        return new LogisticsCargoCategoryFact(null, null, null, null, null, null, null, null, null, null, null, false, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsPriceRuleFact dummyPriceRule(Long sourceVersionItemId) {
        return new LogisticsPriceRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, "NORMAL", null, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsSurchargeRuleFact dummySurcharge(Long sourceVersionItemId) {
        return new LogisticsSurchargeRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, false, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsBillingRuleFact dummyBillingRule(Long sourceVersionItemId) {
        return new LogisticsBillingRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, null, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsRestrictionRuleFact dummyRestriction(Long sourceVersionItemId) {
        return new LogisticsRestrictionRuleFact(null, null, null, null, null, null, null, null, false, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsWarehouseFeeRuleFact dummyWarehouseFee(Long sourceVersionItemId) {
        return new LogisticsWarehouseFeeRuleFact(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, LogisticsQuoteFactStatus.ACTIVE.value(), dummyLineage(sourceVersionItemId));
    }

    private LogisticsServiceLineFact toServiceLine(Map<String, Object> row) {
        return new LogisticsServiceLineFact(
                text(row, "naturalKey"),
                text(row, "forwarderCode"),
                text(row, "forwarderName"),
                text(row, "country"),
                text(row, "fulfillmentMode"),
                text(row, "destinationNode"),
                text(row, "transportMode"),
                text(row, "serviceScope"),
                text(row, "channelName"),
                text(row, "originWarehouse"),
                text(row, "destinationWarehouse"),
                text(row, "departureFrequency"),
                integer(row, "estimatedDaysMin"),
                integer(row, "estimatedDaysMax"),
                text(row, "effectiveFrom"),
                text(row, "status"),
                lineage(row)
        );
    }

    private LogisticsPriceRuleFact toPriceRule(Map<String, Object> row) {
        return new LogisticsPriceRuleFact(
                text(row, "naturalKey"),
                text(row, "forwarderCode"),
                text(row, "serviceLineKey"),
                text(row, "cargoCategoryKey"),
                decimal(row, "unitPrice"),
                text(row, "currency"),
                text(row, "billingUnit"),
                text(row, "pricingModel"),
                decimal(row, "minimumBillableUnit"),
                text(row, "minimumBillableUnitType"),
                decimal(row, "minimumCharge"),
                decimal(row, "volumeDivisor"),
                text(row, "seaWeightRatio"),
                text(row, "roundingRule"),
                text(row, "priceStatus"),
                text(row, "effectiveFrom"),
                text(row, "status"),
                lineage(row)
        );
    }

    private LogisticsCargoCategoryFact toCargoCategory(Map<String, Object> row) {
        return new LogisticsCargoCategoryFact(
                text(row, "naturalKey"),
                text(row, "forwarderCode"),
                text(row, "serviceLineKey"),
                text(row, "categoryCode"),
                text(row, "categoryName"),
                text(row, "sourceCategoryName"),
                text(row, "productExamples"),
                text(row, "keywords"),
                text(row, "electricType"),
                text(row, "sensitiveTags"),
                text(row, "packingPolicy"),
                booleanValue(row, "manualConfirmRequired"),
                text(row, "status"),
                lineage(row)
        );
    }

    private LogisticsRestrictionRuleFact toRestrictionRule(Map<String, Object> row) {
        return new LogisticsRestrictionRuleFact(
                text(row, "naturalKey"),
                text(row, "forwarderCode"),
                text(row, "serviceLineKey"),
                text(row, "restrictionType"),
                text(row, "itemText"),
                text(row, "requirementText"),
                text(row, "applicabilityScope"),
                text(row, "severity"),
                booleanValue(row, "manualConfirmRequired"),
                text(row, "status"),
                lineage(row)
        );
    }

    private LogisticsQuoteFactSourceLineage lineage(Map<String, Object> row) {
        return new LogisticsQuoteFactSourceLineage(
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

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private boolean booleanValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        byte[] bytes = value instanceof byte[] ? (byte[]) value : null;
        if (bytes != null && bytes.length > 0) {
            return bytes[0] != 0;
        }
        return Boolean.parseBoolean(value.toString());
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

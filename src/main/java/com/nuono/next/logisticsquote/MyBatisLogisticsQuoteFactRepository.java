package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteFactMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
        return Collections.emptyList();
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
}

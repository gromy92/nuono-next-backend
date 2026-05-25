package com.nuono.next.logisticsquote;

import java.util.List;
import java.util.Collections;
import java.util.Optional;

public interface LogisticsQuoteFactRepository {

    Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId);

    void insertServiceLine(LogisticsServiceLineFact fact);

    List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query);

    default Optional<LogisticsCargoCategoryFact> findCargoCategoryBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertCargoCategory(LogisticsCargoCategoryFact fact) {
        throw new UnsupportedOperationException("Cargo category facts are not supported by this repository");
    }

    default List<LogisticsCargoCategoryFact> findActiveCargoCategories(String forwarderCode, String serviceLineKey) {
        return Collections.emptyList();
    }

    default Optional<LogisticsPriceRuleFact> findPriceRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertPriceRule(LogisticsPriceRuleFact fact) {
        throw new UnsupportedOperationException("Price rule facts are not supported by this repository");
    }

    default List<LogisticsPriceRuleFact> findPriceRulesByServiceLineKey(String serviceLineKey) {
        return Collections.emptyList();
    }

    default List<LogisticsPriceRuleFact> findComparablePriceRules(LogisticsQuoteComparisonQuery query) {
        return Collections.emptyList();
    }

    default Optional<LogisticsSurchargeRuleFact> findSurchargeRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertSurchargeRule(LogisticsSurchargeRuleFact fact) {
        throw new UnsupportedOperationException("Surcharge facts are not supported by this repository");
    }

    default List<LogisticsSurchargeRuleFact> findSurchargeRulesByServiceLineKey(String serviceLineKey) {
        return Collections.emptyList();
    }

    default Optional<LogisticsBillingRuleFact> findBillingRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertBillingRule(LogisticsBillingRuleFact fact) {
        throw new UnsupportedOperationException("Billing facts are not supported by this repository");
    }

    default List<LogisticsBillingRuleFact> findBillingRulesByServiceLineKey(String serviceLineKey) {
        return Collections.emptyList();
    }

    default Optional<LogisticsRestrictionRuleFact> findRestrictionRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertRestrictionRule(LogisticsRestrictionRuleFact fact) {
        throw new UnsupportedOperationException("Restriction facts are not supported by this repository");
    }

    default List<LogisticsRestrictionRuleFact> findRestrictionRulesByServiceLineKey(String serviceLineKey) {
        return Collections.emptyList();
    }

    default Optional<LogisticsWarehouseFeeRuleFact> findWarehouseFeeRuleBySourceVersionItemId(Long sourceVersionItemId) {
        return Optional.empty();
    }

    default void insertWarehouseFeeRule(LogisticsWarehouseFeeRuleFact fact) {
        throw new UnsupportedOperationException("Warehouse fee facts are not supported by this repository");
    }

    default List<LogisticsWarehouseFeeRuleFact> findWarehouseFeeRulesByWarehouseNode(String country, String warehouseNode) {
        return Collections.emptyList();
    }

    default boolean hasActiveFactWithNaturalKey(LogisticsQuoteFactType factType, String naturalKey) {
        return false;
    }

    default void supersedeActiveFacts(LogisticsQuoteFactType factType, String naturalKey) {
        // Optional repository capability for persistent adapters.
    }
}

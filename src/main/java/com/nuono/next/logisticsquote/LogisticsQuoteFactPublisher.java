package com.nuono.next.logisticsquote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class LogisticsQuoteFactPublisher {

    private final LogisticsQuoteFactRepository repository;
    private final LogisticsQuoteFactNaturalKey naturalKeys;
    private final LogisticsQuoteFactStatusPolicy statusPolicy;

    public LogisticsQuoteFactPublisher(LogisticsQuoteFactRepository repository) {
        this.repository = repository;
        this.naturalKeys = new LogisticsQuoteFactNaturalKey();
        this.statusPolicy = new LogisticsQuoteFactStatusPolicy();
    }

    public LogisticsQuoteFactLandingResult land(List<LogisticsQuotePublishedItem> items) {
        LogisticsQuoteFactLandingResult result = new LogisticsQuoteFactLandingResult();
        Map<String, String> operationSignatures = new HashMap<>();
        for (LogisticsQuotePublishedItem item : normalizeRelationshipAliases(items)) {
            if (item == null) {
                result.skipped();
                continue;
            }
            LogisticsQuoteFactType factType = LogisticsQuoteFactType.fromItemType(item.getItemType());
            String naturalKey = naturalKeys.resolve(item);
            if (isBlank(naturalKey)) {
                result.skipped();
                continue;
            }
            if (LogisticsQuoteFactType.SERVICE_LINE == factType) {
                landServiceLine(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.CARGO_CATEGORY == factType) {
                landCargoCategory(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.PRICE_RULE == factType) {
                landPriceRule(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.SURCHARGE_RULE == factType) {
                landSurchargeRule(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.BILLING_RULE == factType) {
                landBillingRule(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.RESTRICTION_RULE == factType) {
                landRestrictionRule(item, naturalKey, result, operationSignatures);
            } else if (LogisticsQuoteFactType.WAREHOUSE_FEE_RULE == factType) {
                landWarehouseFeeRule(item, naturalKey, result, operationSignatures);
            } else {
                result.skipped();
            }
        }
        return result;
    }

    private List<LogisticsQuotePublishedItem> normalizeRelationshipAliases(List<LogisticsQuotePublishedItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, String> serviceLineAliases = serviceLineAliases(items);
        Map<String, String> cargoCategoryAliases = cargoCategoryAliases(items, serviceLineAliases);
        List<LogisticsQuotePublishedItem> normalized = new ArrayList<>();
        for (LogisticsQuotePublishedItem item : items) {
            normalized.add(normalizeRelationshipAliases(item, serviceLineAliases, cargoCategoryAliases));
        }
        return normalized;
    }

    private Map<String, String> serviceLineAliases(List<LogisticsQuotePublishedItem> items) {
        Map<String, String> aliases = new HashMap<>();
        for (LogisticsQuotePublishedItem item : items) {
            if (item == null || LogisticsQuoteFactType.SERVICE_LINE != LogisticsQuoteFactType.fromItemType(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = item.getPayload();
            String forwarderCode = text(payload.get("forwarderCode"));
            String canonicalKey = serviceLineBusinessKey(item);
            putAlias(aliases, forwarderCode, canonicalKey, canonicalKey);
            putAlias(aliases, forwarderCode, item.getNaturalKey(), canonicalKey);
            putAlias(aliases, forwarderCode, payload.get("serviceLineKey"), canonicalKey);
            putAlias(aliases, forwarderCode, payload.get("channelName"), canonicalKey);
        }
        return aliases;
    }

    private Map<String, String> cargoCategoryAliases(
            List<LogisticsQuotePublishedItem> items,
            Map<String, String> serviceLineAliases
    ) {
        Map<String, String> aliases = new HashMap<>();
        for (LogisticsQuotePublishedItem item : items) {
            if (item == null || LogisticsQuoteFactType.CARGO_CATEGORY != LogisticsQuoteFactType.fromItemType(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>(item.getPayload());
            boolean serviceLineChanged = normalizeServiceLineReference(payload, serviceLineAliases);
            String forwarderCode = text(payload.get("forwarderCode"));
            String serviceLineKey = keyText(payload.get("serviceLineKey"));
            String canonicalKey = serviceLineChanged
                    ? resolveFromPayload(item.getItemType(), payload, item.getNaturalKey())
                    : naturalKeys.resolve(item);
            if (isBlank(canonicalKey)) {
                canonicalKey = resolveFromPayload(item.getItemType(), payload, item.getNaturalKey());
            }
            putCategoryAlias(aliases, forwarderCode, serviceLineKey, canonicalKey, canonicalKey);
            putCategoryAlias(aliases, forwarderCode, serviceLineKey, item.getNaturalKey(), canonicalKey);
            putCategoryAlias(aliases, forwarderCode, serviceLineKey, payload.get("categoryCode"), canonicalKey);
            putCategoryAlias(aliases, forwarderCode, serviceLineKey, payload.get("categoryName"), canonicalKey);
            putCategoryAlias(aliases, forwarderCode, serviceLineKey, payload.get("sourceCategoryName"), canonicalKey);
        }
        return aliases;
    }

    private LogisticsQuotePublishedItem normalizeRelationshipAliases(
            LogisticsQuotePublishedItem item,
            Map<String, String> serviceLineAliases,
            Map<String, String> cargoCategoryAliases
    ) {
        if (item == null) {
            return null;
        }
        LogisticsQuoteFactType factType = LogisticsQuoteFactType.fromItemType(item.getItemType());
        Map<String, Object> payload = new LinkedHashMap<>(item.getPayload());
        String naturalKey = item.getNaturalKey();
        boolean changed = false;

        if (LogisticsQuoteFactType.SERVICE_LINE == factType) {
            String canonicalServiceLineKey = serviceLineBusinessKey(item);
            if (!isBlank(canonicalServiceLineKey) && !canonicalServiceLineKey.equals(naturalKey)) {
                naturalKey = canonicalServiceLineKey;
                changed = true;
            }
        } else if (referencesServiceLine(factType)) {
            String forwarderCode = text(payload.get("forwarderCode"));
            String originalServiceLineKey = keyText(payload.get("serviceLineKey"));
            changed = normalizeServiceLineReference(payload, serviceLineAliases);
            if (!changed
                    && hasServiceLineAliasForForwarder(serviceLineAliases, forwarderCode)
                    && !serviceLineAliases.containsKey(aliasKey(forwarderCode, originalServiceLineKey))) {
                payload.put("serviceLineKey", "");
                changed = true;
            }
            if (referencesCargoCategory(factType)) {
                changed = normalizeCargoCategoryReference(payload, cargoCategoryAliases) || changed;
            }
            if (changed) {
                naturalKey = resolveFromPayload(item.getItemType(), payload, item.getNaturalKey());
            }
        }

        if (!changed) {
            return item;
        }
        return new LogisticsQuotePublishedItem(item.getItemType(), naturalKey, payload, item.getSourceLineage());
    }

    private boolean normalizeServiceLineReference(
            Map<String, Object> payload,
            Map<String, String> serviceLineAliases
    ) {
        String forwarderCode = text(payload.get("forwarderCode"));
        String original = keyText(payload.get("serviceLineKey"));
        String canonical = serviceLineAliases.get(aliasKey(forwarderCode, original));
        if (isBlank(canonical) || canonical.equals(original)) {
            return false;
        }
        payload.put("serviceLineKey", canonical);
        return true;
    }

    private boolean normalizeCargoCategoryReference(
            Map<String, Object> payload,
            Map<String, String> cargoCategoryAliases
    ) {
        String forwarderCode = text(payload.get("forwarderCode"));
        String serviceLineKey = keyText(payload.get("serviceLineKey"));
        String original = keyText(payload.get("cargoCategoryKey"));
        String canonical = cargoCategoryAliases.get(categoryAliasKey(forwarderCode, serviceLineKey, original));
        if (isBlank(canonical) || canonical.equals(original)) {
            return false;
        }
        payload.put("cargoCategoryKey", canonical);
        return true;
    }

    private String serviceLineBusinessKey(LogisticsQuotePublishedItem item) {
        String resolved = naturalKeys.resolve(item);
        if (!isBlank(resolved)) {
            return resolved;
        }
        Map<String, Object> payload = item.getPayload();
        String forwarderCode = keyText(payload.get("forwarderCode"));
        String country = keyText(payload.get("country"));
        String transportMode = keyText(payload.get("transportMode"));
        String serviceScope = keyText(payload.get("serviceScope"));
        String destinationNode = keyText(payload.get("destinationNode"));
        if (!isBlank(forwarderCode)
                && !isBlank(country)
                && !isBlank(transportMode)
                && !isBlank(serviceScope)
                && !isBlank(destinationNode)) {
            return String.join("|", forwarderCode, country, transportMode, serviceScope, destinationNode);
        }
        return null;
    }

    private String resolveFromPayload(String itemType, Map<String, Object> payload, String fallbackNaturalKey) {
        String resolved = naturalKeys.resolve(new LogisticsQuotePublishedItem(itemType, null, payload, null));
        return isBlank(resolved) ? fallbackNaturalKey : resolved;
    }

    private boolean referencesServiceLine(LogisticsQuoteFactType factType) {
        return LogisticsQuoteFactType.CARGO_CATEGORY == factType
                || LogisticsQuoteFactType.PRICE_RULE == factType
                || LogisticsQuoteFactType.SURCHARGE_RULE == factType
                || LogisticsQuoteFactType.BILLING_RULE == factType
                || LogisticsQuoteFactType.RESTRICTION_RULE == factType;
    }

    private boolean referencesCargoCategory(LogisticsQuoteFactType factType) {
        return LogisticsQuoteFactType.PRICE_RULE == factType
                || LogisticsQuoteFactType.BILLING_RULE == factType;
    }

    private boolean hasServiceLineAliasForForwarder(Map<String, String> serviceLineAliases, String forwarderCode) {
        if (serviceLineAliases.isEmpty() || isBlank(forwarderCode)) {
            return false;
        }
        String prefix = forwarderCode.trim().toLowerCase(Locale.ROOT) + "|";
        return serviceLineAliases.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    private void putAlias(Map<String, String> aliases, Object forwarderCode, Object alias, String canonicalKey) {
        if (isBlank(canonicalKey)) {
            return;
        }
        String key = aliasKey(text(forwarderCode), keyText(alias));
        if (!isBlank(key)) {
            aliases.putIfAbsent(key, canonicalKey);
        }
    }

    private void putCategoryAlias(
            Map<String, String> aliases,
            Object forwarderCode,
            Object serviceLineKey,
            Object alias,
            String canonicalKey
    ) {
        if (isBlank(canonicalKey)) {
            return;
        }
        String key = categoryAliasKey(text(forwarderCode), keyText(serviceLineKey), keyText(alias));
        if (!isBlank(key)) {
            aliases.putIfAbsent(key, canonicalKey);
        }
    }

    private String aliasKey(String forwarderCode, String alias) {
        if (isBlank(forwarderCode) || isBlank(alias)) {
            return "";
        }
        return forwarderCode.trim().toLowerCase(Locale.ROOT) + "|" + normalizeAlias(alias);
    }

    private String categoryAliasKey(String forwarderCode, String serviceLineKey, String alias) {
        if (isBlank(forwarderCode) || isBlank(serviceLineKey) || isBlank(alias)) {
            return "";
        }
        return forwarderCode.trim().toLowerCase(Locale.ROOT)
                + "|" + normalizeAlias(serviceLineKey)
                + "|" + normalizeAlias(alias);
    }

    private String normalizeAlias(String value) {
        return keyText(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void landServiceLine(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("country")))
                || isBlank(text(payload.get("transportMode")))
                || isBlank(text(payload.get("serviceScope")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findServiceLineBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.SERVICE_LINE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertServiceLine(toServiceLine(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.SERVICE_LINE, naturalKey, result);
        repository.insertServiceLine(toServiceLine(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.SERVICE_LINE, item.getPayload())
        ));
        result.inserted();
    }

    private void landCargoCategory(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("serviceLineKey")))
                || isBlank(text(payload.get("categoryName")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findCargoCategoryBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.CARGO_CATEGORY,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertCargoCategory(toCargoCategory(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        String status = statusPolicy.initialStatus(LogisticsQuoteFactType.CARGO_CATEGORY, payload);
        if (LogisticsQuoteFactStatus.ACTIVE.value().equals(status)) {
            supersedeActiveIfNeeded(LogisticsQuoteFactType.CARGO_CATEGORY, naturalKey, result);
        }
        repository.insertCargoCategory(toCargoCategory(item, naturalKey, status));
        result.inserted();
    }

    private void landPriceRule(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("serviceLineKey")))
                || isBlank(text(payload.get("billingUnit")))
                || isBlank(text(payload.get("pricingModel")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findPriceRuleBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.PRICE_RULE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertPriceRule(toPriceRule(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.PRICE_RULE, naturalKey, result);
        repository.insertPriceRule(toPriceRule(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.PRICE_RULE, item.getPayload())
        ));
        result.inserted();
    }

    private void landSurchargeRule(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("serviceLineKey")))
                || isBlank(text(payload.get("surchargeName")))
                || isBlank(text(payload.get("triggerCondition")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findSurchargeRuleBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.SURCHARGE_RULE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertSurchargeRule(toSurchargeRule(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.SURCHARGE_RULE, naturalKey, result);
        repository.insertSurchargeRule(toSurchargeRule(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.SURCHARGE_RULE, item.getPayload())
        ));
        result.inserted();
    }

    private void landBillingRule(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("serviceLineKey")))
                || isBlank(text(payload.get("ruleName")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findBillingRuleBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.BILLING_RULE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertBillingRule(toBillingRule(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.BILLING_RULE, naturalKey, result);
        repository.insertBillingRule(toBillingRule(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.BILLING_RULE, item.getPayload())
        ));
        result.inserted();
    }

    private void landRestrictionRule(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("serviceLineKey")))
                || isBlank(text(payload.get("restrictionType")))
                || isBlank(text(payload.get("itemText")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findRestrictionRuleBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.RESTRICTION_RULE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertRestrictionRule(toRestrictionRule(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.RESTRICTION_RULE, naturalKey, result);
        repository.insertRestrictionRule(toRestrictionRule(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.RESTRICTION_RULE, item.getPayload())
        ));
        result.inserted();
    }

    private void landWarehouseFeeRule(
            LogisticsQuotePublishedItem item,
            String naturalKey,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("forwarderCode")))
                || isBlank(text(payload.get("country")))
                || isBlank(text(payload.get("warehouseNode")))
                || isBlank(text(payload.get("serviceName")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findWarehouseFeeRuleBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                LogisticsQuoteFactType.WAREHOUSE_FEE_RULE,
                naturalKey,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertWarehouseFeeRule(toWarehouseFeeRule(item, naturalKey, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(LogisticsQuoteFactType.WAREHOUSE_FEE_RULE, naturalKey, result);
        repository.insertWarehouseFeeRule(toWarehouseFeeRule(
                item,
                naturalKey,
                statusPolicy.initialStatus(LogisticsQuoteFactType.WAREHOUSE_FEE_RULE, item.getPayload())
        ));
        result.inserted();
    }

    private DuplicateDecision recordDuplicateOrConflict(
            LogisticsQuoteFactType factType,
            String naturalKey,
            LogisticsQuotePublishedItem item,
            LogisticsQuoteFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        String scopeKey = statusPolicy.landingScopeKey(factType, naturalKey);
        String signature = statusPolicy.payloadSignature(factType, item.getPayload());
        String previousSignature = operationSignatures.putIfAbsent(scopeKey, signature);
        if (previousSignature == null) {
            return DuplicateDecision.NONE;
        }
        if (statusPolicy.isConflictingDuplicate(previousSignature, signature)) {
            result.conflict();
            return DuplicateDecision.CONFLICT;
        }
        result.unchanged();
        return DuplicateDecision.UNCHANGED;
    }

    private void supersedeActiveIfNeeded(
            LogisticsQuoteFactType factType,
            String naturalKey,
            LogisticsQuoteFactLandingResult result
    ) {
        if (repository.hasActiveFactWithNaturalKey(factType, naturalKey)) {
            repository.supersedeActiveFacts(factType, naturalKey);
            result.superseded();
        }
    }

    private Long sourceVersionItemId(LogisticsQuotePublishedItem item) {
        return item.getSourceLineage() == null ? null : item.getSourceLineage().getSourceVersionItemId();
    }

    private LogisticsServiceLineFact toServiceLine(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        return new LogisticsServiceLineFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                text(payload.get("forwarderName")),
                text(payload.get("country")),
                null,
                text(payload.get("destinationNode")),
                text(payload.get("transportMode")),
                text(payload.get("serviceScope")),
                text(payload.get("channelName")),
                text(payload.get("originWarehouse")),
                text(payload.get("destinationWarehouse")),
                text(payload.get("departureFrequency")),
                integer(payload.get("leadTimeMinDays")),
                integer(payload.get("leadTimeMaxDays")),
                text(payload.get("effectiveDate")),
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsCargoCategoryFact toCargoCategory(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        boolean manualConfirmRequired = bool(payload.get("manualConfirmRequired"));
        return new LogisticsCargoCategoryFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                keyText(payload.get("serviceLineKey")),
                text(payload.get("categoryCode")),
                text(payload.get("categoryName")),
                text(payload.get("sourceCategoryName")),
                text(payload.get("productExamples")),
                text(payload.get("keywords")),
                text(payload.get("electricType")),
                text(payload.get("sensitiveTags")),
                text(payload.get("packingPolicy")),
                manualConfirmRequired,
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsPriceRuleFact toPriceRule(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        String priceStatus = text(payload.get("priceStatus"));
        if (isBlank(priceStatus)) {
            priceStatus = "NORMAL";
        }
        return new LogisticsPriceRuleFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                keyText(payload.get("serviceLineKey")),
                keyText(payload.get("cargoCategoryKey")),
                decimal(payload.get("unitPrice")),
                text(payload.get("currency")),
                text(payload.get("billingUnit")),
                text(payload.get("pricingModel")),
                decimal(payload.get("minimumBillableUnit")),
                text(payload.get("minimumBillableUnitType")),
                decimal(payload.get("minimumCharge")),
                decimal(payload.get("volumeDivisor")),
                text(payload.get("seaWeightRatio")),
                text(payload.get("roundingRule")),
                priceStatus,
                text(payload.get("effectiveDate")),
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsSurchargeRuleFact toSurchargeRule(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        return new LogisticsSurchargeRuleFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                keyText(payload.get("serviceLineKey")),
                text(payload.get("surchargeName")),
                text(payload.get("surchargeType")),
                text(payload.get("triggerCondition")),
                text(payload.get("pricingModel")),
                decimal(payload.get("amount")),
                decimal(payload.get("rate")),
                text(payload.get("currency")),
                text(payload.get("billingUnit")),
                decimal(payload.get("minimumCharge")),
                bool(payload.get("includedInBasePrice")),
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsBillingRuleFact toBillingRule(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        return new LogisticsBillingRuleFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                keyText(payload.get("serviceLineKey")),
                keyText(payload.get("cargoCategoryKey")),
                text(payload.get("ruleName")),
                text(payload.get("ruleType")),
                text(payload.get("conditionText")),
                text(payload.get("structuredField")),
                text(payload.get("operator")),
                decimal(payload.get("thresholdValue")),
                text(payload.get("thresholdUnit")),
                text(payload.get("actionText")),
                text(payload.get("severity")),
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsRestrictionRuleFact toRestrictionRule(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        return new LogisticsRestrictionRuleFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                keyText(payload.get("serviceLineKey")),
                text(payload.get("restrictionType")),
                text(payload.get("itemText")),
                text(payload.get("requirementText")),
                text(payload.get("applicabilityScope")),
                text(payload.get("severity")),
                bool(payload.get("manualConfirmRequired")),
                status,
                item.getSourceLineage()
        );
    }

    private LogisticsWarehouseFeeRuleFact toWarehouseFeeRule(LogisticsQuotePublishedItem item, String naturalKey, String status) {
        Map<String, Object> payload = item.getPayload();
        return new LogisticsWarehouseFeeRuleFact(
                naturalKey,
                text(payload.get("forwarderCode")),
                text(payload.get("country")),
                text(payload.get("warehouseNode")),
                text(payload.get("serviceName")),
                text(payload.get("serviceType")),
                text(payload.get("processingScope")),
                text(payload.get("feeType")),
                text(payload.get("pricingModel")),
                decimal(payload.get("amount")),
                decimal(payload.get("rate")),
                text(payload.get("currency")),
                text(payload.get("billingUnit")),
                text(payload.get("conditionText")),
                text(payload.get("freeCondition")),
                status,
                item.getSourceLineage()
        );
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String keyText(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        return text.replace("|FBN|", "|").replace("|FBP|", "|");
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = text(value);
        if (text == null || text.isEmpty()) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = text(value);
        if (isBlank(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum DuplicateDecision {
        NONE,
        UNCHANGED,
        CONFLICT
    }
}

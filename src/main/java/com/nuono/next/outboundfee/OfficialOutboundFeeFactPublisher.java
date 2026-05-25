package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class OfficialOutboundFeeFactPublisher {

    private final OfficialOutboundFeeFactRepository repository;
    private final OfficialOutboundFeeFactStatusPolicy statusPolicy;

    public OfficialOutboundFeeFactPublisher(OfficialOutboundFeeFactRepository repository) {
        this.repository = repository;
        this.statusPolicy = new OfficialOutboundFeeFactStatusPolicy();
    }

    public OfficialOutboundFeeFactLandingResult land(List<OfficialOutboundFeePublishedItem> items) {
        OfficialOutboundFeeFactLandingResult result = new OfficialOutboundFeeFactLandingResult();
        Map<String, String> operationSignatures = new HashMap<>();
        for (OfficialOutboundFeePublishedItem item : items) {
            if (item == null) {
                result.skipped();
                continue;
            }
            OfficialOutboundFeeFactType factType;
            try {
                factType = OfficialOutboundFeeFactType.fromItemType(item.getItemType());
            } catch (IllegalArgumentException ignored) {
                result.skipped();
                continue;
            }
            if (isBlank(item.getNaturalKey())) {
                result.skipped();
                continue;
            }
            if (OfficialOutboundFeeFactType.SIZE_CLASSIFICATION == factType) {
                landSizeClassification(item, result, operationSignatures);
            } else if (OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB == factType) {
                landWeightSlab(item, result, operationSignatures);
            } else if (OfficialOutboundFeeFactType.CALCULATION_POLICY == factType) {
                landCalculationPolicy(item, result, operationSignatures);
            }
        }
        return result;
    }

    public OfficialOutboundFeeFactLandingResult landFullSnapshot(List<OfficialOutboundFeePublishedItem> items) {
        OfficialOutboundFeeFactLandingResult result = land(items);
        supersedeMissingSnapshotFacts(items, result);
        return result;
    }

    private void supersedeMissingSnapshotFacts(
            List<OfficialOutboundFeePublishedItem> items,
            OfficialOutboundFeeFactLandingResult result
    ) {
        Map<SnapshotScope, List<String>> naturalKeysByScope = new LinkedHashMap<>();
        for (OfficialOutboundFeePublishedItem item : items) {
            if (item == null || isBlank(item.getNaturalKey())) {
                continue;
            }
            OfficialOutboundFeeFactType factType;
            try {
                factType = OfficialOutboundFeeFactType.fromItemType(item.getItemType());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Map<String, Object> payload = item.getPayload();
            String country = text(payload.get("country"));
            String platform = text(payload.get("platform"));
            String fulfillmentType = text(payload.get("fulfillmentType"));
            if (isBlank(country) || isBlank(platform) || isBlank(fulfillmentType)) {
                continue;
            }
            SnapshotScope scope = new SnapshotScope(factType, country, platform, fulfillmentType);
            naturalKeysByScope.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(item.getNaturalKey());
        }
        for (Map.Entry<SnapshotScope, List<String>> entry : naturalKeysByScope.entrySet()) {
            SnapshotScope scope = entry.getKey();
            int supersededCount = repository.supersedeActiveFactsMissingFromSnapshot(
                    scope.factType,
                    scope.country,
                    scope.platform,
                    scope.fulfillmentType,
                    entry.getValue()
            );
            result.superseded(supersededCount);
        }
    }

    private void landSizeClassification(
            OfficialOutboundFeePublishedItem item,
            OfficialOutboundFeeFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("country")))
                || isBlank(text(payload.get("platform")))
                || isBlank(text(payload.get("fulfillmentType")))
                || isBlank(text(payload.get("classificationName")))) {
            result.skipped();
            return;
        }
        if (!isUnboundedBulkyClassification(payload)
                && (isBlank(text(payload.get("longestSideMaxCm")))
                || isBlank(text(payload.get("medianSideMaxCm")))
                || isBlank(text(payload.get("shortestSideMaxCm")))
                || isBlank(text(payload.get("maxShippingWeightGrams"))))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findSizeClassificationBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                OfficialOutboundFeeFactType.SIZE_CLASSIFICATION,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertSizeClassification(toSizeClassification(item, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(OfficialOutboundFeeFactType.SIZE_CLASSIFICATION, item.getNaturalKey(), result);
        repository.insertSizeClassification(toSizeClassification(item, statusPolicy.initialStatus(payload)));
        result.inserted();
    }

    private void landWeightSlab(
            OfficialOutboundFeePublishedItem item,
            OfficialOutboundFeeFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("country")))
                || isBlank(text(payload.get("platform")))
                || isBlank(text(payload.get("fulfillmentType")))
                || isBlank(text(payload.get("classificationName")))
                || isBlank(text(payload.get("weightMaxGrams")))
                || isBlank(text(payload.get("standardFeeAmount")))
                || isBlank(text(payload.get("currency")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findWeightSlabBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertWeightSlab(toWeightSlab(item, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB, item.getNaturalKey(), result);
        repository.insertWeightSlab(toWeightSlab(item, statusPolicy.initialStatus(payload)));
        result.inserted();
    }

    private void landCalculationPolicy(
            OfficialOutboundFeePublishedItem item,
            OfficialOutboundFeeFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        Map<String, Object> payload = item.getPayload();
        if (isBlank(text(payload.get("country")))
                || isBlank(text(payload.get("platform")))
                || isBlank(text(payload.get("fulfillmentType")))
                || isBlank(text(payload.get("shippingWeightFormula")))) {
            result.skipped();
            return;
        }
        Long sourceVersionItemId = sourceVersionItemId(item);
        if (sourceVersionItemId != null && repository.findCalculationPolicyBySourceVersionItemId(sourceVersionItemId).isPresent()) {
            result.unchanged();
            return;
        }
        DuplicateDecision duplicateDecision = recordDuplicateOrConflict(
                OfficialOutboundFeeFactType.CALCULATION_POLICY,
                item,
                result,
                operationSignatures
        );
        if (DuplicateDecision.CONFLICT == duplicateDecision) {
            repository.insertCalculationPolicy(toCalculationPolicy(item, statusPolicy.conflictStatus()));
            return;
        }
        if (DuplicateDecision.UNCHANGED == duplicateDecision) {
            return;
        }
        supersedeActiveIfNeeded(OfficialOutboundFeeFactType.CALCULATION_POLICY, item.getNaturalKey(), result);
        repository.insertCalculationPolicy(toCalculationPolicy(item, statusPolicy.initialStatus(payload)));
        result.inserted();
    }

    private DuplicateDecision recordDuplicateOrConflict(
            OfficialOutboundFeeFactType factType,
            OfficialOutboundFeePublishedItem item,
            OfficialOutboundFeeFactLandingResult result,
            Map<String, String> operationSignatures
    ) {
        String scopeKey = factType.name() + "|" + item.getNaturalKey();
        String signature = item.getPayload().toString();
        String previousSignature = operationSignatures.putIfAbsent(scopeKey, signature);
        if (previousSignature == null) {
            return DuplicateDecision.NONE;
        }
        if (!previousSignature.equals(signature)) {
            result.conflict();
            return DuplicateDecision.CONFLICT;
        }
        result.unchanged();
        return DuplicateDecision.UNCHANGED;
    }

    private void supersedeActiveIfNeeded(
            OfficialOutboundFeeFactType factType,
            String naturalKey,
            OfficialOutboundFeeFactLandingResult result
    ) {
        if (repository.hasActiveFactWithNaturalKey(factType, naturalKey)) {
            repository.supersedeActiveFacts(factType, naturalKey);
            result.superseded();
        }
    }

    private OutboundSizeClassificationRuleFact toSizeClassification(OfficialOutboundFeePublishedItem item, String status) {
        Map<String, Object> payload = item.getPayload();
        return new OutboundSizeClassificationRuleFact(
                item.getNaturalKey(),
                text(payload.get("country")),
                text(payload.get("platform")),
                text(payload.get("fulfillmentType")),
                text(payload.get("classificationName")),
                decimal(payload.get("longestSideMaxCm")),
                decimal(payload.get("medianSideMaxCm")),
                decimal(payload.get("shortestSideMaxCm")),
                decimal(payload.get("maxShippingWeightGrams")),
                decimal(payload.get("packagingWeightGrams")),
                integer(payload.get("priority")),
                text(payload.get("dimensionUnit")),
                text(payload.get("weightUnit")),
                text(payload.get("effectiveDate")),
                status,
                item.getSourceLineage()
        );
    }

    private OutboundFeeWeightSlabRuleFact toWeightSlab(OfficialOutboundFeePublishedItem item, String status) {
        Map<String, Object> payload = item.getPayload();
        return new OutboundFeeWeightSlabRuleFact(
                item.getNaturalKey(),
                text(payload.get("country")),
                text(payload.get("platform")),
                text(payload.get("fulfillmentType")),
                text(payload.get("classificationName")),
                decimal(payload.get("weightMinGrams")),
                boolObject(payload.get("weightMinInclusive")),
                decimal(payload.get("weightMaxGrams")),
                boolObject(payload.get("weightMaxInclusive")),
                decimal(payload.get("standardFeeAmount")),
                decimal(payload.get("highAspFeeAmount")),
                decimal(payload.get("salesPriceThresholdAmount")),
                text(payload.get("thresholdCurrency")),
                decimal(payload.get("extraWeightStepGrams")),
                decimal(payload.get("extraFeeAmount")),
                text(payload.get("currency")),
                text(payload.get("effectiveDate")),
                status,
                item.getSourceLineage()
        );
    }

    private OutboundFeeCalculationPolicyFact toCalculationPolicy(OfficialOutboundFeePublishedItem item, String status) {
        Map<String, Object> payload = item.getPayload();
        return new OutboundFeeCalculationPolicyFact(
                item.getNaturalKey(),
                text(payload.get("country")),
                text(payload.get("platform")),
                text(payload.get("fulfillmentType")),
                text(payload.get("policyName")),
                text(payload.get("shippingWeightFormula")),
                text(payload.get("dimensionSortRule")),
                text(payload.get("weightBoundaryRule")),
                text(payload.get("roundingRule")),
                decimal(payload.get("salesPriceThresholdAmount")),
                text(payload.get("thresholdCurrency")),
                text(payload.get("dimensionUnit")),
                text(payload.get("weightUnit")),
                text(payload.get("effectiveDate")),
                status,
                item.getSourceLineage()
        );
    }

    private Long sourceVersionItemId(OfficialOutboundFeePublishedItem item) {
        return item.getSourceLineage() == null ? null : item.getSourceLineage().getSourceVersionItemId();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integer(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = text(value);
        return isBlank(text) ? null : Integer.valueOf(text);
    }

    private Boolean boolObject(Object value) {
        if (value == null) {
            return null;
        }
        return bool(value);
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
        return isBlank(text) ? null : new BigDecimal(text);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isUnboundedBulkyClassification(Map<String, Object> payload) {
        return "bulky".equalsIgnoreCase(text(payload.get("classificationName")));
    }

    private enum DuplicateDecision {
        NONE,
        UNCHANGED,
        CONFLICT
    }

    private static final class SnapshotScope {
        private final OfficialOutboundFeeFactType factType;
        private final String country;
        private final String platform;
        private final String fulfillmentType;

        private SnapshotScope(
                OfficialOutboundFeeFactType factType,
                String country,
                String platform,
                String fulfillmentType
        ) {
            this.factType = factType;
            this.country = country;
            this.platform = platform;
            this.fulfillmentType = fulfillmentType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SnapshotScope)) {
                return false;
            }
            SnapshotScope that = (SnapshotScope) other;
            return factType == that.factType
                    && country.equals(that.country)
                    && platform.equals(that.platform)
                    && fulfillmentType.equals(that.fulfillmentType);
        }

        @Override
        public int hashCode() {
            int result = factType.hashCode();
            result = 31 * result + country.hashCode();
            result = 31 * result + platform.hashCode();
            result = 31 * result + fulfillmentType.hashCode();
            return result;
        }
    }
}

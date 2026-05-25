package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialOutboundFeeCalculator {

    private final OfficialOutboundFeeFactRepository repository;
    private final OfficialOutboundFeeFactStatusPolicy statusPolicy;

    public OfficialOutboundFeeCalculator(OfficialOutboundFeeFactRepository repository) {
        this.repository = repository;
        this.statusPolicy = new OfficialOutboundFeeFactStatusPolicy();
    }

    public OfficialOutboundFeeCalculationResult calculate(OfficialOutboundFeeCalculationRequest request) {
        if (request == null
                || request.getLengthCm() == null
                || request.getWidthCm() == null
                || request.getHeightCm() == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.MISSING_DIMENSIONS);
        }
        if (request.getProductWeightGrams() == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.MISSING_WEIGHT);
        }
        if (request.getSalePriceAmount() == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.MISSING_SALE_PRICE);
        }

        OutboundFeeCalculationPolicyFact policy = repository.findActiveCalculationPolicy(
                        request.getCountry(),
                        request.getPlatform(),
                        request.getFulfillmentType(),
                        request.getCalculationDate()
                )
                .filter(item -> statusPolicy.isBusinessReadable(item.getStatus()))
                .orElse(null);
        if (policy == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.POLICY_NOT_FOUND);
        }
        if (StringUtils.hasText(request.getCurrency())
                && StringUtils.hasText(policy.getThresholdCurrency())
                && !request.getCurrency().equalsIgnoreCase(policy.getThresholdCurrency())) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.CURRENCY_MISMATCH);
        }

        List<BigDecimal> dimensions = sortedDimensions(request);
        OutboundSizeClassificationRuleFact classification = matchClassification(request, dimensions);
        if (classification == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.CLASSIFICATION_NOT_FOUND);
        }

        BigDecimal packagingWeight = nvl(classification.getPackagingWeightGrams());
        BigDecimal shippingWeight = request.getProductWeightGrams().add(packagingWeight);
        OutboundFeeWeightSlabRuleFact slab = matchSlab(request, classification.getClassificationName(), shippingWeight);
        if (slab == null) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.SLAB_NOT_FOUND);
        }
        if (StringUtils.hasText(request.getCurrency())
                && StringUtils.hasText(slab.getCurrency())
                && !request.getCurrency().equalsIgnoreCase(slab.getCurrency())) {
            return OfficialOutboundFeeCalculationResult.failure(OfficialOutboundFeeCalculationFailure.CURRENCY_MISMATCH);
        }

        BigDecimal threshold = slab.getSalesPriceThresholdAmount() == null
                ? policy.getSalesPriceThresholdAmount()
                : slab.getSalesPriceThresholdAmount();
        BigDecimal fee = feeForPrice(request.getSalePriceAmount(), threshold, slab);
        int extraSteps = extraSteps(shippingWeight, slab);
        if (extraSteps > 0 && slab.getExtraFeeAmount() != null) {
            fee = fee.add(slab.getExtraFeeAmount().multiply(BigDecimal.valueOf(extraSteps)));
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sortedDimensionsCm", dimensions);
        evidence.put("packagingWeightGrams", packagingWeight);
        evidence.put("shippingWeightGrams", normalizeNumber(shippingWeight));
        evidence.put("salesPriceThresholdAmount", threshold == null ? null : normalizeNumber(threshold));
        evidence.put("extraSteps", extraSteps);
        evidence.put("policyNaturalKey", policy.getNaturalKey());
        evidence.put("classificationNaturalKey", classification.getNaturalKey());
        evidence.put("slabNaturalKey", slab.getNaturalKey());

        Long sourceVersionId = slab.getSourceLineage() == null ? null : slab.getSourceLineage().getSourceVersionId();
        return OfficialOutboundFeeCalculationResult.success(
                normalizeNumber(fee),
                slab.getCurrency(),
                classification.getClassificationName(),
                slab.getNaturalKey(),
                sourceVersionId,
                evidence
        );
    }

    private OutboundSizeClassificationRuleFact matchClassification(
            OfficialOutboundFeeCalculationRequest request,
            List<BigDecimal> dimensions
    ) {
        return repository.findActiveSizeClassifications(
                        request.getCountry(),
                        request.getPlatform(),
                        request.getFulfillmentType(),
                        request.getCalculationDate()
                )
                .stream()
                .filter(item -> statusPolicy.isBusinessReadable(item.getStatus()))
                .filter(item -> sameScope(request, item.getCountry(), item.getPlatform(), item.getFulfillmentType()))
                .filter(item -> dimensionFits(dimensions.get(0), item.getLongestSideMaxCm()))
                .filter(item -> dimensionFits(dimensions.get(1), item.getMedianSideMaxCm()))
                .filter(item -> dimensionFits(dimensions.get(2), item.getShortestSideMaxCm()))
                .filter(item -> {
                    BigDecimal shippingWeight = request.getProductWeightGrams().add(nvl(item.getPackagingWeightGrams()));
                    return item.getMaxShippingWeightGrams() == null || shippingWeight.compareTo(item.getMaxShippingWeightGrams()) <= 0;
                })
                .sorted(Comparator
                        .comparing((OutboundSizeClassificationRuleFact item) -> item.getPriority() == null ? Integer.MAX_VALUE : item.getPriority())
                        .thenComparing(OutboundSizeClassificationRuleFact::getMaxShippingWeightGrams, Comparator.nullsLast(BigDecimal::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private OutboundFeeWeightSlabRuleFact matchSlab(
            OfficialOutboundFeeCalculationRequest request,
            String classificationName,
            BigDecimal shippingWeight
    ) {
        List<OutboundFeeWeightSlabRuleFact> candidates = repository.findActiveWeightSlabs(
                        request.getCountry(),
                        request.getPlatform(),
                        request.getFulfillmentType(),
                        request.getCalculationDate()
                )
                .stream()
                .filter(item -> statusPolicy.isBusinessReadable(item.getStatus()))
                .filter(item -> sameScope(request, item.getCountry(), item.getPlatform(), item.getFulfillmentType()))
                .filter(item -> classificationName.equals(item.getClassificationName()))
                .filter(item -> !StringUtils.hasText(request.getCurrency()) || request.getCurrency().equalsIgnoreCase(item.getCurrency()))
                .collect(java.util.stream.Collectors.toList());

        for (OutboundFeeWeightSlabRuleFact candidate : candidates) {
            if (weightInRange(shippingWeight, candidate)) {
                return candidate;
            }
        }
        return candidates.stream()
                .filter(item -> item.getWeightMaxGrams() != null)
                .filter(item -> shippingWeight.compareTo(item.getWeightMaxGrams()) > 0)
                .filter(item -> item.getExtraWeightStepGrams() != null && item.getExtraFeeAmount() != null)
                .max(Comparator.comparing(OutboundFeeWeightSlabRuleFact::getWeightMaxGrams))
                .orElse(null);
    }

    private boolean weightInRange(BigDecimal shippingWeight, OutboundFeeWeightSlabRuleFact slab) {
        if (slab.getWeightMinGrams() != null) {
            int minCompare = shippingWeight.compareTo(slab.getWeightMinGrams());
            boolean minInclusive = slab.getWeightMinInclusive() == null || slab.getWeightMinInclusive();
            if (minCompare < 0 || (minCompare == 0 && !minInclusive)) {
                return false;
            }
        }
        if (slab.getWeightMaxGrams() != null) {
            int maxCompare = shippingWeight.compareTo(slab.getWeightMaxGrams());
            boolean maxInclusive = slab.getWeightMaxInclusive() == null || slab.getWeightMaxInclusive();
            return maxCompare < 0 || (maxCompare == 0 && maxInclusive);
        }
        return true;
    }

    private BigDecimal feeForPrice(BigDecimal salePrice, BigDecimal threshold, OutboundFeeWeightSlabRuleFact slab) {
        if (threshold != null
                && slab.getHighAspFeeAmount() != null
                && salePrice.compareTo(threshold) > 0) {
            return slab.getHighAspFeeAmount();
        }
        return slab.getStandardFeeAmount();
    }

    private int extraSteps(BigDecimal shippingWeight, OutboundFeeWeightSlabRuleFact slab) {
        if (slab.getWeightMaxGrams() == null
                || slab.getExtraWeightStepGrams() == null
                || slab.getExtraWeightStepGrams().compareTo(BigDecimal.ZERO) <= 0
                || shippingWeight.compareTo(slab.getWeightMaxGrams()) <= 0) {
            return 0;
        }
        return shippingWeight.subtract(slab.getWeightMaxGrams())
                .divide(slab.getExtraWeightStepGrams(), 0, RoundingMode.CEILING)
                .intValue();
    }

    private List<BigDecimal> sortedDimensions(OfficialOutboundFeeCalculationRequest request) {
        List<BigDecimal> dimensions = new ArrayList<>();
        dimensions.add(request.getLengthCm());
        dimensions.add(request.getWidthCm());
        dimensions.add(request.getHeightCm());
        dimensions.sort(Comparator.reverseOrder());
        return dimensions;
    }

    private boolean sameScope(
            OfficialOutboundFeeCalculationRequest request,
            String country,
            String platform,
            String fulfillmentType
    ) {
        return sameText(request.getCountry(), country)
                && sameText(request.getPlatform(), platform)
                && sameText(request.getFulfillmentType(), fulfillmentType);
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean dimensionFits(BigDecimal actual, BigDecimal max) {
        return max == null || actual.compareTo(max) <= 0;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalizeNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}

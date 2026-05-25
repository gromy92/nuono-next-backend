package com.nuono.next.outboundfee;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class OfficialOutboundFeeCalculationResult {

    private final boolean success;
    private final OfficialOutboundFeeCalculationFailure failure;
    private final BigDecimal finalFeeAmount;
    private final String currency;
    private final String matchedClassificationName;
    private final String matchedSlabNaturalKey;
    private final Long sourceVersionId;
    private final Map<String, Object> evidence;

    private OfficialOutboundFeeCalculationResult(
            boolean success,
            OfficialOutboundFeeCalculationFailure failure,
            BigDecimal finalFeeAmount,
            String currency,
            String matchedClassificationName,
            String matchedSlabNaturalKey,
            Long sourceVersionId,
            Map<String, Object> evidence
    ) {
        this.success = success;
        this.failure = failure;
        this.finalFeeAmount = finalFeeAmount;
        this.currency = currency;
        this.matchedClassificationName = matchedClassificationName;
        this.matchedSlabNaturalKey = matchedSlabNaturalKey;
        this.sourceVersionId = sourceVersionId;
        this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
    }

    static OfficialOutboundFeeCalculationResult success(
            BigDecimal finalFeeAmount,
            String currency,
            String matchedClassificationName,
            String matchedSlabNaturalKey,
            Long sourceVersionId,
            Map<String, Object> evidence
    ) {
        return new OfficialOutboundFeeCalculationResult(
                true,
                null,
                finalFeeAmount,
                currency,
                matchedClassificationName,
                matchedSlabNaturalKey,
                sourceVersionId,
                evidence
        );
    }

    static OfficialOutboundFeeCalculationResult failure(OfficialOutboundFeeCalculationFailure failure) {
        return new OfficialOutboundFeeCalculationResult(false, failure, null, null, null, null, null, Map.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public OfficialOutboundFeeCalculationFailure getFailure() {
        return failure;
    }

    public BigDecimal getFinalFeeAmount() {
        return finalFeeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMatchedClassificationName() {
        return matchedClassificationName;
    }

    public String getMatchedSlabNaturalKey() {
        return matchedSlabNaturalKey;
    }

    public Long getSourceVersionId() {
        return sourceVersionId;
    }

    public Map<String, Object> getEvidence() {
        return new LinkedHashMap<>(evidence);
    }
}

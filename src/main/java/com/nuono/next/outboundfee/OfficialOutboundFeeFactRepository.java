package com.nuono.next.outboundfee;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OfficialOutboundFeeFactRepository {

    Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId);

    default List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return List.of();
    }

    void insertSizeClassification(OutboundSizeClassificationRuleFact fact);

    Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId);

    default List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return List.of();
    }

    void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact);

    Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId);

    default Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(
            String country,
            String platform,
            String fulfillmentType,
            LocalDate calculationDate
    ) {
        return Optional.empty();
    }

    void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact);

    boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey);

    void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey);

    default int supersedeActiveFactsMissingFromSnapshot(
            OfficialOutboundFeeFactType factType,
            String country,
            String platform,
            String fulfillmentType,
            List<String> snapshotNaturalKeys
    ) {
        return 0;
    }
}

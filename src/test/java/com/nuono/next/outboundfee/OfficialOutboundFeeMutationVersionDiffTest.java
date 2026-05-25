package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.filemanagement.parse.FileParseItemStandardRow;
import com.nuono.next.filemanagement.parse.FileParseOfficialOutboundFeeStandard;
import com.nuono.next.filemanagement.parse.FileParseResultDiffService;
import com.nuono.next.filemanagement.parse.FileParseStructuredAiResult;
import com.nuono.next.filemanagement.parse.FileParseStructuredItem;
import com.nuono.next.filemanagement.parse.FileParseTaskRow;
import com.nuono.next.filemanagement.parse.FileParseVersionItemRow;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialOutboundFeeMutationVersionDiffTest {

    @Mock
    private FileManagementParseMapper fileManagementParseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coversMutationVersionDiffPublishSupersedeAndRecalculationEvidence() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setBaseVersionId(81001L);
        List<FileParseVersionItemRow> baseItems = List.of(
                versionItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, smallClassification("Small Envelope")),
                versionItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, mediumClassification("Medium")),
                versionItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, smallSlab("Small Envelope", "500", "6.5", "8.5", "50", "2")),
                versionItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, boundarySlab("Small Envelope", "500", "6.5")),
                versionItem(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, policy("50"))
        );
        when(fileManagementParseMapper.selectVersionItems(81001L)).thenReturn(baseItems);

        FileParseStructuredAiResult structuredResult = new FileParseStructuredAiResult();
        FileParseStructuredItem feeChange = structuredItem(
                FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                smallSlab("Small Envelope", "500", "7.25", "9.25", "45", "3")
        );
        FileParseStructuredItem newClassification = structuredItem(
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                bulkyClassification("Bulky")
        );
        FileParseStructuredItem renamedClassification = structuredItem(
                FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                mediumClassification("Compact")
        );
        FileParseStructuredItem boundaryChange = structuredItem(
                FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                boundarySlab("Small Envelope", "1000", "11")
        );
        FileParseStructuredItem thresholdChange = structuredItem(
                FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                policy("45")
        );
        structuredResult.setItems(List.of(feeChange, newClassification, renamedClassification, boundaryChange, thresholdChange));

        new FileParseResultDiffService(fileManagementParseMapper, objectMapper)
                .applyDiff(task, itemStandards(), structuredResult);

        assertEquals("changed", feeChange.getChangeType());
        assertTrue(feeChange.getChangedFieldKeysJson().contains("standardFeeAmount"));
        assertTrue(feeChange.getChangedFieldKeysJson().contains("highAspFeeAmount"));
        assertTrue(feeChange.getChangedFieldKeysJson().contains("salesPriceThresholdAmount"));
        assertTrue(feeChange.getChangedFieldKeysJson().contains("extraFeeAmount"));
        assertEquals("changed", thresholdChange.getChangeType());
        assertTrue(thresholdChange.getChangedFieldKeysJson().contains("salesPriceThresholdAmount"));
        assertEquals("added", newClassification.getChangeType());
        assertEquals("added", renamedClassification.getChangeType());
        assertEquals("added", boundaryChange.getChangeType());
        assertTrue(structuredResult.getItems().stream()
                .anyMatch(item -> "delete_suspected".equals(item.getChangeType())
                        && item.getOldPayloadJson().contains("\"classificationName\":\"Medium\"")));
        assertTrue(structuredResult.getItems().stream()
                .anyMatch(item -> "delete_suspected".equals(item.getChangeType())
                        && item.getOldPayloadJson().contains("\"weightMaxGrams\":\"500\"")));

        InMemoryRepository repository = new InMemoryRepository();
        OfficialOutboundFeeFactPublisher publisher = new OfficialOutboundFeeFactPublisher(repository);
        publisher.landFullSnapshot(List.of(
                publishedItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, naturalKey("KSA", "NOON", "FBN", "Small Envelope", "2026-05-01"), smallClassification("Small Envelope"), 91001L, 9100L),
                publishedItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, naturalKey("KSA", "NOON", "FBN", "Medium", "2026-05-01"), mediumClassification("Medium"), 91005L, 9100L),
                publishedItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, slabNaturalKey("Small Envelope", "0", "500"), smallSlab("Small Envelope", "500", "6.5", "8.5", "50", "2"), 91002L, 9100L),
                publishedItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, slabNaturalKey("Small Envelope", "500", "1000"), boundarySlab("Small Envelope", "1000", "11"), 91006L, 9100L),
                publishedItem(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, policyNaturalKey("KSA"), policy("50"), 91003L, 9100L),
                publishedItem(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, policyNaturalKey("UAE"), uaePolicy(), 91004L, 9100L)
        ));
        OfficialOutboundFeeCalculator calculator = new OfficialOutboundFeeCalculator(repository);
        assertEquals(new BigDecimal("6.5"), calculate(calculator, "KSA", "SAR", "48", "280", "18", "8", "8").getFinalFeeAmount());
        assertEquals(new BigDecimal("10.5"), calculate(calculator, "KSA", "SAR", "40", "1280", "18", "8", "8").getFinalFeeAmount());

        OfficialOutboundFeeFactLandingResult secondPublish = publisher.landFullSnapshot(List.of(
                publishedItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, naturalKey("KSA", "NOON", "FBN", "Small Envelope", "2026-05-01"), smallClassification("Small Envelope"), 92001L, 9200L),
                publishedItem(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION, naturalKey("KSA", "NOON", "FBN", "Bulky", "2026-05-01"), bulkyClassification("Bulky"), 92002L, 9200L),
                publishedItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, slabNaturalKey("Small Envelope", "0", "500"), smallSlab("Small Envelope", "500", "7.25", "9.25", "45", "3"), 92003L, 9200L),
                publishedItem(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB, slabNaturalKey("Bulky", "0", "5000"), bulkySlab(), 92004L, 9200L),
                publishedItem(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, policyNaturalKey("KSA"), policy("45"), 92005L, 9200L)
        ));

        assertEquals(5, secondPublish.getSupersededCount());
        assertEquals(new BigDecimal("9.25"), calculate(calculator, "KSA", "SAR", "48", "280", "18", "8", "8").getFinalFeeAmount());
        assertEquals(new BigDecimal("13.25"), calculate(calculator, "KSA", "SAR", "40", "1280", "18", "8", "8").getFinalFeeAmount());
        assertEquals("Bulky", calculate(calculator, "KSA", "SAR", "40", "1200", "45", "40", "35").getMatchedClassificationName());
        assertEquals(0, repository.activeClassificationCount("KSA", "Medium"));
        assertEquals(0, repository.activeSlabCount(slabNaturalKey("Small Envelope", "500", "1000")));
        assertEquals(1, repository.activePolicyCount("UAE"));
    }

    private OfficialOutboundFeeCalculationResult calculate(
            OfficialOutboundFeeCalculator calculator,
            String country,
            String currency,
            String salePrice,
            String weightGrams,
            String lengthCm,
            String widthCm,
            String heightCm
    ) {
        return calculator.calculate(new OfficialOutboundFeeCalculationRequest(
                country,
                "NOON",
                "FBN",
                currency,
                new BigDecimal(salePrice),
                new BigDecimal(weightGrams),
                new BigDecimal(lengthCm),
                new BigDecimal(widthCm),
                new BigDecimal(heightCm),
                LocalDate.parse("2026-05-24")
        ));
    }

    private List<FileParseItemStandardRow> itemStandards() {
        return List.of(
                itemStandard(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION),
                itemStandard(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB),
                itemStandard(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY)
        );
    }

    private FileParseItemStandardRow itemStandard(String itemType) {
        FileParseOfficialOutboundFeeStandard.ItemTypeDefinition definition =
                FileParseOfficialOutboundFeeStandard.definition(itemType);
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(itemType);
        row.setDiffRuleJson("{\"compareFields\":[\"" + String.join("\",\"", definition.getCompareFields()) + "\"]}");
        return row;
    }

    private FileParseStructuredItem structuredItem(String itemType, Map<String, Object> payload) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(itemType);
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson(writeJson(payload));
        return item;
    }

    private FileParseVersionItemRow versionItem(String itemType, Map<String, Object> payload) {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId((long) writeJson(payload).hashCode());
        row.setVersionId(81001L);
        row.setItemType(itemType);
        row.setNaturalKeyHash("hash-" + Math.abs(writeJson(payload).hashCode()));
        row.setVersionPayloadJson(writeJson(payload));
        row.setSortNo(1);
        return row;
    }

    private OfficialOutboundFeePublishedItem publishedItem(
            String itemType,
            String naturalKey,
            Map<String, Object> payload,
            Long sourceVersionItemId,
            Long sourceVersionId
    ) {
        return new OfficialOutboundFeePublishedItem(
                itemType,
                naturalKey,
                payload,
                new OfficialOutboundFeeSourceLineage(
                        "file_management",
                        20114L,
                        40114L,
                        sourceVersionId,
                        sourceVersionItemId,
                        "mutated-fbn-fees.pdf",
                        "version_item:" + sourceVersionItemId
                )
        );
    }

    private Map<String, Object> smallClassification(String classificationName) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", classificationName,
                "longestSideMaxCm", "20",
                "medianSideMaxCm", "15",
                "shortestSideMaxCm", "10",
                "maxShippingWeightGrams", "2000",
                "packagingWeightGrams", "20",
                "priority", "1",
                "dimensionUnit", "cm",
                "weightUnit", "g",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> mediumClassification(String classificationName) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", classificationName,
                "longestSideMaxCm", "35",
                "medianSideMaxCm", "25",
                "shortestSideMaxCm", "15",
                "maxShippingWeightGrams", "3000",
                "packagingWeightGrams", "50",
                "priority", "2",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> bulkyClassification(String classificationName) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", classificationName,
                "longestSideMaxCm", "60",
                "medianSideMaxCm", "45",
                "shortestSideMaxCm", "40",
                "maxShippingWeightGrams", "5000",
                "packagingWeightGrams", "100",
                "priority", "5",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> smallSlab(
            String classificationName,
            String maxWeight,
            String standardFee,
            String highAspFee,
            String threshold,
            String extraFee
    ) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", classificationName,
                "weightMinGrams", "0",
                "weightMinInclusive", "false",
                "weightMaxGrams", maxWeight,
                "weightMaxInclusive", "true",
                "standardFeeAmount", standardFee,
                "highAspFeeAmount", highAspFee,
                "salesPriceThresholdAmount", threshold,
                "thresholdCurrency", "SAR",
                "extraWeightStepGrams", "500",
                "extraFeeAmount", extraFee,
                "currency", "SAR",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> boundarySlab(String classificationName, String maxWeight, String standardFee) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", classificationName,
                "weightMinGrams", "500",
                "weightMinInclusive", "false",
                "weightMaxGrams", maxWeight,
                "weightMaxInclusive", "true",
                "standardFeeAmount", standardFee,
                "currency", "SAR",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> bulkySlab() {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "classificationName", "Bulky",
                "weightMinGrams", "0",
                "weightMinInclusive", "false",
                "weightMaxGrams", "5000",
                "weightMaxInclusive", "true",
                "standardFeeAmount", "22",
                "currency", "SAR",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> policy(String threshold) {
        return mapOf(
                "country", "KSA",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "policyName", "Noon FBN official outbound policy",
                "shippingWeightFormula", "product_weight_plus_packaging_weight",
                "dimensionSortRule", "sort_dimensions_desc",
                "weightBoundaryRule", "min_exclusive_max_inclusive",
                "roundingRule", "no_rounding",
                "salesPriceThresholdAmount", threshold,
                "thresholdCurrency", "SAR",
                "effectiveDate", "2026-05-01"
        );
    }

    private Map<String, Object> uaePolicy() {
        return mapOf(
                "country", "UAE",
                "platform", "NOON",
                "fulfillmentType", "FBN",
                "shippingWeightFormula", "product_weight_plus_packaging_weight",
                "salesPriceThresholdAmount", "50",
                "thresholdCurrency", "AED",
                "effectiveDate", "2026-05-01"
        );
    }

    private String naturalKey(String country, String platform, String fulfillmentType, String classificationName, String effectiveDate) {
        return country + "|" + platform + "|" + fulfillmentType + "|" + classificationName + "|" + effectiveDate;
    }

    private String slabNaturalKey(String classificationName, String minWeight, String maxWeight) {
        return "KSA|NOON|FBN|" + classificationName + "|MIN:" + minWeight + "|MAX:" + maxWeight + "|CUR:SAR|2026-05-01";
    }

    private String policyNaturalKey(String country) {
        return country + "|NOON|FBN|2026-05-01";
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return map;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class InMemoryRepository implements OfficialOutboundFeeFactRepository {

        private final List<OutboundSizeClassificationRuleFact> classifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> slabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
            return classifications.stream()
                    .filter(item -> sourceVersionItemId.equals(item.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            List<OutboundSizeClassificationRuleFact> result = new ArrayList<>();
            for (OutboundSizeClassificationRuleFact fact : classifications) {
                if ("ACTIVE".equals(fact.getStatus()) && country.equals(fact.getCountry())) {
                    result.add(fact);
                }
            }
            return result;
        }

        @Override
        public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
            classifications.add(fact);
        }

        @Override
        public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
            return slabs.stream()
                    .filter(item -> sourceVersionItemId.equals(item.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            List<OutboundFeeWeightSlabRuleFact> result = new ArrayList<>();
            for (OutboundFeeWeightSlabRuleFact fact : slabs) {
                if ("ACTIVE".equals(fact.getStatus()) && country.equals(fact.getCountry())) {
                    result.add(fact);
                }
            }
            return result;
        }

        @Override
        public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
            slabs.add(fact);
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
            return policies.stream()
                    .filter(item -> sourceVersionItemId.equals(item.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return policies.stream()
                    .filter(item -> "ACTIVE".equals(item.getStatus()) && country.equals(item.getCountry()))
                    .findFirst();
        }

        @Override
        public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
            policies.add(fact);
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
            return facts(factType).stream().anyMatch(fact -> naturalKey.equals(fact.naturalKey()) && "ACTIVE".equals(fact.status()));
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
            facts(factType).stream()
                    .filter(fact -> naturalKey.equals(fact.naturalKey()) && "ACTIVE".equals(fact.status()))
                    .forEach(OfficialFactView::supersede);
        }

        @Override
        public int supersedeActiveFactsMissingFromSnapshot(
                OfficialOutboundFeeFactType factType,
                String country,
                String platform,
                String fulfillmentType,
                List<String> snapshotNaturalKeys
        ) {
            int count = 0;
            for (OfficialFactView fact : facts(factType)) {
                if ("ACTIVE".equals(fact.status())
                        && country.equals(fact.country())
                        && platform.equals(fact.platform())
                        && fulfillmentType.equals(fact.fulfillmentType())
                        && !snapshotNaturalKeys.contains(fact.naturalKey())) {
                    fact.supersede();
                    count++;
                }
            }
            return count;
        }

        private int activeClassificationCount(String country, String classificationName) {
            int count = 0;
            for (OutboundSizeClassificationRuleFact classification : classifications) {
                if ("ACTIVE".equals(classification.getStatus())
                        && country.equals(classification.getCountry())
                        && classificationName.equals(classification.getClassificationName())) {
                    count++;
                }
            }
            return count;
        }

        private int activeSlabCount(String naturalKey) {
            int count = 0;
            for (OutboundFeeWeightSlabRuleFact slab : slabs) {
                if ("ACTIVE".equals(slab.getStatus()) && naturalKey.equals(slab.getNaturalKey())) {
                    count++;
                }
            }
            return count;
        }

        private int activePolicyCount(String country) {
            int count = 0;
            for (OutboundFeeCalculationPolicyFact policy : policies) {
                if ("ACTIVE".equals(policy.getStatus()) && country.equals(policy.getCountry())) {
                    count++;
                }
            }
            return count;
        }

        private List<OfficialFactView> facts(OfficialOutboundFeeFactType factType) {
            List<OfficialFactView> facts = new ArrayList<>();
            if (OfficialOutboundFeeFactType.SIZE_CLASSIFICATION == factType) {
                classifications.forEach(fact -> facts.add(new OfficialFactView(fact)));
            } else if (OfficialOutboundFeeFactType.FEE_WEIGHT_SLAB == factType) {
                slabs.forEach(fact -> facts.add(new OfficialFactView(fact)));
            } else if (OfficialOutboundFeeFactType.CALCULATION_POLICY == factType) {
                policies.forEach(fact -> facts.add(new OfficialFactView(fact)));
            }
            return facts;
        }
    }

    private static final class OfficialFactView {
        private final Object fact;

        private OfficialFactView(Object fact) {
            this.fact = fact;
        }

        private String naturalKey() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getNaturalKey();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getNaturalKey();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getNaturalKey();
        }

        private String status() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getStatus();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getStatus();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getStatus();
        }

        private String country() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getCountry();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getCountry();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getCountry();
        }

        private String platform() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getPlatform();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getPlatform();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getPlatform();
        }

        private String fulfillmentType() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                return ((OutboundSizeClassificationRuleFact) fact).getFulfillmentType();
            }
            if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                return ((OutboundFeeWeightSlabRuleFact) fact).getFulfillmentType();
            }
            return ((OutboundFeeCalculationPolicyFact) fact).getFulfillmentType();
        }

        private void supersede() {
            if (fact instanceof OutboundSizeClassificationRuleFact) {
                ((OutboundSizeClassificationRuleFact) fact).setStatus("SUPERSEDED");
            } else if (fact instanceof OutboundFeeWeightSlabRuleFact) {
                ((OutboundFeeWeightSlabRuleFact) fact).setStatus("SUPERSEDED");
            } else {
                ((OutboundFeeCalculationPolicyFact) fact).setStatus("SUPERSEDED");
            }
        }
    }
}

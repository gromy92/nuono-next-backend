package com.nuono.next.outboundfee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.filemanagement.parse.FileParseActiveVersionRow;
import com.nuono.next.filemanagement.parse.FileParseItemStandardRow;
import com.nuono.next.filemanagement.parse.FileParsePublishCommand;
import com.nuono.next.filemanagement.parse.FileParsePublishService;
import com.nuono.next.filemanagement.parse.FileParsePublishView;
import com.nuono.next.filemanagement.parse.FileParseResultItemRow;
import com.nuono.next.filemanagement.parse.FileParseResultItemViewAssembler;
import com.nuono.next.filemanagement.parse.FileParseTargetPlanRow;
import com.nuono.next.filemanagement.parse.FileParseTaskRow;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialOutboundFeeFileManagementPublishIntegrationTest {

    @Test
    void shouldLandOfficialOutboundFactsWhenFileManagementVersionPublishes() {
        FileManagementParseMapper mapper = mock(FileManagementParseMapper.class);
        FileParseResultItemViewAssembler assembler = new FileParseResultItemViewAssembler(new ObjectMapper());
        InMemoryRepository repository = new InMemoryRepository();
        FileParsePublishService service = new FileParsePublishService(
                mapper,
                assembler,
                null,
                new OfficialOutboundFeeFactPublisher(repository)
        );

        FileParseTaskRow task = task();
        FileParseTargetPlanRow targetPlan = targetPlan();
        when(mapper.selectPublishAuditByIdempotency(20114L, "idem-official-outbound-facts")).thenReturn(null);
        when(mapper.selectActiveVersionForUpdate(4003L, "global", "global:*")).thenReturn(new FileParseActiveVersionRow());
        when(mapper.countBlockingResultItems(40114L)).thenReturn(0);
        when(mapper.countOpenHardValidationIssues(20114L)).thenReturn(0);
        when(mapper.selectResultItemsForPublish(40114L)).thenReturn(List.of(classificationItem(), slabItem(), policyItem()));
        when(mapper.nextVersionId()).thenReturn(70034L);
        when(mapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(mapper.nextVersionItemId()).thenReturn(88061L, 88062L, 88063L);
        when(mapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                any(Integer.class),
                anyLong()
        )).thenReturn(1);
        String expectedVersionNo = "OUTBOUND-FEE-KSA-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-70034";
        when(mapper.updateActiveVersion(4003L, "global", "global:*", 70034L, expectedVersionNo, 10001L))
                .thenReturn(1);
        when(mapper.markTaskPublished(20114L, 40114L, 10001L)).thenReturn(1);
        when(mapper.nextAuditLogId()).thenReturn(90034L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40114L);
        command.setConfirmMessage("确认发布官方出仓费版本");

        FileParsePublishView view = service.publish(
                task,
                targetPlan,
                List.of(
                        itemStandard("outbound_size_classification_rule"),
                        itemStandard("outbound_fee_weight_slab_rule"),
                        itemStandard("outbound_fee_calculation_policy")
                ),
                command,
                "idem-official-outbound-facts",
                10001L
        );

        assertEquals(70034L, view.getVersionId());
        assertEquals(3, view.getOutboundFeeFactInsertedCount());
        assertEquals(0, view.getOutboundFeeFactSkippedCount());
        assertEquals(1, repository.classifications.size());
        assertEquals(1, repository.slabs.size());
        assertEquals(1, repository.policies.size());
        assertEquals(88061L, repository.classifications.get(0).getSourceLineage().getSourceVersionItemId());
        assertEquals(88062L, repository.slabs.get(0).getSourceLineage().getSourceVersionItemId());
        assertEquals(88063L, repository.policies.get(0).getSourceLineage().getSourceVersionItemId());
    }

    private static FileParseTaskRow task() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setId(20114L);
        task.setTargetPlanId(4003L);
        task.setStandardVersionId(2002L);
        task.setCurrentResultId(40114L);
        task.setStatus("ready_to_publish");
        task.setDocumentTitle("fulfilled-by-noon-fbn-fees-in-ksa.pdf");
        return task;
    }

    private static FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow targetPlan = new FileParseTargetPlanRow();
        targetPlan.setId(4003L);
        targetPlan.setCode("outbound_fee_ksa");
        targetPlan.setLabel("出仓费-KSA");
        targetPlan.setDocumentType("official_outbound_fee");
        targetPlan.setStandardVersionId(2002L);
        return targetPlan;
    }

    private static FileParseResultItemRow classificationItem() {
        FileParseResultItemRow row = resultItem("outbound_size_classification_rule", "KSA|NOON|FBN|Small Envelope|2026-05-24", 1);
        row.setId(50111L);
        row.setEffectivePayloadJson("{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"longestSideMaxCm\":20,\"medianSideMaxCm\":15,\"shortestSideMaxCm\":2,\"maxShippingWeightGrams\":250,\"packagingWeightGrams\":20,\"effectiveDate\":\"2026-05-24\"}");
        return row;
    }

    private static FileParseResultItemRow slabItem() {
        FileParseResultItemRow row = resultItem("outbound_fee_weight_slab_rule", "KSA|NOON|FBN|Small Envelope|MIN:0:1|MAX:250:1|CUR:SAR|2026-05-24", 2);
        row.setId(50112L);
        row.setEffectivePayloadJson("{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"classificationName\":\"Small Envelope\",\"weightMinGrams\":0,\"weightMinInclusive\":true,\"weightMaxGrams\":250,\"weightMaxInclusive\":true,\"standardFeeAmount\":6.25,\"currency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}");
        return row;
    }

    private static FileParseResultItemRow policyItem() {
        FileParseResultItemRow row = resultItem("outbound_fee_calculation_policy", "KSA|NOON|FBN|2026-05-24", 3);
        row.setId(50113L);
        row.setEffectivePayloadJson("{\"country\":\"KSA\",\"platform\":\"NOON\",\"fulfillmentType\":\"FBN\",\"shippingWeightFormula\":\"physical_weight_plus_packaging_weight\",\"salesPriceThresholdAmount\":25,\"thresholdCurrency\":\"SAR\",\"effectiveDate\":\"2026-05-24\"}");
        return row;
    }

    private static FileParseResultItemRow resultItem(String itemType, String naturalKey, int sortNo) {
        FileParseResultItemRow row = new FileParseResultItemRow();
        row.setTaskId(20114L);
        row.setResultId(40114L);
        row.setTargetPlanId(4003L);
        row.setItemType(itemType);
        row.setNaturalKey(naturalKey);
        row.setNaturalKeyHash("hash-" + sortNo);
        row.setChangeType("added");
        row.setReviewStatus("confirmed");
        row.setValidationStatus("pass");
        row.setEffectiveValidationStatus("pass");
        row.setSortNo(sortNo);
        return row;
    }

    private static FileParseItemStandardRow itemStandard(String itemType) {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(itemType);
        row.setFieldSchemaJson("{}");
        row.setDisplayConfigJson("{}");
        row.setValidationRuleJson("{}");
        row.setDiffRuleJson("{}");
        return row;
    }

    private static final class InMemoryRepository implements OfficialOutboundFeeFactRepository {

        private final List<OutboundSizeClassificationRuleFact> classifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> slabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
            classifications.add(fact);
        }

        @Override
        public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
            slabs.add(fact);
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
            policies.add(fact);
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
            return false;
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
        }
    }
}

package com.nuono.next.logisticsquote;

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

class LogisticsQuoteFileManagementPublishIntegrationTest {

    @Test
    void shouldLandStructuredLogisticsFactsWhenFileManagementVersionPublishes() {
        FileManagementParseMapper mapper = mock(FileManagementParseMapper.class);
        FileParseResultItemViewAssembler assembler = new FileParseResultItemViewAssembler(new ObjectMapper());
        InMemoryFactRepository repository = new InMemoryFactRepository();
        FileParsePublishService service = new FileParsePublishService(
                mapper,
                assembler,
                new LogisticsQuoteFactPublisher(repository),
                null
        );

        FileParseTaskRow task = task();
        FileParseTargetPlanRow targetPlan = targetPlan();
        when(mapper.selectPublishAuditByIdempotency(20112L, "idem-logistics-facts")).thenReturn(null);
        when(mapper.selectActiveVersionForUpdate(4006L, "global", "global:*")).thenReturn(new FileParseActiveVersionRow());
        when(mapper.countBlockingResultItems(40104L)).thenReturn(0);
        when(mapper.countOpenHardValidationIssues(20112L)).thenReturn(0);
        when(mapper.selectResultItemsForPublish(40104L)).thenReturn(List.of(serviceLineItem(), priceItem()));
        when(mapper.nextVersionId()).thenReturn(70024L);
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
        when(mapper.nextVersionItemId()).thenReturn(88051L, 88052L);
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
        String expectedVersionNo = "LOGISTICS-ET-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-70024";
        when(mapper.updateActiveVersion(4006L, "global", "global:*", 70024L, expectedVersionNo, 10001L))
                .thenReturn(1);
        when(mapper.markTaskPublished(20112L, 40104L, 10001L)).thenReturn(1);
        when(mapper.nextAuditLogId()).thenReturn(90024L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40104L);
        command.setConfirmMessage("确认发布结构化物流版本");

        FileParsePublishView view = service.publish(
                task,
                targetPlan,
                List.of(itemStandard("logistics_service_line"), itemStandard("logistics_base_price")),
                command,
                "idem-logistics-facts",
                10001L
        );

        assertEquals(70024L, view.getVersionId());
        assertEquals(2, view.getLogisticsFactInsertedCount());
        assertEquals(0, view.getLogisticsFactSkippedCount());
        assertEquals(1, repository.serviceLines.size());
        assertEquals(1, repository.prices.size());
        assertEquals(88051L, repository.serviceLines.get(0).getSourceLineage().getSourceVersionItemId());
        assertEquals(88052L, repository.prices.get(0).getSourceLineage().getSourceVersionItemId());
    }

    private static FileParseTaskRow task() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setId(20112L);
        task.setTargetPlanId(4006L);
        task.setStandardVersionId(5105L);
        task.setCurrentResultId(40104L);
        task.setStatus("ready_to_publish");
        task.setDocumentTitle("ET物流报价-20260414入仓生效.pdf");
        return task;
    }

    private static FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow targetPlan = new FileParseTargetPlanRow();
        targetPlan.setId(4006L);
        targetPlan.setCode("logistics_et");
        targetPlan.setLabel("物流-易通");
        targetPlan.setStandardVersionId(5105L);
        return targetPlan;
    }

    private static FileParseResultItemRow serviceLineItem() {
        FileParseResultItemRow row = resultItem("logistics_service_line", "et|SA|FBN|air|headhaul|RUH", 1);
        row.setId(50101L);
        row.setEffectivePayloadJson("{\"forwarderCode\":\"et\",\"forwarderName\":\"ET/易通\",\"country\":\"SA\",\"fulfillmentMode\":\"FBN\",\"transportMode\":\"air\",\"serviceScope\":\"headhaul\",\"destinationNode\":\"RUH\"}");
        return row;
    }

    private static FileParseResultItemRow priceItem() {
        FileParseResultItemRow row = resultItem("logistics_base_price", "et|SA|FBN|air|headhaul|RUH|general|kg|unit_price", 2);
        row.setId(50102L);
        row.setEffectivePayloadJson("{\"forwarderCode\":\"et\",\"serviceLineKey\":\"et|SA|FBN|air|headhaul|RUH\",\"cargoCategoryKey\":\"et|SA|FBN|air|headhaul|RUH|general\",\"unitPrice\":\"64\",\"currency\":\"SAR\",\"billingUnit\":\"kg\",\"pricingModel\":\"unit_price\",\"priceStatus\":\"NORMAL\"}");
        return row;
    }

    private static FileParseResultItemRow resultItem(String itemType, String naturalKey, int sortNo) {
        FileParseResultItemRow row = new FileParseResultItemRow();
        row.setTaskId(20112L);
        row.setResultId(40104L);
        row.setTargetPlanId(4006L);
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

    private static final class InMemoryFactRepository implements LogisticsQuoteFactRepository {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsPriceRuleFact> prices = new ArrayList<>();

        @Override
        public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return serviceLines.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            serviceLines.add(fact);
        }

        @Override
        public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
            return serviceLines;
        }

        @Override
        public Optional<LogisticsPriceRuleFact> findPriceRuleBySourceVersionItemId(Long sourceVersionItemId) {
            return prices.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertPriceRule(LogisticsPriceRuleFact fact) {
            prices.add(fact);
        }
    }
}

package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FileParseLogisticsCoverageValidatorTest {

    private final FileParseLogisticsCoverageValidator validator = new FileParseLogisticsCoverageValidator();

    @Test
    void shouldFlagUnlinkedManualRelativeChangeAndMissingSaudiSeaSection() {
        FileParseTargetPlanRow targetPlan = new FileParseTargetPlanRow();
        targetPlan.setCode("logistics_et");
        targetPlan.setDocumentType("logistics_rule");

        FileParseStructuredItem uaeAir = item(
                FileParseLogisticsQuoteStandard.SERVICE_LINE,
                "{\"forwarderCode\":\"et\",\"country\":\"UAE\",\"transportMode\":\"air\",\"serviceScope\":\"warehouse_to_fbn\",\"serviceLineKey\":\"ET UAE air\"}",
                List.of(35001L)
        );

        List<FileParseLogisticsCoverageValidator.SourceRowEvidence> sourceRows = List.of(
                new FileParseLogisticsCoverageValidator.SourceRowEvidence(35001L, "pdf_text_line", "UAE air FBN delivery 5-7 days"),
                new FileParseLogisticsCoverageValidator.SourceRowEvidence(35002L, "manual_text_block", "人工补充：Saudi sea 基础价格整体上调 10%，需要新版报价确认")
        );

        List<FileParseLogisticsCoverageValidator.CoverageIssue> issues = validator.validate(
                targetPlan,
                List.of(uaeAir),
                sourceRows
        );

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(issue ->
                "manual_relative_change_unresolved".equals(issue.getCode())
                        && "hard_error".equals(issue.getSeverity())
                        && Long.valueOf(35002L).equals(issue.getSourceRowId())
        ));
        assertTrue(issues.stream().anyMatch(issue ->
                "logistics_section_missing".equals(issue.getCode())
                        && issue.getDetailsJson().contains("\"country\":\"KSA\"")
                        && issue.getDetailsJson().contains("\"transportMode\":\"sea\"")
        ));
    }

    private FileParseStructuredItem item(String itemType, String payloadJson, List<Long> sourceRowIds) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(itemType);
        item.setNormalizedPayloadJson(payloadJson);
        item.setSourceRowIds(sourceRowIds);
        item.setValidationStatus("pass");
        return item;
    }
}

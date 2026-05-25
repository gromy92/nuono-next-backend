package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FileParseOutboundFeeSourceScopePolicyTest {

    private final FileParseOutboundFeeSourceScopePolicy policy = new FileParseOutboundFeeSourceScopePolicy();

    @Test
    void shouldKeepOnlyTargetCurrencyRowsAndManualSupplementForUniversalWorkbook() {
        List<SourceRow> scoped = policy.outboundFeeRows(
                targetPlan("outbound_fee_ksa"),
                List.of(
                        row(1L, "excel_row", "Outbound", "UAE Standard Parcel 9.8 AED"),
                        row(2L, "excel_row", "Outbound", "KSA Standard Parcel 9.8 SAR"),
                        row(3L, "excel_row", "Outbound", "EGY Standard Parcel 7.5 EGP"),
                        row(4L, "excel_row", "Calculator", "The expected fees based on the details you provided above\tFBN Outbound Fee\tIF(D11<=25,INDEX(Outbound!$D:$D,MATCH(C50,Outbound!$A:$A,1)+1))\tSAR"),
                        row(5L, "manual_text_block", null, "KSA Bulky 35-40kg ASP>25 出仓费增加 4 SAR。")
                ),
                SourceRow::rawText,
                SourceRow::sourceType,
                SourceRow::sheetName
        );

        assertEquals(List.of(2L, 5L), ids(scoped));
    }

    @Test
    void shouldClipMixedPdfToOutboundSectionAndExcludeReferralFeeRows() {
        List<SourceRow> scoped = policy.outboundFeeRows(
                targetPlan("outbound_fee_ksa"),
                List.of(
                        row(1L, "pdf_text_line", null, "1. Referral Fees"),
                        row(2L, "pdf_text_line", null, "Fashion All 27% SAR"),
                        row(3L, "pdf_text_line", null, "2. FBN Outbound fees"),
                        row(4L, "pdf_text_line", null, "Small Envelope 7 SAR per unit shipped"),
                        row(5L, "pdf_text_line", null, "3. Monthly Storage Fees"),
                        row(6L, "pdf_text_line", null, "Storage 0.5 SAR per cbm")
                ),
                SourceRow::rawText,
                SourceRow::sourceType,
                SourceRow::sheetName
        );

        assertEquals(List.of(3L, 4L), ids(scoped));
    }

    @Test
    void shouldNotFallbackToReferralRowsWhenOutboundSectionIsMissing() {
        List<SourceRow> scoped = policy.outboundFeeRows(
                targetPlan("outbound_fee_ksa"),
                List.of(
                        row(1L, "pdf_text_line", null, "1. Referral Fees"),
                        row(2L, "pdf_text_line", null, "Fashion All 27% SAR")
                ),
                SourceRow::rawText,
                SourceRow::sourceType,
                SourceRow::sheetName
        );

        assertEquals(List.of(), ids(scoped));
    }

    @Test
    void shouldExcludeStorageRemovalFaqAndValueAddedRowsEvenWithoutExplicitSection() {
        List<SourceRow> scoped = policy.outboundFeeRows(
                targetPlan("outbound_fee_ksa"),
                List.of(
                        row(1L, "pdf_text_line", null, "Inventory Removal Fee 10 SAR per unit"),
                        row(2L, "pdf_text_line", null, "Monthly Storage Fees 0.5 SAR per cbm"),
                        row(3L, "pdf_text_line", null, "Value Added Services labeling 1 SAR"),
                        row(4L, "pdf_text_line", null, "FAQ Can I change my FBN outbound charges?"),
                        row(5L, "pdf_text_line", null, "FBN Outbound Fee Small Envelope 7 SAR per unit shipped")
                ),
                SourceRow::rawText,
                SourceRow::sourceType,
                SourceRow::sheetName
        );

        assertEquals(List.of(5L), ids(scoped));
    }

    private List<Long> ids(List<SourceRow> rows) {
        return rows.stream().map(SourceRow::id).collect(Collectors.toList());
    }

    private FileParseTargetPlanRow targetPlan(String code) {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setCode(code);
        row.setDocumentType("official_outbound_fee");
        return row;
    }

    private static SourceRow row(Long id, String sourceType, String sheetName, String rawText) {
        return new SourceRow(id, sourceType, sheetName, rawText);
    }

    private static class SourceRow {

        private final Long id;
        private final String sourceType;
        private final String sheetName;
        private final String rawText;

        private SourceRow(Long id, String sourceType, String sheetName, String rawText) {
            this.id = id;
            this.sourceType = sourceType;
            this.sheetName = sheetName;
            this.rawText = rawText;
        }

        private Long id() {
            return id;
        }

        private String sourceType() {
            return sourceType;
        }

        private String sheetName() {
            return sheetName;
        }

        private String rawText() {
            return rawText;
        }
    }
}

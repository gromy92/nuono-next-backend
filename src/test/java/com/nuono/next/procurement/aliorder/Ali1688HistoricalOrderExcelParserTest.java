package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderExcelParserTest {

    @Test
    void parsesSanitized49ColumnWorkbookWithContinuationAndLogistics() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.sanitizedWorkbook()),
                "sanitized-1688-order-export.xlsx"
        );

        assertThat(result.getHeaderValidation().isValid()).isTrue();
        assertThat(result.getHeaderValidation().getActualHeaderCount()).isEqualTo(49);
        assertThat(result.getSummary().getOrderHeaderRowCount()).isEqualTo(2);
        assertThat(result.getSummary().getProductLineCount()).isEqualTo(3);
        assertThat(result.getSummary().getLogisticsLineCount()).isEqualTo(2);
        assertThat(result.getSummary().getValidRowCount()).isEqualTo(3);
        assertThat(result.getSummary().getDuplicateCandidateCount()).isZero();
        assertThat(result.getRows()).hasSize(3);
        assertThat(result.getRows().get(1).isContinuationRow()).isTrue();
        assertThat(result.getRows().get(1).getOrderNo()).isEqualTo("ALI-SAFE-20260525-001");
        assertThat(result.getRows().get(1).getTitle()).isEqualTo("脱敏复古锁心本");
        assertThat(result.getRows().get(0).getSupplierName()).isEqualTo("义乌脱敏源头工厂");
        assertThat(result.getRows().get(0).getOrderTime()).isEqualTo("2026-05-25 10:30:00");
        assertThat(result.getRows().get(0).getLogisticsCompany()).isEqualTo("中通快递(ZTO)");
        assertThat(result.getRows().get(2).getDownstreamOrderNo()).isEqualTo("DOWNSTREAM-SAFE-002");
        assertThat(result.getRowErrors()).isEmpty();
    }

    @Test
    void parsesRowsWhenWorksheetDimensionIsFalseA1() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.workbookWithFalseWorksheetDimension()),
                "sanitized-1688-order-export.xlsx"
        );

        assertThat(result.getHeaderValidation().isValid()).isTrue();
        assertThat(result.getSummary().getProductLineCount()).isEqualTo(3);
        assertThat(result.getRows()).extracting(Ali1688HistoricalOrderExcelParseResult.Row::getOrderNo)
                .containsExactly(
                        "ALI-SAFE-20260525-001",
                        "ALI-SAFE-20260525-001",
                        "ALI-SAFE-20260525-002"
                );
    }

    @Test
    void rejectsDamagedWorkbookWithSafeTypedError() {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("not-xlsx".getBytes()), "bad.xlsx"))
                .isInstanceOf(Ali1688HistoricalOrderExcelImportException.class)
                .extracting("failureCode")
                .isEqualTo("damaged_workbook");
    }

    @Test
    void reportsHeaderMismatchWithoutSensitiveRowContent() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.workbookWithShiftedHeaders()),
                "wrong-template.xlsx"
        );

        assertThat(result.getHeaderValidation().isValid()).isFalse();
        assertThat(result.getHeaderValidation().getMismatchedHeaders()).isNotEmpty();
        assertThat(result.getHeaderValidation().getMessage())
                .contains("表头")
                .doesNotContain("脱敏地址");
    }

    @Test
    void reportsRowLevelFormatErrorsWithSafeCodes() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.workbookWithOrphanAndInvalidRows()),
                "invalid-rows.xlsx"
        );

        assertThat(result.getRowErrors()).extracting(Ali1688HistoricalOrderExcelParseResult.RowMessage::getCode)
                .contains(
                        "orphan_continuation_row",
                        "invalid_money",
                        "invalid_quantity",
                        "invalid_timestamp"
                );
        assertThat(result.getRowErrors()).extracting(Ali1688HistoricalOrderExcelParseResult.RowMessage::getFieldName)
                .contains("订单编号", "单价(元)", "数量", "订单创建时间", "订单付款时间");
        assertThat(result.getSummary().getValidRowCount()).isZero();
    }

    @Test
    void capsRowLevelErrorSamplesAndAggregatesExcessiveErrors() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.workbookWithManyInvalidRows(12)),
                "many-invalid-rows.xlsx"
        );

        assertThat(result.getRowErrors()).hasSizeLessThanOrEqualTo(20);
        assertThat(result.getRowWarnings()).extracting(Ali1688HistoricalOrderExcelParseResult.RowMessage::getCode)
                .contains("additional_errors_capped");
    }

    @Test
    void reportsNoImportableProductRows() throws Exception {
        Ali1688HistoricalOrderExcelParser parser = new Ali1688HistoricalOrderExcelParser();

        Ali1688HistoricalOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(Ali1688HistoricalOrderExcelFixtureSupport.workbookWithNoImportableProductRows()),
                "no-products.xlsx"
        );

        assertThat(result.getSummary().getProductLineCount()).isZero();
        assertThat(result.getRowErrors()).extracting(Ali1688HistoricalOrderExcelParseResult.RowMessage::getCode)
                .contains("no_importable_rows");
    }
}

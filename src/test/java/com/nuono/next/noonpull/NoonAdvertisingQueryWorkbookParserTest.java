package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingQueryReport;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class NoonAdvertisingQueryWorkbookParserTest {
    @Test
    void preservesDuplicateFactsAndSkipsOnlyInvalidBusinessRows() throws Exception {
        AdvertisingQueryReport report = new NoonAdvertisingQueryWorkbookParser().parse(
                workbook(),
                new AdvertisingCampaignRef("C-1", "Campaign")
        );

        assertEquals(7, report.getSourceItemCount());
        assertEquals(4, report.getBusinessSkippedItemCount());
        assertEquals(3, report.getFacts().size());
        assertEquals("same query", report.getFacts().get(0).getQueryText());
        assertEquals("same query", report.getFacts().get(1).getQueryText());
        assertEquals("count overflow", report.getFacts().get(2).getQueryText());
        assertEquals(2_147_483_648L, report.getFacts().get(2).getViews());
    }

    private byte[] workbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("(Product) Queries");
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "Campaign Name", "Sku", "Query", "Views", "Clicks", "Orders",
                    "Assisted Orders", "ATC", "Spends", "Revenue", "CTR", "ROAS",
                    "CPC", "CPS", "CVR"
            );
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            row(sheet.createRow(1), "same query", "10");
            row(sheet.createRow(2), "same query", "10");
            row(sheet.createRow(3), "bad metric", "not-a-number");
            row(sheet.createRow(4), "", "10");
            row(sheet.createRow(5), "x".repeat(1001), "10");
            row(sheet.createRow(6), "count overflow", "2147483648");
            row(sheet.createRow(7), "amount overflow", "10", "1000000000000");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void row(Row row, String query, String views) {
        row(row, query, views, "1.25");
    }

    private void row(Row row, String query, String views, String spend) {
        List<String> values = List.of(
                "Campaign", "ZSKU-1", query, views, "2", "0", "0", "1",
                spend, "3.5", "20", "2.8", "0.62", "0", "0"
        );
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}

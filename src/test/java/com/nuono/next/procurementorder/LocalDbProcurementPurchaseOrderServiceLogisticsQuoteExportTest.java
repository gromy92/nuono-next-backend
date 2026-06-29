package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

class LocalDbProcurementPurchaseOrderServiceLogisticsQuoteExportTest {

    @Test
    void reportLinesIncludeAlreadyConfirmedQuotesForSelectedRoute() {
        ForwarderRouteRecommendationRecord route = new ForwarderRouteRecommendationRecord();
        route.siteCode = "SA";
        route.transportMode = "SEA";

        PurchaseOrderLogisticsQuoteLineRecord pending = line("PENDING_QUOTE", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord confirmed = line("CONFIRMED", "SA", "SEA");
        PurchaseOrderLogisticsQuoteLineRecord otherRoute = line("PENDING_QUOTE", "AE", "SEA");

        List<PurchaseOrderLogisticsQuoteLineRecord> reportLines =
                LocalDbProcurementPurchaseOrderService.logisticsQuoteReportLines(
                        List.of(pending, confirmed, otherRoute),
                        route
                );

        assertEquals(List.of(pending, confirmed), reportLines);
    }

    @Test
    void yiteTemplateExportsHistoryQuoteColumn() throws Exception {
        LocalDbProcurementPurchaseOrderService service =
                new LocalDbProcurementPurchaseOrderService(null, null, null, new ObjectMapper());
        Method method = LocalDbProcurementPurchaseOrderService.class.getDeclaredMethod(
                "buildYiteLogisticsQuoteWorkbook",
                PurchaseOrderRecord.class,
                List.class
        );
        method.setAccessible(true);

        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.orderNo = "SO-TEST";
        PurchaseOrderLogisticsQuoteLineRecord line = line("CONFIRMED", "SA", "SEA");
        line.barcode = "PAPERSAYSB085";
        line.titleEn = "Sample English";
        line.titleCache = "样品";
        line.quantity = 10;
        line.forwarderCode = "YT";
        line.serviceName = "义特沙特海运";
        line.unitPrice = new BigDecimal("1390.0000");
        line.billingUnit = "CBM";

        byte[] bytes = (byte[]) method.invoke(service, order, List.of(line));

        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(22);
            Row data = sheet.getRow(23);
            int historyQuoteColumn = findHeaderColumn(header, "历史报价");

            assertEquals(15, historyQuoteColumn);
            assertEquals("1390/CBM", data.getCell(historyQuoteColumn).getStringCellValue());
        }
    }

    private int findHeaderColumn(Row header, String title) {
        for (int index = 0; index < header.getLastCellNum(); index++) {
            Cell cell = header.getCell(index);
            if (cell != null && title.equals(cell.getStringCellValue())) {
                return index;
            }
        }
        return -1;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(String quoteStatus, String siteCode, String transportMode) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.quoteStatus = quoteStatus;
        line.siteCode = siteCode;
        line.plannedTransportMode = transportMode;
        return line;
    }
}

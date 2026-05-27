package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonProductViewsSalesReportParserTest {

    @Test
    void parsesNoonSalesReportRowsAndComputesNetUnits() {
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-18,PAPERSAYSB359,SA,Z02AD5F198C0C2E813C30Z,Z02AD5F198C0C2E813C30Z-1,stationery,office,paper,papersay,SAR,\"Paper notebook, lined\",SA,120,450,10,8,3,128.50,64.20%,6.67%,16.06"
        );

        List<NoonProductViewsSalesReportRow> rows = new NoonProductViewsSalesReportParser().parse(csv);

        assertEquals(1, rows.size());
        NoonProductViewsSalesReportRow row = rows.get(0);
        assertEquals(LocalDate.of(2026, 5, 18), row.getVisitDate());
        assertEquals("PAPERSAYSB359", row.getPartnerSku());
        assertEquals("Z02AD5F198C0C2E813C30Z-1", row.getSku());
        assertEquals("Paper notebook, lined", row.getProductTitle());
        assertEquals("SA", row.getCountryCode());
        assertEquals(120, row.getYourVisitors());
        assertEquals(450, row.getTotalVisitors());
        assertEquals(10, row.getGrossUnits());
        assertEquals(8, row.getShippedUnits());
        assertEquals(3, row.getCancelledUnits());
        assertEquals(8, row.getNetUnits());
        assertEquals(new BigDecimal("128.50"), row.getRevenueShipped());
        assertEquals(new BigDecimal("64.20"), row.getBuyBoxVisitorPercentage());
        assertEquals(new BigDecimal("6.67"), row.getConversionVisitorsPercentage());
        assertEquals(new BigDecimal("16.06"), row.getAspShippedPercentage());
    }

    @Test
    void preservesBlankSourceMetricsWhileComputingNetUnits() {
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-04-30,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,,,1,1,,19.8,,,19.8"
        );

        NoonProductViewsSalesReportRow row = new NoonProductViewsSalesReportParser().parse(csv).get(0);

        assertNull(row.getYourVisitors());
        assertNull(row.getTotalVisitors());
        assertEquals(1, row.getGrossUnits());
        assertEquals(1, row.getShippedUnits());
        assertNull(row.getCancelledUnits());
        assertEquals(1, row.getNetUnits());
        assertEquals(new BigDecimal("19.8"), row.getRevenueShipped());
        assertNull(row.getBuyBoxVisitorPercentage());
        assertNull(row.getConversionVisitorsPercentage());
        assertEquals(new BigDecimal("19.8"), row.getAspShippedPercentage());
    }

    @Test
    void rejectsReportsMissingRequiredHeaders() {
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-18,PAPERSAYSB359,SA,Z02AD5F198C0C2E813C30Z,Z02AD5F198C0C2E813C30Z-1,stationery,office,paper,papersay,SAR,Paper notebook,SA,120,450,8,3,128.50,64.20%,6.67%,16.06"
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new NoonProductViewsSalesReportParser().parse(csv)
        );

        assertEquals(
                "Missing Noon product views and sales report columns: Gross_Units",
                error.getMessage()
        );
    }
}

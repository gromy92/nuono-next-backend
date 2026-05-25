package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class NoonSalesReportAdapterTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldTreatRecentEmptyProductViewsReportAsPendingConfirmation() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI);
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter((fact) -> {
        }, clock);

        NoonReportProcessResult result = adapter.process(file("Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n",
                LocalDate.of(2026, 5, 23)));

        assertEquals(NoonReportProcessResult.Code.EMPTY_REPORT_PENDING_CONFIRMATION, result.getCode());
    }

    @Test
    void shouldTreatOlderEmptyProductViewsReportAsTerminalEmptyReport() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI);
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter((fact) -> {
        }, clock);

        NoonReportProcessResult result = adapter.process(file("Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n",
                LocalDate.of(2026, 5, 20)));

        assertEquals(NoonReportProcessResult.Code.EMPTY_REPORT, result.getCode());
    }

    private NoonReportDownloadedFile file(String csv, LocalDate date) {
        return new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR245027-NAE")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.SALES)
                        .reportType("productviewsandsalesdata")
                        .dateFrom(date)
                        .dateTo(date)
                        .build(),
                "EXP-SALES",
                "batch-1",
                "digest-1",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }
}

package com.nuono.next.datapull.report;

import com.nuono.next.noonpull.NoonReportPullRequest;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts persisted report intent into the existing Noon provider request without inference. */
final class NoonReportIntentSupport {
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?:^|:)date-range:(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})$"
    );

    private NoonReportIntentSupport() {
    }

    static NoonReportWindow window(ExportReportIntent intent) {
        Matcher matcher = DATE_RANGE.matcher(
                Objects.requireNonNull(intent, "intent").getBusinessWindowKey()
        );
        if (!matcher.find()) {
            throw new IllegalArgumentException("REPORT_BUSINESS_WINDOW_INVALID");
        }
        return new NoonReportWindow(
                LocalDate.parse(matcher.group(1)),
                LocalDate.parse(matcher.group(2))
        );
    }

    static NoonReportPullRequest request(
            ExportReportIntent intent,
            NoonReportDefinition definition
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        NoonReportDefinition safeDefinition = Objects.requireNonNull(definition, "definition");
        if (safeIntent.getOperationCode() != safeDefinition.getOperationCode()
                || !safeIntent.getProviderChannel().equals(safeDefinition.getProviderChannel())) {
            throw new IllegalArgumentException("REPORT_DEFINITION_INTENT_MISMATCH");
        }
        NoonReportWindow window = window(safeIntent);
        String storeCode = ReportFactColumnContract.text(safeIntent.getStoreCode(), 80);
        String siteCode = ReportFactColumnContract.text(safeIntent.getSiteCode(), 20);
        if (storeCode.isEmpty() || siteCode.isEmpty()) {
            throw new IllegalArgumentException("REPORT_SCOPE_NOT_PERSISTABLE");
        }
        return NoonReportPullRequest.builder()
                .ownerUserId(safeIntent.getOwnerUserId())
                .storeCode(storeCode)
                .siteCode(siteCode)
                .dataDomain(safeDefinition.getDataDomain())
                .reportType(safeDefinition.getReportType())
                .dateFrom(window.getDateFrom())
                .dateTo(window.getDateTo())
                .build();
    }
}

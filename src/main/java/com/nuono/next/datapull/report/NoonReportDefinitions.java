package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noonpull.NoonFinanceTransactionReportDescriptor;
import com.nuono.next.noonpull.NoonPullDataDomain;
import org.springframework.util.StringUtils;

/** Single composition-time catalog for the remaining export-report operations. */
final class NoonReportDefinitions {
    private final String financeReportType;

    NoonReportDefinitions(String financeReportType) {
        this.financeReportType = StringUtils.hasText(financeReportType)
                ? financeReportType.trim()
                : NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE;
    }

    NoonReportDefinition dp01() {
        return definition(OperationCode.DP01, "NOON_REPORT_SALES", NoonPullDataDomain.SALES,
                "noon_catalog_reports_productviewsandsalesdata", null);
    }

    NoonReportDefinition dp03() {
        return definition(OperationCode.DP03, "NOON_REPORT_FINANCE",
                NoonPullDataDomain.FINANCE_TRANSACTION, financeReportType, null);
    }

    NoonReportDefinition dp07b() {
        return definition(OperationCode.DP07B, "NOON_FBN_REPORT",
                NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                FbnReceivedExportReportProvider.REPORT_TYPE, null);
    }

    private NoonReportDefinition definition(
            OperationCode operation,
            String channel,
            NoonPullDataDomain domain,
            String reportType,
            String handlePrefix
    ) {
        return new NoonReportDefinition(operation, channel, domain, reportType, handlePrefix);
    }
}

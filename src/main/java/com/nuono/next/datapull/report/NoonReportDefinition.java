package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noonpull.NoonPullDataDomain;
import java.util.Objects;

/** Immutable operation-to-provider contract for one Noon export report. */
public final class NoonReportDefinition {
    private final OperationCode operationCode;
    private final String providerChannel;
    private final NoonPullDataDomain dataDomain;
    private final String reportType;
    private final String syntheticHandlePrefix;

    public NoonReportDefinition(
            OperationCode operationCode,
            String providerChannel,
            NoonPullDataDomain dataDomain,
            String reportType,
            String syntheticHandlePrefix
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.providerChannel = ReportContract.requireIdentity(providerChannel, "providerChannel");
        this.dataDomain = Objects.requireNonNull(dataDomain, "dataDomain");
        this.reportType = ReportContract.requireIdentity(reportType, "reportType");
        this.syntheticHandlePrefix = ReportContract.optionalIdentity(
                syntheticHandlePrefix,
                "syntheticHandlePrefix"
        );
    }

    public OperationCode getOperationCode() { return operationCode; }
    public String getProviderChannel() { return providerChannel; }
    public NoonPullDataDomain getDataDomain() { return dataDomain; }
    public String getReportType() { return reportType; }
    public boolean hasSyntheticHandle() { return syntheticHandlePrefix != null; }

    public String syntheticHandle(NoonReportWindow window) {
        if (syntheticHandlePrefix == null) {
            throw new IllegalStateException("REPORT_SYNTHETIC_HANDLE_UNAVAILABLE");
        }
        return syntheticHandlePrefix + ":" + window.getDateFrom() + ".." + window.getDateTo();
    }
}

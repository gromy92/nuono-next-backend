package com.nuono.next.datapull.report;

/** One normalized source-row decision ready for durable report staging. */
public final class ReportPlannedRow {
    public enum Decision {
        ACCEPTED,
        BUSINESS_SKIP,
        CONTAINER_CONTRACT_ERROR
    }

    private final long rowNumber;
    private final Decision decision;
    private final String identitySha256;
    private final String payloadJson;

    private ReportPlannedRow(
            long rowNumber,
            Decision decision,
            String identitySha256,
            String payloadJson
    ) {
        this.rowNumber = rowNumber;
        this.decision = decision;
        this.identitySha256 = identitySha256;
        this.payloadJson = payloadJson;
    }

    public static ReportPlannedRow accepted(long rowNumber, String identity, String payloadJson) {
        return new ReportPlannedRow(
                rowNumber,
                Decision.ACCEPTED,
                ReportDigestSupport.sha256(identity),
                payloadJson
        );
    }

    public static ReportPlannedRow businessSkip(long rowNumber) {
        return new ReportPlannedRow(rowNumber, Decision.BUSINESS_SKIP, null, null);
    }

    public static ReportPlannedRow containerError(long rowNumber) {
        return new ReportPlannedRow(
                rowNumber,
                Decision.CONTAINER_CONTRACT_ERROR,
                null,
                null
        );
    }

    public long getRowNumber() { return rowNumber; }
    public Decision getDecision() { return decision; }
    public String getIdentitySha256() { return identitySha256; }
    public String getPayloadJson() { return payloadJson; }
}

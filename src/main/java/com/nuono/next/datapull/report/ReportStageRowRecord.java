package com.nuono.next.datapull.report;

/** Persistence record for one source-row decision in an immutable report stage. */
public class ReportStageRowRecord {
    private Long taskId;
    private Long rowNumber;
    private String decision;
    private String identitySha256;
    private String acceptedIdentitySha256;
    private String payloadJson;

    static ReportStageRowRecord from(long taskId, ReportPlannedRow row) {
        ReportStageRowRecord record = new ReportStageRowRecord();
        record.taskId = taskId;
        record.rowNumber = row.getRowNumber();
        record.decision = row.getDecision().name();
        record.identitySha256 = row.getIdentitySha256();
        record.acceptedIdentitySha256 = row.getDecision()
                == ReportPlannedRow.Decision.ACCEPTED
                ? row.getIdentitySha256()
                : null;
        record.payloadJson = row.getPayloadJson();
        return record;
    }

    void markLaterIdentityConflict() {
        if (!"ACCEPTED".equals(decision) || identitySha256 == null) {
            throw new IllegalStateException("only an accepted row can become an identity conflict");
        }
        decision = "LATER_IDENTITY_CONFLICT";
        acceptedIdentitySha256 = null;
        payloadJson = null;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getRowNumber() { return rowNumber; }
    public void setRowNumber(Long rowNumber) { this.rowNumber = rowNumber; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getIdentitySha256() { return identitySha256; }
    public void setIdentitySha256(String identitySha256) { this.identitySha256 = identitySha256; }
    public String getAcceptedIdentitySha256() { return acceptedIdentitySha256; }
    public void setAcceptedIdentitySha256(String acceptedIdentitySha256) { this.acceptedIdentitySha256 = acceptedIdentitySha256; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}

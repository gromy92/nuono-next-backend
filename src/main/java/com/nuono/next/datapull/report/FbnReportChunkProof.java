package com.nuono.next.datapull.report;

/** Target-side proof for one bounded DP-07-B set-based chunk. */
public class FbnReportChunkProof {
    private Long reportRows;
    private Long receiptRows;
    private Long warningRows;

    public Long getReportRows() { return reportRows; }
    public void setReportRows(Long reportRows) { this.reportRows = reportRows; }
    public Long getReceiptRows() { return receiptRows; }
    public void setReceiptRows(Long receiptRows) { this.receiptRows = receiptRows; }
    public Long getWarningRows() { return warningRows; }
    public void setWarningRows(Long warningRows) { this.warningRows = warningRows; }
}

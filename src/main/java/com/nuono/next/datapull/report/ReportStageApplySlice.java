package com.nuono.next.datapull.report;

/** Stable accepted-row boundary for one bounded set-based fact advance. */
public class ReportStageApplySlice {
    private Long rowCount;
    private Long lastRowNumber;

    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Long getLastRowNumber() { return lastRowNumber; }
    public void setLastRowNumber(Long lastRowNumber) { this.lastRowNumber = lastRowNumber; }
}

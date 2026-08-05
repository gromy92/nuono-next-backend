package com.nuono.next.datapull.report;

/** Internal signal whose surrounding short transaction must roll back as a contract failure. */
final class ReportApplyContractException extends RuntimeException {
    private final String code;

    ReportApplyContractException(String code) {
        super(code);
        this.code = code;
    }

    String getCode() { return code; }
}

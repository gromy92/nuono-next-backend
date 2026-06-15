package com.nuono.next.procurement.aliorder;

public enum Ali1688HistoricalOrderExcelImportFailureCode {
    UNSUPPORTED_FILE_TYPE("unsupported_file_type"),
    FILE_TOO_LARGE("file_too_large"),
    DAMAGED_WORKBOOK("damaged_workbook"),
    EMPTY_WORKBOOK("empty_workbook"),
    HEADER_MISMATCH("header_mismatch"),
    NO_IMPORTABLE_ROWS("no_importable_rows"),
    ORPHAN_CONTINUATION_ROW("orphan_continuation_row"),
    INVALID_MONEY("invalid_money"),
    INVALID_QUANTITY("invalid_quantity"),
    INVALID_TIMESTAMP("invalid_timestamp");

    private final String code;

    Ali1688HistoricalOrderExcelImportFailureCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

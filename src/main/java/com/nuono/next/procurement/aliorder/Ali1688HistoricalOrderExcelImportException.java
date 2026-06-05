package com.nuono.next.procurement.aliorder;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class Ali1688HistoricalOrderExcelImportException extends ResponseStatusException {

    private final String failureCode;

    public Ali1688HistoricalOrderExcelImportException(
            HttpStatus status,
            Ali1688HistoricalOrderExcelImportFailureCode failureCode,
            String reason
    ) {
        super(status, reason);
        this.failureCode = failureCode.getCode();
    }

    public String getFailureCode() {
        return failureCode;
    }
}

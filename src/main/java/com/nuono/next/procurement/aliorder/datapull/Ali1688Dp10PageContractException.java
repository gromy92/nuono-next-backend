package com.nuono.next.procurement.aliorder.datapull;

final class Ali1688Dp10PageContractException extends RuntimeException {

    private final String sanitizedCode;

    Ali1688Dp10PageContractException(String sanitizedCode) {
        super(sanitizedCode);
        this.sanitizedCode = sanitizedCode;
    }

    String getSanitizedCode() {
        return sanitizedCode;
    }
}

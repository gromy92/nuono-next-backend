package com.nuono.next.warehousedispatch;

final class WarehouseRequestConflictException extends IllegalStateException {

    WarehouseRequestConflictException(String message) {
        super(message);
    }
}

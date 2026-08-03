package com.nuono.next.warehousedispatch;

final class WarehouseInventoryStateConflictException extends IllegalStateException {

    WarehouseInventoryStateConflictException(String message) {
        super(message);
    }
}

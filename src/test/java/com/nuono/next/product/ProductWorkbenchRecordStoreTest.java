package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProductWorkbenchRecordStoreTest {

    @Test
    void storesRecordsByKeyAndIgnoresInvalidWrites() {
        ProductWorkbenchRecordStore store = new ProductWorkbenchRecordStore();
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();

        store.put(null, record);
        store.put("owner::store::sku", null);
        store.put("owner::store::sku", record);

        assertNull(store.get(null));
        assertSame(record, store.get("owner::store::sku"));
    }
}

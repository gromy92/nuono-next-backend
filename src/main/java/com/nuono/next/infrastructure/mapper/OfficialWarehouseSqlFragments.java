package com.nuono.next.infrastructure.mapper;

final class OfficialWarehouseSqlFragments {
    static final String BARCODE_SCOPE = ""
            + " OR EXISTS (SELECT 1 FROM product_barcode scopeBarcode"
            + " WHERE scopeBarcode.logical_store_id = ls.id AND scopeBarcode.is_deleted = b'0'"
            + " AND COALESCE(scopeBarcode.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'"
            + " AND scopeBarcode.barcode = line.sku"
            + " AND BINARY scopeBarcode.barcode = BINARY line.sku)";

    private OfficialWarehouseSqlFragments() {
    }
}

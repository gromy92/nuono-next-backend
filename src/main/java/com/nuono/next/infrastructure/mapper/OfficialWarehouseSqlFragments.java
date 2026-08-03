package com.nuono.next.infrastructure.mapper;

final class OfficialWarehouseSqlFragments {
    static final String BARCODE_SCOPE = ""
            + " OR EXISTS (SELECT 1 FROM product_barcode scopeBarcode"
            + " WHERE scopeBarcode.logical_store_id = ls.id AND scopeBarcode.is_deleted = b'0'"
            + " AND COALESCE(scopeBarcode.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'"
            + " AND scopeBarcode.barcode = line.sku"
            + " AND BINARY scopeBarcode.barcode = BINARY line.sku)";

    static final String OWNER_UNIQUE_BARCODE_IDENTITY = ""
            + " AND 1 = (SELECT COUNT(DISTINCT identityBarcode.logical_store_id, BINARY identityBarcode.partner_sku)"
            + " FROM product_barcode identityBarcode"
            + " JOIN product_master identityMaster ON identityMaster.id = identityBarcode.product_master_id"
            + " AND identityMaster.logical_store_id = identityBarcode.logical_store_id"
            + " AND identityMaster.partner_sku = identityBarcode.partner_sku"
            + " AND BINARY identityMaster.partner_sku = BINARY identityBarcode.partner_sku"
            + " AND identityMaster.is_deleted = b'0'"
            + " JOIN logical_store identityStore ON identityStore.id = identityBarcode.logical_store_id"
            + " AND identityStore.owner_user_id = b.owner_user_id AND identityStore.is_deleted = b'0'"
            + " WHERE identityBarcode.is_deleted = b'0'"
            + " AND COALESCE(identityBarcode.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'"
            + " AND identityBarcode.barcode = line.sku"
            + " AND BINARY identityBarcode.barcode = BINARY line.sku)";

    static final String UNIQUE_SITE_VARIANT = ""
            + " AND 1 = (SELECT COUNT(DISTINCT identityVariant.id)"
            + " FROM product_variant identityVariant"
            + " JOIN product_site_offer identityOffer ON identityOffer.variant_id = identityVariant.id"
            + " AND identityOffer.site_id = lss.id AND identityOffer.is_deleted = b'0'"
            + " WHERE identityVariant.product_master_id = pm.id"
            + " AND identityVariant.partner_sku = pb.partner_sku"
            + " AND BINARY identityVariant.partner_sku = BINARY pb.partner_sku"
            + " AND identityVariant.is_deleted = b'0')";

    private OfficialWarehouseSqlFragments() {
    }
}

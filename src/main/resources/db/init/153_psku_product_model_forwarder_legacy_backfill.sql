-- Backfill legacy product-level forwarder facts that predate shipping-order-line PSKU fields.
-- Legacy rows may have product_variant_id/product_master_id but no source_shipping_order_line_id,
-- so migration 152 could not derive logical_store_id + partner_sku from shipping order lines.

UPDATE `product_forwarder_declaration_attribute` pfda
LEFT JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = pfda.product_variant_id
LEFT JOIN `product_variant` canonical_pv
  ON canonical_pv.id = COALESCE(merge_map.canonical_variant_id, pfda.product_variant_id)
LEFT JOIN `product_master` canonical_pm
  ON canonical_pm.id = COALESCE(canonical_pv.product_master_id, pfda.product_master_id)
JOIN `logical_store` ls
  ON ls.id = COALESCE(canonical_pv.logical_store_id, canonical_pm.logical_store_id)
 AND ls.owner_user_id = pfda.owner_user_id
 AND ls.is_deleted = b'0'
SET pfda.logical_store_id = ls.id,
    pfda.partner_sku = COALESCE(
        NULLIF(TRIM(canonical_pv.partner_sku), ''),
        NULLIF(TRIM(canonical_pm.partner_sku), '')
    ),
    pfda.gmt_updated = NOW()
WHERE pfda.is_deleted = b'0'
  AND COALESCE(
      NULLIF(TRIM(canonical_pv.partner_sku), ''),
      NULLIF(TRIM(canonical_pm.partner_sku), '')
  ) IS NOT NULL
  AND (
      pfda.logical_store_id IS NULL
      OR pfda.logical_store_id <> ls.id
      OR NULLIF(TRIM(pfda.partner_sku), '') IS NULL
      OR TRIM(pfda.partner_sku) <> COALESCE(
          NULLIF(TRIM(canonical_pv.partner_sku), ''),
          NULLIF(TRIM(canonical_pm.partner_sku), '')
      )
  );

UPDATE `product_forwarder_channel_quote` pfcq
LEFT JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = pfcq.product_variant_id
LEFT JOIN `product_variant` canonical_pv
  ON canonical_pv.id = COALESCE(merge_map.canonical_variant_id, pfcq.product_variant_id)
LEFT JOIN `product_master` canonical_pm
  ON canonical_pm.id = COALESCE(canonical_pv.product_master_id, pfcq.product_master_id)
JOIN `logical_store` ls
  ON ls.id = COALESCE(canonical_pv.logical_store_id, canonical_pm.logical_store_id)
 AND ls.owner_user_id = pfcq.owner_user_id
 AND ls.is_deleted = b'0'
SET pfcq.logical_store_id = ls.id,
    pfcq.partner_sku = COALESCE(
        NULLIF(TRIM(canonical_pv.partner_sku), ''),
        NULLIF(TRIM(canonical_pm.partner_sku), '')
    ),
    pfcq.gmt_updated = NOW()
WHERE pfcq.is_deleted = b'0'
  AND COALESCE(
      NULLIF(TRIM(canonical_pv.partner_sku), ''),
      NULLIF(TRIM(canonical_pm.partner_sku), '')
  ) IS NOT NULL
  AND (
      pfcq.logical_store_id IS NULL
      OR pfcq.logical_store_id <> ls.id
      OR NULLIF(TRIM(pfcq.partner_sku), '') IS NULL
      OR TRIM(pfcq.partner_sku) <> COALESCE(
          NULLIF(TRIM(canonical_pv.partner_sku), ''),
          NULLIF(TRIM(canonical_pm.partner_sku), '')
      )
  );

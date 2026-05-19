-- Keep product_master's denormalized Group fields aligned with known product_group_member rows.
-- This only reconciles groups that already have a product_group projection; products without
-- a known group projection keep their existing initialization data.

UPDATE product_master pm
JOIN product_group_member pgm
  ON pgm.sku_parent = pm.sku_parent
 AND pgm.member_status = 'active'
 AND pgm.is_deleted = b'0'
JOIN product_group pg
  ON pg.id = pgm.product_group_id
 AND pg.logical_store_id = pm.logical_store_id
 AND pg.is_deleted = b'0'
JOIN (
    SELECT product_group_id, COUNT(1) AS member_count
    FROM product_group_member
    WHERE member_status = 'active'
      AND is_deleted = b'0'
    GROUP BY product_group_id
) pgc
  ON pgc.product_group_id = pg.id
SET pm.sku_group = pg.sku_group,
    pm.group_ref = COALESCE(NULLIF(pg.group_ref, ''), NULLIF(pg.group_ref_canonical, ''), pg.sku_group),
    pm.group_name_cache = COALESCE(NULLIF(pg.group_name, ''), NULLIF(pg.group_ref, ''), NULLIF(pg.group_ref_canonical, ''), pg.sku_group),
    pm.group_member_count = pgc.member_count,
    pm.gmt_updated = NOW()
WHERE pm.is_deleted = b'0';

UPDATE product_master pm
JOIN product_group pg
  ON pg.logical_store_id = pm.logical_store_id
 AND pg.is_deleted = b'0'
 AND (
      pm.sku_group = pg.sku_group
      OR pm.group_ref = pg.group_ref
      OR pm.group_ref = pg.group_ref_canonical
 )
LEFT JOIN product_group_member pgm
  ON pgm.product_group_id = pg.id
 AND pgm.sku_parent = pm.sku_parent
 AND pgm.member_status = 'active'
 AND pgm.is_deleted = b'0'
SET pm.sku_group = NULL,
    pm.group_ref = NULL,
    pm.group_name_cache = NULL,
    pm.group_member_count = NULL,
    pm.gmt_updated = NOW()
WHERE pm.is_deleted = b'0'
  AND pgm.id IS NULL;

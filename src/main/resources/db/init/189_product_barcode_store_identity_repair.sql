-- Pre-206 additive repair: align every mappable barcode row, including deleted
-- compatibility rows, to the logical store of its current variant.

UPDATE `product_barcode` pb
JOIN `product_variant` pv
  ON pv.id = pb.variant_id
SET pb.logical_store_id = pv.logical_store_id,
    pb.gmt_updated = NOW()
WHERE pv.logical_store_id IS NOT NULL
  AND (
    pb.logical_store_id IS NULL
    OR pb.logical_store_id <> pv.logical_store_id
  );

assert_migration_189_mappable() {
  local blockers
  blockers="$(db_scalar "
    SELECT COUNT(*)
    FROM product_barcode pb
    LEFT JOIN product_variant pv ON pv.id = pb.variant_id
    WHERE pv.id IS NULL
       OR pv.logical_store_id IS NULL;
  ")"
  emit MIGRATION_189_MAPPING_BLOCKERS "$blockers"
  [ "$blockers" = "0" ]
}

postcheck_migration_189() {
  local blockers
  blockers="$(db_scalar "
    SELECT COUNT(*)
    FROM product_barcode pb
    LEFT JOIN product_variant pv ON pv.id = pb.variant_id
    WHERE pv.id IS NULL
       OR pv.logical_store_id IS NULL
       OR pb.logical_store_id IS NULL
       OR pb.logical_store_id <> pv.logical_store_id;
  ")"
  emit MIGRATION_189_FULL_ROW_BLOCKERS "$blockers"
  [ "$blockers" = "0" ]
}

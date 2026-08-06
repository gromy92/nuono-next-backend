migration_182_state() {
  db_scalar "
    SELECT CASE
      WHEN (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND (
            (column_name = 'product_master_id'
              AND column_type = 'bigint' AND is_nullable = 'YES'
              AND column_default IS NULL)
            OR (column_name = 'logical_store_id'
              AND column_type = 'bigint' AND is_nullable = 'YES'
              AND column_default IS NULL)
            OR (column_name = 'partner_sku'
              AND column_type = 'varchar(100)' AND is_nullable = 'YES'
              AND column_default IS NULL)
          )
      ) = 3
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_master'
          AND non_unique = 1
          AND index_type = 'BTREE'
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'product_master_id')
            OR (seq_in_index = 2 AND column_name = 'is_deleted'))
      ) = 2
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_master'
      ) = 2
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_store_psku'
          AND non_unique = 1
          AND index_type = 'BTREE'
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'logical_store_id')
            OR (seq_in_index = 2 AND column_name = 'partner_sku')
            OR (seq_in_index = 3 AND column_name = 'is_deleted'))
      ) = 3
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_store_psku'
      ) = 3
      THEN 'READY'
      WHEN (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND (
            (column_name = 'product_master_id'
              AND column_type = 'bigint' AND is_nullable = 'YES'
              AND column_default IS NULL)
            OR (column_name = 'logical_store_id'
              AND column_type = 'bigint' AND is_nullable = 'NO'
              AND column_default IS NULL)
            OR (column_name = 'partner_sku'
              AND column_type = 'varchar(100)' AND is_nullable = 'YES'
              AND column_default IS NULL)
          )
      ) = 3
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_master'
          AND non_unique = 1
          AND index_type = 'BTREE'
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'product_master_id')
            OR (seq_in_index = 2 AND column_name = 'is_deleted'))
      ) = 2
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_master'
      ) = 2
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_store_psku'
          AND non_unique = 1
          AND index_type = 'BTREE'
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'logical_store_id')
            OR (seq_in_index = 2 AND column_name = 'partner_sku')
            OR (seq_in_index = 3 AND column_name = 'is_deleted'))
      ) = 3
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'idx_product_barcode_store_psku'
      ) = 3
      AND NOT EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'uk_product_barcode_barcode'
      )
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'uk_product_barcode_store_barcode'
          AND non_unique = 0
          AND index_type = 'BTREE'
          AND sub_part IS NULL
          AND ((seq_in_index = 1 AND column_name = 'logical_store_id')
            OR (seq_in_index = 2 AND column_name = 'barcode'))
      ) = 2
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'uk_product_barcode_store_barcode'
      ) = 2
      AND NOT EXISTS(
        SELECT 1 FROM product_barcode WHERE logical_store_id IS NULL
      )
      THEN 'READY_SUCCESSOR_206'
      WHEN (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND column_name IN (
            'product_master_id', 'logical_store_id', 'partner_sku'
          )
      ) = 0
      AND (
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name IN (
            'idx_product_barcode_master',
            'idx_product_barcode_store_psku'
          )
      ) = 0
      THEN 'EXACT_LEGACY'
      ELSE 'PARTIAL_UNSAFE'
    END;
  "
}

assert_migration_182_mappable() {
  local blockers
  blockers="$(db_scalar "
    SELECT COUNT(*)
    FROM product_barcode pb
    LEFT JOIN product_variant pv ON pv.id = pb.variant_id
    LEFT JOIN product_master pm ON pm.id = pv.product_master_id
    WHERE pv.id IS NULL
       OR pm.id IS NULL
       OR pv.logical_store_id IS NULL
       OR pm.logical_store_id IS NULL
       OR pv.logical_store_id <> pm.logical_store_id;
  ")"
  emit MIGRATION_182_RELATION_BLOCKERS "$blockers"
  [ "$blockers" = 0 ]
}

postcheck_migration_182() {
  local state
  state="$(migration_182_state)"
  [ "$state" = READY ] || [ "$state" = READY_SUCCESSOR_206 ] || return 1
  local blockers
  blockers="$(db_scalar "
    SELECT COUNT(*)
    FROM product_barcode pb
    JOIN product_variant pv ON pv.id = pb.variant_id
    JOIN product_master pm ON pm.id = pv.product_master_id
    WHERE pb.is_deleted = b'0'
      AND pv.is_deleted = b'0'
      AND pm.is_deleted = b'0'
      AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
      AND (
        pb.product_master_id IS NULL
        OR pb.product_master_id <> pm.id
        OR pb.logical_store_id IS NULL
        OR pb.logical_store_id <> pm.logical_store_id
        OR NULLIF(TRIM(pb.partner_sku), '') IS NULL
        OR TRIM(pb.partner_sku) <> TRIM(pv.partner_sku)
      );
  ")"
  emit MIGRATION_182_DATA_BLOCKERS "$blockers"
  [ "$blockers" = 0 ]
}

ensure_migration_182() {
  local state
  state="$(migration_182_state)"
  emit MIGRATION_182_PRE_STATE "$state"
  case "$state" in
    READY)
      if postcheck_migration_182; then
        emit MIGRATION_182_ACTION SKIPPED_READY
        return 0
      fi
      assert_migration_182_mappable
      apply_migration "$MIGRATION_182"
      postcheck_migration_182
      emit MIGRATION_182_ACTION APPLIED_DATA_REPAIR
      ;;
    READY_SUCCESSOR_206)
      if postcheck_migration_182; then
        emit MIGRATION_182_ACTION SKIPPED_READY_SUCCESSOR_206
        return 0
      fi
      emit MIGRATION_182_ACTION BLOCKED_SUCCESSOR_DATA_DRIFT
      return 1
      ;;
    EXACT_LEGACY)
      assert_migration_182_mappable
      apply_migration "$MIGRATION_182"
      postcheck_migration_182
      emit MIGRATION_182_ACTION APPLIED_FROM_EXACT_LEGACY
      ;;
    *)
      emit MIGRATION_182_ACTION BLOCKED_PARTIAL_UNSAFE
      return 1
      ;;
  esac
}

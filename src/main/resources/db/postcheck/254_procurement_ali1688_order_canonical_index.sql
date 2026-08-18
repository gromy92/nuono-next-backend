SELECT IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_ali1688_order_header'
      AND column_name = 'superseded_by_order_id'
      AND column_type = 'bigint'
      AND is_nullable = 'YES'
  )
  AND EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_ali1688_order_dedup_audit'
  )
  AND (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_ali1688_order_dedup_audit'
      AND index_name = 'PRIMARY'
  ) = 'correction_code,entity_type,entity_id'
  AND
  (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_ali1688_order_header'
      AND index_name = 'idx_proc_ali1688_order_canonical'
  ) = 'owner_user_id,provider_order_no,superseded_by_order_id,is_deleted,authorization_id,gmt_updated,id',
  1,
  0
) AS result;

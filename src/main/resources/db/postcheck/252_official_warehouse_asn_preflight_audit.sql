SELECT IF(
  (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_preflight_audit'
      AND COLUMN_NAME IN (
        'id', 'owner_user_id', 'store_code', 'site_code', 'attempt_asn_id',
        'failure_code', 'invalid_lines_json', 'gmt_create'
      )
  ) = 8,
  1,
  0
) AS official_warehouse_asn_preflight_audit_ready;

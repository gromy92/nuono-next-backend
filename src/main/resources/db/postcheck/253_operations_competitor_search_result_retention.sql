SELECT IF(
  (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_search_run'
      AND index_name = 'idx_ops_comp_search_run_retention'
  ) = 'status,finished_at,id',
  1,
  0
) AS result;

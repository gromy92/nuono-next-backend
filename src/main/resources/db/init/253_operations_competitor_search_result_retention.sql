-- Bounded 15-day retention depends on this terminal-run lookup index.
SET NAMES utf8mb4;

SET @ops_comp_retention_index_shape := (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'operations_competitor_search_run'
    AND index_name = 'idx_ops_comp_search_run_retention'
);

SET @ops_comp_retention_index_sql := IF(
  @ops_comp_retention_index_shape IS NULL,
  'ALTER TABLE `operations_competitor_search_run` ADD INDEX `idx_ops_comp_search_run_retention` (`status`, `finished_at`, `id`), ALGORITHM=INPLACE, LOCK=NONE',
  IF(
    @ops_comp_retention_index_shape = 'status,finished_at,id',
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''OPS_COMP_SEARCH_RESULT_RETENTION_INDEX_DRIFT'''
  )
);
PREPARE ops_comp_retention_index_stmt FROM @ops_comp_retention_index_sql;
EXECUTE ops_comp_retention_index_stmt;
DEALLOCATE PREPARE ops_comp_retention_index_stmt;

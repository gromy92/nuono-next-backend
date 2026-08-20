SELECT IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_auth_recovery_checkpoint'
      AND engine = 'InnoDB'
  )
  AND (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_auth_recovery_checkpoint'
      AND index_name = 'PRIMARY'
  ) = 'recovery_id'
  AND EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_auth_recovery_checkpoint'
      AND column_name = 'ciphertext'
      AND data_type = 'mediumblob'
  )
  AND EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_auth_recovery_checkpoint'
      AND column_name = 'initialization_vector'
      AND column_type = 'varbinary(12)'
  ),
  1,
  0
) AS result;


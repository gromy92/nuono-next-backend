assert_database_idle() {
  local information_schema_blockers
  information_schema_blockers="$(db_scalar "
    SELECT COUNT(*) FROM information_schema.processlist
    WHERE id <> CONNECTION_ID()
      AND db = DATABASE()
      AND command <> 'Sleep';
  ")"
  emit INFORMATION_SCHEMA_IDLE_BLOCKERS "$information_schema_blockers"
  [ "$information_schema_blockers" = 0 ]

  local innodb_transaction_blockers
  if innodb_transaction_blockers="$(db_scalar "
      SELECT COUNT(*) FROM information_schema.innodb_trx
      WHERE trx_mysql_thread_id <> CONNECTION_ID();
    " 2>/dev/null)"; then
    emit INNODB_TRANSACTION_EVIDENCE AVAILABLE
    emit INNODB_TRANSACTION_BLOCKERS "$innodb_transaction_blockers"
    [ "$innodb_transaction_blockers" = 0 ]
  else
    emit INNODB_TRANSACTION_EVIDENCE UNAVAILABLE_EXPLICIT_LOCK_REQUIRED
  fi

  local performance_schema_blockers
  if performance_schema_blockers="$(db_scalar "
      SELECT
        (SELECT COUNT(*) FROM performance_schema.metadata_locks
         WHERE lock_status = 'PENDING')
        + (SELECT COUNT(*) FROM performance_schema.data_lock_waits);
    " 2>/dev/null)"; then
    emit PERFORMANCE_SCHEMA_LOCK_EVIDENCE AVAILABLE
    emit PERFORMANCE_SCHEMA_LOCK_BLOCKERS "$performance_schema_blockers"
    [ "$performance_schema_blockers" = 0 ]
  else
    emit PERFORMANCE_SCHEMA_LOCK_EVIDENCE UNAVAILABLE_EXPLICIT_LOCK_REQUIRED
  fi

  local lock_probe
  if ! lock_probe="$(
    mysql \
      --defaults-extra-file="$MYSQL_CNF" \
      --batch \
      --skip-column-names \
      --raw 2>/dev/null <<'SQL'
SET SESSION lock_wait_timeout = 5;
SET SESSION innodb_lock_wait_timeout = 5;
SET autocommit = 0;
LOCK TABLES product_barcode WRITE, product_variant READ;
SELECT IF(
  @@innodb_table_locks = 1,
  'ACQUIRED',
  'INNODB_TABLE_LOCKS_DISABLED'
);
COMMIT;
UNLOCK TABLES;
SQL
  )"; then
    emit DATABASE_TABLE_LOCK_PROBE FAILED
    return 1
  fi
  [ "$lock_probe" = "ACQUIRED" ] || {
    emit DATABASE_TABLE_LOCK_PROBE INVALID_EVIDENCE
    return 1
  }
  emit DATABASE_TABLE_LOCK_PROBE ACQUIRED_AND_RELEASED
  emit DATABASE_LOCK_BLOCKERS 0
}

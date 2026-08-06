# Migration 238 permanently retires the table created by migration 205. The
# additive compatibility tail must therefore prove the successor state before
# deciding whether migration 205 remains applicable.

migration_238_state() {
  db_scalar "
    SELECT CASE
      WHEN (
        SELECT COUNT(*)
        FROM nuono_schema_migration
        WHERE migration_key = '238_noon_auth_business_wait_queue.sql'
      ) = 0 THEN 'ABSENT'
      WHEN (
        SELECT COUNT(*)
        FROM nuono_schema_migration h
        JOIN nuono_schema_migration_attempt a
          ON a.migration_key = h.migration_key
         AND a.attempt_no = h.attempt_no
        WHERE h.migration_key = '238_noon_auth_business_wait_queue.sql'
          AND h.state = 'APPLIED'
          AND a.state = 'APPLIED'
          AND h.checksum_sha256 = a.checksum_sha256
          AND h.postcheck_sha256 = a.postcheck_sha256
      ) = 1 THEN 'APPLIED'
      ELSE 'BLOCKED'
    END;
  "
}

migration_205_relation_count() {
  db_scalar "
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'product_listing_reauthentication_attempt';
  "
}

migration_205_exact_shape() {
  db_scalar "
    SELECT IF(
      (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
          AND table_collation = 'utf8mb4_unicode_ci'
      ) = 1
      AND (
        SELECT GROUP_CONCAT(
          CONCAT_WS(':',
            ordinal_position,
            column_name,
            LOWER(column_type),
            LOWER(COALESCE(collation_name, '-')),
            UPPER(is_nullable),
            UPPER(COALESCE(column_default, '<NULL>')),
            LOWER(COALESCE(extra, ''))
          ) ORDER BY ordinal_position SEPARATOR '|'
        )
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
      ) = CONCAT(
        '1:real_run_task_id:bigint:-:NO:<NULL>:|',
        '2:owner_user_id:bigint:-:NO:<NULL>:|',
        '3:draft_id:bigint:-:NO:<NULL>:|',
        '4:project_id:bigint:-:NO:<NULL>:|',
        '5:project_code:varchar(100):utf8mb4_bin:NO:<NULL>:|',
        '6:store_code:varchar(100):utf8mb4_unicode_ci:NO:<NULL>:|',
        '7:recovery_id:bigint:-:NO:<NULL>:|',
        '8:recovery_item_id:bigint:-:NO:<NULL>:|',
        '9:requested_auth_version:bigint:-:NO:<NULL>:|',
        '10:resume_action:varchar(40):utf8mb4_unicode_ci:NO:<NULL>:|',
        '11:status:varchar(32):utf8mb4_unicode_ci:NO:PENDING:|',
        '12:version_no:bigint:-:NO:0:|',
        '13:failure_code:varchar(80):utf8mb4_unicode_ci:YES:<NULL>:|',
        '14:requested_at:datetime:-:NO:<NULL>:|',
        '15:completed_at:datetime:-:YES:<NULL>:|',
        '16:gmt_create:datetime:-:NO:CURRENT_TIMESTAMP:default_generated|',
        '17:gmt_updated:datetime:-:NO:CURRENT_TIMESTAMP:',
        'default_generated on update current_timestamp'
      )
      AND (
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
      ) = 8
      AND (
        SELECT GROUP_CONCAT(
          CONCAT_WS(':',
            index_name,
            non_unique,
            seq_in_index,
            column_name,
            COALESCE(sub_part, '-'),
            collation
          ) ORDER BY BINARY index_name, seq_in_index SEPARATOR '|'
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
      ) = CONCAT(
        'PRIMARY:0:1:real_run_task_id:-:A|',
        'idx_listing_reauth_item:1:1:recovery_item_id:-:A|',
        'idx_listing_reauth_owner_status:1:1:owner_user_id:-:A|',
        'idx_listing_reauth_owner_status:1:2:status:-:A|',
        'idx_listing_reauth_owner_status:1:3:gmt_updated:-:A|',
        'idx_listing_reauth_recovery:1:1:recovery_id:-:A|',
        'idx_listing_reauth_recovery:1:2:owner_user_id:-:A|',
        'idx_listing_reauth_recovery:1:3:project_code:-:A'
      )
      AND (
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
          AND UPPER(index_type) = 'BTREE'
          AND sub_part IS NULL
          AND collation = 'A'
          AND is_visible = 'YES'
          AND expression IS NULL
      ) = 8,
      1,
      0
    );
  "
}

migration_205_row_count() {
  db_scalar "SELECT COUNT(*) FROM product_listing_reauthentication_attempt;"
}

drop_migration_205_table() {
  mysql \
    "${MYSQL_LOGIN_PATH_ARGS[@]}" \
    --skip-reconnect \
    --protocol=TCP \
    --host="$EXPECTED_DB_HOST" \
    --port="$EXPECTED_DB_PORT" \
    --database="$EXPECTED_SCHEMA" \
    --execute="DROP TABLE product_listing_reauthentication_attempt"
}

require_safe_migration_205_relation() {
  local relation_count="$1"
  local shape
  local row_count
  if [ "$relation_count" != 1 ]; then
    emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_RELATION_DRIFT
    return 1
  fi
  shape="$(migration_205_exact_shape)"
  if [ "$shape" != 1 ]; then
    emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_SHAPE_DRIFT
    return 1
  fi
  row_count="$(migration_205_row_count)"
  if ! [[ "$row_count" =~ ^[0-9]+$ ]] || [ "$row_count" != 0 ]; then
    emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_NON_EMPTY
    emit MIGRATION_205_OBSOLETE_ROW_COUNT "$row_count"
    return 1
  fi
  emit MIGRATION_205_OBSOLETE_ROW_COUNT 0
}

repair_retired_migration_205() {
  local successor_state
  local relation_count
  successor_state="$(migration_238_state)"
  relation_count="$(migration_205_relation_count)"
  if ! [[ "$relation_count" =~ ^[0-9]+$ ]]; then
    emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_RELATION_DRIFT
    return 1
  fi
  case "$successor_state" in
    APPLIED)
      if [ "$relation_count" = 0 ]; then
        emit MIGRATION_205_SUCCESSOR_REPAIR_STATE SKIPPED_SUCCESSOR_238_READY
        return 0
      fi
      require_safe_migration_205_relation "$relation_count"
      drop_migration_205_table
      if [ "$(migration_205_relation_count)" != 0 ]; then
        emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_DROP_NOT_EFFECTIVE
        return 1
      fi
      emit MIGRATION_205_SUCCESSOR_REPAIR_STATE DROPPED_EMPTY_EXACT_205_SUCCESSOR_238
      ;;
    ABSENT)
      if [ "$relation_count" = 0 ]; then
        emit MIGRATION_205_SUCCESSOR_REPAIR_STATE READY_SUCCESSOR_238_PENDING
        return 0
      fi
      require_safe_migration_205_relation "$relation_count"
      emit MIGRATION_205_SUCCESSOR_REPAIR_STATE READY_EMPTY_EXACT_205_SUCCESSOR_238_PENDING
      ;;
    *)
      emit MIGRATION_205_SUCCESSOR_REPAIR_STATE BLOCKED_SUCCESSOR_238_HISTORY
      return 1
      ;;
  esac
}

apply_or_skip_migration_205() {
  local successor_state
  successor_state="$(migration_238_state)"
  case "$successor_state" in
    APPLIED)
      if [ "$(migration_205_relation_count)" != 0 ]; then
        emit MIGRATION_205_RESULT BLOCKED_SUCCESSOR_238_RELATION_PRESENT
        return 1
      fi
      emit MIGRATION_205_RESULT SKIPPED_RETIRED_SUCCESSOR_238
      ;;
    ABSENT)
      apply_migration "$MIGRATION_205"
      postcheck_migration_205
      emit MIGRATION_205_RESULT APPLIED_LEGACY_SUCCESSOR_238_ABSENT
      ;;
    *)
      emit MIGRATION_205_RESULT BLOCKED_SUCCESSOR_238_HISTORY
      return 1
      ;;
  esac
}

-- Migration 248: DP08 immutable member sets, task progress, and bounded retention.
SET NAMES utf8mb4;
-- Requires migration 247 schedule core to be applied and postchecked first.
-- Owns DP08 member staging, immutable member sets, per-task progress and bounded retention guards.
CREATE TABLE IF NOT EXISTS dp_pull_schedule_dp08_member_stage_head (
    operation_code VARCHAR(16) NOT NULL,
    epoch_no BIGINT NOT NULL,
    scan_pass TINYINT UNSIGNED NOT NULL,
    scope_key VARCHAR(96) NOT NULL,
    source_cursor VARCHAR(512) NOT NULL,
    member_count BIGINT NOT NULL,
    member_ordered_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_payload TEXT NOT NULL,
    effective_from_utc DATETIME(3) NOT NULL,
    stage_state VARCHAR(16) NOT NULL,
    member_set_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, epoch_no, scan_pass, scope_key),
    KEY idx_dp08_member_stage_set (member_set_id),
    CONSTRAINT chk_dp08_member_stage_head_identity CHECK (
        operation_code IN ('DP08A','DP08B') AND scan_pass IN (1,2)
        AND member_count > 0 AND version_no >= 0
        AND CHAR_LENGTH(TRIM(scope_key)) > 0
        AND CHAR_LENGTH(TRIM(source_cursor)) > 0
        AND OCTET_LENGTH(base_payload) BETWEEN 1 AND 4096
        AND member_ordered_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_dp08_member_stage_head_state CHECK (
        (stage_state = 'SCANNING' AND member_set_id IS NULL)
        OR
        (stage_state IN ('FINALIZING','EMITTED')
         AND member_set_id IS NOT NULL
         AND member_set_id REGEXP '^[0-9a-f]{64}$')
    ),
    CONSTRAINT fk_dp08_member_stage_head_epoch
        FOREIGN KEY (operation_code, epoch_no)
        REFERENCES dp_pull_schedule_source_epoch (operation_code, epoch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_schedule_dp08_member_stage_item (
    operation_code VARCHAR(16) NOT NULL,
    epoch_no BIGINT NOT NULL,
    scan_pass TINYINT UNSIGNED NOT NULL,
    scope_key VARCHAR(96) NOT NULL,
    member_key VARCHAR(64) NOT NULL,
    member_kind VARCHAR(16) NOT NULL,
    watch_product_id BIGINT NOT NULL,
    competitor_product_id BIGINT NULL,
    noon_product_code VARCHAR(64) NOT NULL,
    source_updated_at_utc DATETIME(3) NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, epoch_no, scan_pass, scope_key, member_key),
    CONSTRAINT chk_dp08_member_stage_item_identity CHECK (
        operation_code IN ('DP08A','DP08B') AND scan_pass IN (1,2)
        AND watch_product_id > 0 AND CHAR_LENGTH(TRIM(member_key)) > 0
        AND CHAR_LENGTH(TRIM(noon_product_code)) > 0
        AND ((member_kind = 'SELF' AND competitor_product_id IS NULL)
             OR (member_kind = 'COMPETITOR' AND competitor_product_id > 0))
    ),
    CONSTRAINT fk_dp08_member_stage_item_head
        FOREIGN KEY (operation_code, epoch_no, scan_pass, scope_key)
        REFERENCES dp_pull_schedule_dp08_member_stage_head (
            operation_code, epoch_no, scan_pass, scope_key
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_dp08_member_set (
    member_set_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_code VARCHAR(16) NOT NULL,
    scope_key VARCHAR(96) NOT NULL,
    member_count BIGINT NOT NULL,
    member_ordered_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    handle_payload_type VARCHAR(64) NOT NULL,
    handle_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    handle_payload TEXT NOT NULL,
    effective_from_utc DATETIME(3) NOT NULL,
    set_state VARCHAR(16) NOT NULL,
    copy_cursor VARCHAR(64) NULL,
    copied_member_count BIGINT NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (member_set_id),
    KEY idx_dp08_member_set_scope (operation_code, scope_key, effective_from_utc),
    CONSTRAINT chk_dp08_member_set_identity CHECK (
        member_set_id REGEXP '^[0-9a-f]{64}$'
        AND operation_code IN ('DP08A','DP08B')
        AND member_count > 0 AND copied_member_count BETWEEN 0 AND member_count
        AND version_no >= 0 AND CHAR_LENGTH(TRIM(scope_key)) > 0
        AND member_ordered_sha256 REGEXP '^[0-9a-f]{64}$'
        AND handle_payload_sha256 REGEXP '^[0-9a-f]{64}$'
        AND OCTET_LENGTH(handle_payload) BETWEEN 1 AND 4096
    ),
    CONSTRAINT chk_dp08_member_set_state CHECK (
        (set_state = 'BUILDING' AND copied_member_count < member_count)
        OR
        (set_state = 'SEALED' AND copied_member_count = member_count
         AND copy_cursor IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_dp08_member_set_item (
    member_set_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_key VARCHAR(64) NOT NULL,
    member_kind VARCHAR(16) NOT NULL,
    watch_product_id BIGINT NOT NULL,
    competitor_product_id BIGINT NULL,
    noon_product_code VARCHAR(64) NOT NULL,
    source_updated_at_utc DATETIME(3) NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (member_set_id, member_key),
    CONSTRAINT chk_dp08_member_set_item_identity CHECK (
        watch_product_id > 0 AND CHAR_LENGTH(TRIM(member_key)) > 0
        AND CHAR_LENGTH(TRIM(noon_product_code)) > 0
        AND ((member_kind = 'SELF' AND competitor_product_id IS NULL)
             OR (member_kind = 'COMPETITOR' AND competitor_product_id > 0))
    ),
    CONSTRAINT fk_dp08_member_set_item_set
        FOREIGN KEY (member_set_id) REFERENCES dp_pull_dp08_member_set (member_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_dp08_task_member_progress (
    task_id BIGINT NOT NULL,
    operation_code VARCHAR(16) NOT NULL,
    member_set_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_cursor VARCHAR(64) NULL,
    evidence_member_count BIGINT NOT NULL DEFAULT 0,
    evidence_complete BIT(1) NOT NULL DEFAULT b'0',
    exact_search_required BIT(1) NOT NULL DEFAULT b'0',
    apply_cursor VARCHAR(64) NULL,
    applied_member_count BIGINT NOT NULL DEFAULT 0,
    apply_complete BIT(1) NOT NULL DEFAULT b'0',
    search_run_id BIGINT NULL,
    keyword_run_id BIGINT NULL,
    rank_fact_count INT NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    KEY idx_dp08_task_member_set (member_set_id, task_id),
    CONSTRAINT chk_dp08_task_member_progress_identity CHECK (
        operation_code IN ('DP08A','DP08B')
        AND member_set_id REGEXP '^[0-9a-f]{64}$'
        AND evidence_member_count >= 0 AND applied_member_count >= 0
        AND rank_fact_count >= 0 AND version_no >= 0
    ),
    CONSTRAINT chk_dp08_task_member_progress_operation CHECK (
        (operation_code = 'DP08A' AND evidence_complete = b'1'
         AND exact_search_required = b'0')
        OR operation_code = 'DP08B'
    ),
    CONSTRAINT fk_dp08_task_member_progress_task
        FOREIGN KEY (task_id) REFERENCES dp_pull_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp08_task_member_progress_set
        FOREIGN KEY (member_set_id) REFERENCES dp_pull_dp08_member_set (member_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Each CHECK is independently repairable after a statement-boundary interruption.
SET @dp08_scope_handle_check := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name='dp_pull_schedule_source_scope'
      AND constraint_name='chk_dp_schedule_scope_dp08_handle_size' AND constraint_type='CHECK');
SET @dp08_binding_handle_check := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name='dp_pull_scope_binding_epoch'
      AND constraint_name='chk_dp_scope_binding_dp08_handle_size' AND constraint_type='CHECK');
SET @dp08_task_handle_check := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name='dp_pull_task'
      AND constraint_name='chk_dp_task_dp08_handle_size' AND constraint_type='CHECK');
DROP TEMPORARY TABLE IF EXISTS nuono_dp08_handle_check_shape_guard;
CREATE TEMPORARY TABLE nuono_dp08_handle_check_shape_guard (
    valid_shape BIT(1) NOT NULL,
    CONSTRAINT chk_dp08_handle_check_shape CHECK (valid_shape=b'1')
) ENGINE=InnoDB;
INSERT INTO nuono_dp08_handle_check_shape_guard VALUES (IF(
    @dp08_scope_handle_check IN (0,1)
    AND @dp08_binding_handle_check IN (0,1)
    AND @dp08_task_handle_check IN (0,1), b'1', b'0'));
DROP TEMPORARY TABLE nuono_dp08_handle_check_shape_guard;

SET @dp08_scope_handle_sql := IF(@dp08_scope_handle_check=0,
    'ALTER TABLE `dp_pull_schedule_source_scope` ADD CONSTRAINT `chk_dp_schedule_scope_dp08_handle_size` CHECK (`operation_code` NOT IN (''DP08A'',''DP08B'') OR OCTET_LENGTH(`binding_payload`) BETWEEN 1 AND 4096)',
    'DO 0');
PREPARE dp08_scope_handle_stmt FROM @dp08_scope_handle_sql;
EXECUTE dp08_scope_handle_stmt;
DEALLOCATE PREPARE dp08_scope_handle_stmt;

SET @dp08_binding_handle_sql := IF(@dp08_binding_handle_check=0,
    'ALTER TABLE `dp_pull_scope_binding_epoch` ADD CONSTRAINT `chk_dp_scope_binding_dp08_handle_size` CHECK (`operation_code` NOT IN (''DP08A'',''DP08B'') OR OCTET_LENGTH(`payload`) BETWEEN 1 AND 4096)',
    'DO 0');
PREPARE dp08_binding_handle_stmt FROM @dp08_binding_handle_sql;
EXECUTE dp08_binding_handle_stmt;
DEALLOCATE PREPARE dp08_binding_handle_stmt;

SET @dp08_task_handle_sql := IF(@dp08_task_handle_check=0,
    'ALTER TABLE `dp_pull_task` ADD CONSTRAINT `chk_dp_task_dp08_handle_size` CHECK (`operation_code` NOT IN (''DP08A'',''DP08B'') OR OCTET_LENGTH(`scope_payload`) BETWEEN 1 AND 4096)',
    'DO 0');
PREPARE dp08_task_handle_stmt FROM @dp08_task_handle_sql;
EXECUTE dp08_task_handle_stmt;
DEALLOCATE PREPARE dp08_task_handle_stmt;

-- Retention contract (implemented by bounded keyset maintenance, not an unbounded DELETE):
-- * a terminal header is eligible when older than seven days OR older than the newest two;
-- * therefore keep at most two terminal epochs per operation and none beyond seven days;
-- * delete nested member items, nested heads, then staged scopes in batches of 64;
-- * the restrictive foreign key makes child-first deletion a database invariant;
-- * epoch numbers come only from the durable per-operation sequence and are never reused;
-- * never delete an active_operation_slot or a VERIFYING/SEALED manifest seal.

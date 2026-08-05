-- Migration 243: durable DP pull runtime schema.
-- AUTO_ADDITIVE: isolated runtime objects plus idempotent technical fence/sequence seeds.
-- MySQL 8.0.19+ is required for enforced CHECK constraints and row-alias upserts.
CREATE TABLE IF NOT EXISTS dp_pull_scope_admission (
    scope_key VARCHAR(96) NOT NULL, scope_namespace VARCHAR(32) NOT NULL, owner_user_id BIGINT NOT NULL,
    logical_store_id BIGINT NULL, account_key VARCHAR(160) NOT NULL, egress_key VARCHAR(160) NULL,
    project_code VARCHAR(100) NULL, store_code VARCHAR(100) NULL, site_code VARCHAR(20) NULL,
    admission_kind VARCHAR(32) NOT NULL, first_eligible_at_utc DATETIME(3) NULL COMMENT 'UTC',
    source_binding_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cutover_key VARCHAR(96) NOT NULL, gmt_create DATETIME(3) NOT NULL COMMENT 'UTC admission time',
    PRIMARY KEY (scope_key), UNIQUE KEY uk_dp_scope_admission_cutover (scope_key, cutover_key), KEY idx_dp_scope_admission_cutover (cutover_key, admission_kind, scope_key),
    CONSTRAINT chk_dp_scope_admission_identity CHECK (owner_user_id > 0 AND (logical_store_id IS NULL OR logical_store_id > 0) AND CHAR_LENGTH(TRIM(scope_namespace)) > 0 AND LEFT(scope_key, CHAR_LENGTH(scope_namespace) + 1) = CONCAT(scope_namespace, '-') AND CHAR_LENGTH(TRIM(account_key)) > 0 AND CHAR_LENGTH(TRIM(cutover_key)) > 0 AND (egress_key IS NULL OR CHAR_LENGTH(TRIM(egress_key)) > 0) AND (project_code IS NULL OR CHAR_LENGTH(TRIM(project_code)) > 0) AND (store_code IS NULL OR CHAR_LENGTH(TRIM(store_code)) > 0) AND (site_code IS NULL OR CHAR_LENGTH(TRIM(site_code)) > 0) ),
    CONSTRAINT chk_dp_scope_admission_kind CHECK ((admission_kind = 'CUTOVER_EXISTING' AND first_eligible_at_utc IS NULL) OR (admission_kind = 'POST_CUTOVER' AND first_eligible_at_utc IS NOT NULL AND gmt_create >= first_eligible_at_utc) ),
    CONSTRAINT chk_dp_scope_admission_digest CHECK (source_binding_sha256 REGEXP '^[0-9a-f]{64}$' )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_scope_binding_epoch (
    binding_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, operation_code VARCHAR(16) NOT NULL,
    scope_key VARCHAR(96) NOT NULL, payload_type VARCHAR(64) NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, payload MEDIUMTEXT NOT NULL,
    effective_from_utc DATETIME(3) NOT NULL COMMENT 'UTC', effective_until_utc DATETIME(3) NULL COMMENT 'UTC exclusive',
    source_observed_at_utc DATETIME(3) NOT NULL COMMENT 'UTC',
    open_scope_slot VARCHAR(113) NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (binding_id), UNIQUE KEY uk_dp_scope_binding_start (operation_code, scope_key, effective_from_utc),
    UNIQUE KEY uk_dp_scope_binding_open (open_scope_slot), KEY idx_dp_scope_binding_lookup (operation_code, scope_key, effective_from_utc, effective_until_utc, binding_id), KEY idx_dp_scope_binding_admission (scope_key),
    CONSTRAINT chk_dp_scope_binding_operation CHECK (operation_code IN ('DP08A', 'DP08B')),
    CONSTRAINT chk_dp_scope_binding_identity CHECK (binding_id REGEXP '^[0-9a-f]{64}$' AND CHAR_LENGTH(TRIM(scope_key)) > 0 AND CHAR_LENGTH(TRIM(payload_type)) > 0 AND payload_sha256 REGEXP '^[0-9a-f]{64}$' AND OCTET_LENGTH(payload) BETWEEN 1 AND 16711680),
    CONSTRAINT chk_dp_scope_binding_time CHECK ((effective_until_utc IS NULL OR effective_until_utc > effective_from_utc) AND source_observed_at_utc >= effective_from_utc),
    CONSTRAINT chk_dp_scope_binding_open CHECK ((effective_until_utc IS NULL AND open_scope_slot = CONCAT(operation_code, ':', scope_key)) OR (effective_until_utc IS NOT NULL AND open_scope_slot IS NULL)),
    CONSTRAINT fk_dp_scope_binding_admission FOREIGN KEY (scope_key) REFERENCES dp_pull_scope_admission (scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_task (
    id BIGINT NOT NULL, operation_code VARCHAR(16) NOT NULL, provider_channel VARCHAR(64) NOT NULL,
    owner_user_id BIGINT NOT NULL, logical_store_id BIGINT NULL, account_key VARCHAR(160) NOT NULL,
    egress_key VARCHAR(160) NULL, project_code VARCHAR(100) NULL, store_code VARCHAR(100) NULL,
    site_code VARCHAR(20) NULL, scope_key VARCHAR(96) NOT NULL, schedule_slot DATETIME(3) NOT NULL COMMENT 'UTC',
    scope_binding_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, scope_payload_type VARCHAR(64) NULL,
    scope_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, scope_payload MEDIUMTEXT NULL,
    scope_binding_effective_from_utc DATETIME(3) NULL COMMENT 'UTC',
    business_window_key VARCHAR(160) NOT NULL, state VARCHAR(32) NOT NULL, step_code VARCHAR(80) NOT NULL,
    remote_handle VARCHAR(512) NULL, checkpoint LONGTEXT NULL COMMENT 'Unbounded technical checkpoint; required for DP06 campaign lists', retry_not_before DATETIME(3) NULL COMMENT 'UTC',
    attempt INT NOT NULL DEFAULT 0, lease_owner VARCHAR(200) NULL, lease_until DATETIME(3) NULL COMMENT 'UTC',
    fence_epoch BIGINT NOT NULL DEFAULT 0, version_no BIGINT NOT NULL DEFAULT 0, sanitized_failure_code VARCHAR(80) NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL COMMENT 'UTC',
    PRIMARY KEY (id), UNIQUE KEY uk_dp_pull_task_window (operation_code, scope_key, business_window_key),
    KEY idx_dp_pull_task_due (state, retry_not_before, lease_until, schedule_slot, id), KEY idx_dp_pull_task_scope_order (operation_code, scope_key, schedule_slot, id),
    KEY idx_dp_pull_task_terminal_retention (state, finished_at, id), KEY idx_dp_pull_task_scope_binding (scope_binding_id),
    CONSTRAINT chk_dp_pull_task_operation CHECK (operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' )),
    CONSTRAINT chk_dp_pull_task_identity CHECK (owner_user_id > 0 AND (logical_store_id IS NULL OR logical_store_id > 0) AND CHAR_LENGTH(TRIM(provider_channel)) > 0 AND CHAR_LENGTH(TRIM(account_key)) > 0 AND CHAR_LENGTH(TRIM(scope_key)) > 0 AND CHAR_LENGTH(TRIM(business_window_key)) > 0 AND CHAR_LENGTH(TRIM(step_code)) > 0 AND (egress_key IS NULL OR CHAR_LENGTH(TRIM(egress_key)) > 0) AND (project_code IS NULL OR CHAR_LENGTH(TRIM(project_code)) > 0) AND (store_code IS NULL OR CHAR_LENGTH(TRIM(store_code)) > 0) AND (site_code IS NULL OR CHAR_LENGTH(TRIM(site_code)) > 0) AND (remote_handle IS NULL OR CHAR_LENGTH(TRIM(remote_handle)) > 0) AND (lease_owner IS NULL OR CHAR_LENGTH(TRIM(lease_owner)) > 0) ),
    CONSTRAINT chk_dp_pull_task_counters CHECK (attempt >= 0 AND fence_epoch >= 0 AND version_no >= 0 ),
    CONSTRAINT chk_dp_pull_task_state CHECK (state IN ( 'QUEUED', 'RUNNING', 'WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH', 'SUCCEEDED', 'FAILED', 'SUPERSEDED' )),
    CONSTRAINT chk_dp_pull_task_attempt_state CHECK ((state IN ('QUEUED', 'SUCCEEDED', 'SUPERSEDED') AND attempt = 0) OR (state IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH') AND attempt > 0) OR (state IN ('RUNNING', 'FAILED') AND attempt >= 0) ),
    CONSTRAINT chk_dp_pull_task_fence_state CHECK ((state = 'SUPERSEDED' AND fence_epoch = 0) OR (state = 'QUEUED' AND fence_epoch >= 0) OR (state IN ( 'RUNNING', 'WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH', 'SUCCEEDED', 'FAILED' ) AND fence_epoch > 0) ),
    CONSTRAINT chk_dp_pull_task_lease_state CHECK ((state = 'RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL) OR (state <> 'RUNNING' AND lease_owner IS NULL AND lease_until IS NULL) ),
    CONSTRAINT chk_dp_pull_task_retry_state CHECK ((state IN ('WAITING_REMOTE', 'WAITING_BACKOFF') AND retry_not_before IS NOT NULL) OR state = 'WAITING_AUTH' OR (state NOT IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH') AND retry_not_before IS NULL) ),
    CONSTRAINT chk_dp_pull_task_finished_state CHECK ((state IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED') AND finished_at IS NOT NULL) OR (state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED') AND finished_at IS NULL) ),
    CONSTRAINT chk_dp_pull_task_failure_state CHECK ((state IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH', 'FAILED') AND sanitized_failure_code IS NOT NULL) OR (state IN ('QUEUED', 'SUCCEEDED', 'SUPERSEDED') AND sanitized_failure_code IS NULL) OR state = 'RUNNING' ),
    CONSTRAINT chk_dp_pull_task_failure_shape CHECK (sanitized_failure_code IS NULL OR sanitized_failure_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$' ),
    CONSTRAINT chk_dp_pull_task_scope_binding CHECK ((operation_code IN ('DP08A', 'DP08B') AND scope_binding_id IS NOT NULL AND scope_payload_type IS NOT NULL AND scope_payload_sha256 IS NOT NULL AND scope_payload IS NOT NULL AND scope_binding_effective_from_utc IS NOT NULL AND schedule_slot >= scope_binding_effective_from_utc AND scope_binding_id REGEXP '^[0-9a-f]{64}$' AND CHAR_LENGTH(TRIM(scope_payload_type)) > 0 AND scope_payload_sha256 REGEXP '^[0-9a-f]{64}$' AND OCTET_LENGTH(scope_payload) BETWEEN 1 AND 16711680) OR (operation_code NOT IN ('DP08A', 'DP08B') AND scope_binding_id IS NULL AND scope_payload_type IS NULL AND scope_payload_sha256 IS NULL AND scope_payload IS NULL AND scope_binding_effective_from_utc IS NULL)),
    CONSTRAINT fk_dp_pull_task_scope_binding FOREIGN KEY (scope_binding_id) REFERENCES dp_pull_scope_binding_epoch (binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_runtime_leader (
    runtime_name VARCHAR(32) NOT NULL, leader_owner VARCHAR(200) NULL, leader_epoch BIGINT NOT NULL DEFAULT 0,
    lease_until DATETIME(3) NULL COMMENT 'UTC', gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (runtime_name),
    CONSTRAINT chk_dp_runtime_leader_singleton CHECK (runtime_name = 'daily_pull'),
    CONSTRAINT chk_dp_runtime_leader_epoch CHECK (leader_epoch >= 0),
    CONSTRAINT chk_dp_runtime_leader_lease CHECK ((leader_owner IS NULL AND lease_until IS NULL) OR (leader_owner IS NOT NULL AND CHAR_LENGTH(TRIM(leader_owner)) > 0 AND leader_epoch > 0 AND lease_until IS NOT NULL) )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO dp_pull_runtime_leader (runtime_name, leader_owner, leader_epoch, lease_until, gmt_create, gmt_updated)
VALUES ('daily_pull', NULL, 0, NULL, NOW(3), NOW(3)) AS incoming ON DUPLICATE KEY UPDATE runtime_name = incoming.runtime_name;
CREATE TABLE IF NOT EXISTS dp_pull_backoff_hold (
    hold_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, share_level VARCHAR(16) NOT NULL, provider_channel VARCHAR(64) NOT NULL,
    account_key VARCHAR(160) NOT NULL, operation_code VARCHAR(16) NOT NULL, scope_key VARCHAR(96) NOT NULL,
    egress_key VARCHAR(160) NULL, blocked_until DATETIME(3) NOT NULL COMMENT 'UTC', sanitized_code VARCHAR(80) NOT NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (hold_key), KEY idx_dp_pull_backoff_expiry (blocked_until),
    KEY idx_dp_pull_backoff_account (provider_channel, share_level, account_key, blocked_until ), KEY idx_dp_pull_backoff_exact (provider_channel, share_level, account_key, operation_code, scope_key, blocked_until ),
    KEY idx_dp_pull_backoff_exit (provider_channel, share_level, egress_key, blocked_until ),
    CONSTRAINT chk_dp_pull_backoff_digest CHECK (hold_key REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp_pull_backoff_share CHECK (share_level IN ('EXACT', 'ACCOUNT', 'EXIT') ),
    CONSTRAINT chk_dp_pull_backoff_operation CHECK (operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' )),
    CONSTRAINT chk_dp_pull_backoff_identity CHECK (CHAR_LENGTH(TRIM(provider_channel)) > 0 AND CHAR_LENGTH(TRIM(account_key)) > 0 AND CHAR_LENGTH(TRIM(scope_key)) > 0 AND (egress_key IS NULL OR CHAR_LENGTH(TRIM(egress_key)) > 0) AND (share_level <> 'EXIT' OR egress_key IS NOT NULL) ),
    CONSTRAINT chk_dp_pull_backoff_code CHECK (sanitized_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$' ),
    CONSTRAINT chk_dp_pull_backoff_time CHECK (blocked_until >= gmt_updated)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_emergency_claim_hold (
    hold_key VARCHAR(160) NOT NULL, hold_scope VARCHAR(16) NOT NULL, operation_code VARCHAR(16) NULL,
    scope_key VARCHAR(96) NULL, blocked_until DATETIME(3) NOT NULL COMMENT 'UTC', sanitized_reason VARCHAR(80) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0, gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (hold_key), KEY idx_dp_emergency_claim_hold_expiry (blocked_until),
    KEY idx_dp_emergency_claim_hold_target (hold_scope, operation_code, scope_key, blocked_until ),
    CONSTRAINT chk_dp_emergency_claim_hold_target CHECK ((hold_scope = 'GLOBAL' AND operation_code IS NULL AND scope_key IS NULL) OR (hold_scope = 'OPERATION' AND operation_code IS NOT NULL AND scope_key IS NULL) OR (hold_scope = 'SCOPE' AND operation_code IS NOT NULL AND scope_key IS NOT NULL) ),
    CONSTRAINT chk_dp_emergency_claim_hold_key CHECK ((hold_scope = 'GLOBAL' AND hold_key = 'GLOBAL') OR (hold_scope = 'OPERATION' AND hold_key = CONCAT('OPERATION:', operation_code)) OR (hold_scope = 'SCOPE' AND hold_key = CONCAT('SCOPE:', operation_code, ':', scope_key)) ),
    CONSTRAINT chk_dp_emergency_claim_hold_operation CHECK (operation_code IS NULL OR operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' ) ),
    CONSTRAINT chk_dp_emergency_claim_hold_version CHECK (version_no >= 0),
    CONSTRAINT chk_dp_emergency_claim_hold_reason CHECK (sanitized_reason REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$' ),
    CONSTRAINT chk_dp_emergency_claim_hold_time CHECK (blocked_until > gmt_updated)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_schedule_cutover (
    operation_code VARCHAR(16) NOT NULL, cutover_key VARCHAR(96) NOT NULL, state VARCHAR(16) NOT NULL,
    expected_scope_count INT NOT NULL, anchor_manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, activated_at_utc DATETIME(3) NULL COMMENT 'UTC; required only for ACTIVE',
    version_no BIGINT NOT NULL DEFAULT 0, gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code), UNIQUE KEY uk_dp_schedule_cutover_key (operation_code, cutover_key),
    CONSTRAINT chk_dp_schedule_cutover_state CHECK ((state = 'PREPARING' AND activated_at_utc IS NULL) OR (state = 'ACTIVE' AND activated_at_utc IS NOT NULL) ),
    CONSTRAINT chk_dp_schedule_cutover_count CHECK (expected_scope_count >= 0),
    CONSTRAINT chk_dp_schedule_cutover_operation CHECK (operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' )),
    CONSTRAINT chk_dp_schedule_cutover_identity CHECK (CHAR_LENGTH(TRIM(cutover_key)) > 0 AND version_no >= 0 ),
    CONSTRAINT chk_dp_schedule_cutover_digest CHECK (anchor_manifest_sha256 REGEXP '^[0-9a-f]{64}$' )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_schedule_anchor (
    operation_code VARCHAR(16) NOT NULL, scope_key VARCHAR(96) NOT NULL, cutover_key VARCHAR(96) NOT NULL,
    anchor_kind VARCHAR(32) NOT NULL, reconcile_after_utc DATETIME(3) NOT NULL COMMENT 'UTC exclusive lower bound',
    anchor_evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, scope_key), KEY idx_dp_schedule_anchor_manifest (operation_code, cutover_key, anchor_kind, scope_key ),
    CONSTRAINT chk_dp_schedule_anchor_kind CHECK (anchor_kind IN ('CUTOVER_RECONCILED', 'POST_CUTOVER_SCOPE') ),
    CONSTRAINT chk_dp_schedule_anchor_operation CHECK (operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' )),
    CONSTRAINT chk_dp_schedule_anchor_identity CHECK (CHAR_LENGTH(TRIM(scope_key)) > 0 AND CHAR_LENGTH(TRIM(cutover_key)) > 0 AND anchor_evidence_sha256 REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT fk_dp_schedule_anchor_cutover FOREIGN KEY (operation_code, cutover_key) REFERENCES dp_pull_schedule_cutover (operation_code, cutover_key),
    CONSTRAINT fk_dp_schedule_anchor_admission FOREIGN KEY (scope_key, cutover_key) REFERENCES dp_pull_scope_admission (scope_key, cutover_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_scope_progress (
    operation_code VARCHAR(16) NOT NULL, scope_key VARCHAR(96) NOT NULL, initial_full_completed BIT(1) NOT NULL DEFAULT b'0',
    official_modified_high_water_utc DATETIME(3) NULL, last_applied_business_window_key VARCHAR(160) NULL, version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, scope_key),
    CONSTRAINT chk_dp_scope_progress_operation CHECK (operation_code IN ( 'DP01', 'DP02', 'DP03', 'DP04', 'DP05', 'DP06', 'DP07A', 'DP07B', 'DP08A', 'DP08B', 'DP10' )),
    CONSTRAINT chk_dp_scope_progress_identity CHECK (CHAR_LENGTH(TRIM(scope_key)) > 0 AND (last_applied_business_window_key IS NULL OR CHAR_LENGTH(TRIM(last_applied_business_window_key)) > 0) AND version_no >= 0 ),
    CONSTRAINT chk_dp_scope_progress_dp10 CHECK ((operation_code <> 'DP10' AND official_modified_high_water_utc IS NULL) OR (operation_code = 'DP10' AND initial_full_completed = b'0' AND official_modified_high_water_utc IS NULL AND last_applied_business_window_key IS NULL) OR (operation_code = 'DP10' AND initial_full_completed = b'1' AND official_modified_high_water_utc IS NOT NULL AND last_applied_business_window_key IS NOT NULL) )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_snapshot_stage (
    task_id BIGINT NOT NULL, active_fence_epoch BIGINT NOT NULL, declared_total_pages INT NULL,
    known_last_page INT NULL, poison_code VARCHAR(80) NULL, authority_kind VARCHAR(32) NULL,
    authority_token_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    snapshot_as_of_utc DATETIME(3) NULL COMMENT 'provider UTC when present', declared_collection_count BIGINT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT chk_dp_snapshot_stage_fence CHECK (active_fence_epoch > 0),
    CONSTRAINT chk_dp_snapshot_stage_pages CHECK ((declared_total_pages IS NULL OR declared_total_pages > 0) AND (known_last_page IS NULL OR known_last_page > 0) AND (declared_total_pages IS NULL OR known_last_page IS NULL OR declared_total_pages = known_last_page) ),
    CONSTRAINT chk_dp_snapshot_stage_poison CHECK (poison_code IS NULL OR poison_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$' ),
    CONSTRAINT chk_dp_snapshot_stage_authority CHECK ((authority_kind IS NULL AND authority_token_sha256 IS NULL AND snapshot_as_of_utc IS NULL AND declared_collection_count IS NULL) OR (authority_kind IS NOT NULL AND authority_kind IN ('PAGED_GENERATION', 'COMPLETE_EXPORT') AND authority_token_sha256 IS NOT NULL AND authority_token_sha256 REGEXP '^[0-9a-f]{64}$' AND declared_collection_count IS NOT NULL AND declared_collection_count >= 0) ),
    CONSTRAINT chk_dp_snapshot_stage_version CHECK (version_no >= 0),
    CONSTRAINT fk_dp_snapshot_stage_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_snapshot_stage_page (
    task_id BIGINT NOT NULL, page_no INT NOT NULL, next_page INT NULL,
    is_last_page BIT(1) NULL, total_pages INT NULL, item_count INT NOT NULL,
    source_item_count INT NOT NULL, business_skipped_item_count INT NOT NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, page_no),
    CONSTRAINT chk_dp_snapshot_page_number CHECK ((next_page IS NULL OR next_page > 0) AND (total_pages IS NULL OR total_pages > 0) AND item_count >= 0 AND business_skipped_item_count >= 0 AND source_item_count = item_count + business_skipped_item_count ),
    CONSTRAINT chk_dp_snapshot_page_last CHECK (is_last_page IS NULL OR is_last_page = b'0' OR (is_last_page = b'1' AND next_page IS NULL) ),
    CONSTRAINT fk_dp_snapshot_page_stage FOREIGN KEY (task_id) REFERENCES dp_pull_snapshot_stage (task_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_snapshot_stage_item (
    task_id BIGINT NOT NULL, page_no INT NOT NULL, item_ordinal INT NOT NULL,
    stable_identity VARCHAR(240) NOT NULL, content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, payload MEDIUMTEXT NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, page_no, item_ordinal),
    CONSTRAINT chk_dp_snapshot_item_identity CHECK (item_ordinal >= 0 AND CHAR_LENGTH(TRIM(stable_identity)) > 0 ),
    CONSTRAINT chk_dp_snapshot_item_fingerprint CHECK (content_fingerprint REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp_snapshot_item_payload CHECK (OCTET_LENGTH(payload) <= 16711680 ),
    CONSTRAINT fk_dp_snapshot_item_page FOREIGN KEY (task_id, page_no) REFERENCES dp_pull_snapshot_stage_page (task_id, page_no) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_snapshot_apply (
    task_id BIGINT NOT NULL, operation_code VARCHAR(16) NOT NULL, scope_key VARCHAR(96) NOT NULL,
    business_window_key VARCHAR(160) NOT NULL, applied_fence_epoch BIGINT NOT NULL,
    authority_kind VARCHAR(32) NOT NULL, authority_token_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, snapshot_as_of_utc DATETIME(3) NULL COMMENT 'provider UTC when present',
    declared_collection_count BIGINT NOT NULL, source_item_count BIGINT NOT NULL, applied_item_count BIGINT NOT NULL, identity_skipped_item_count BIGINT NOT NULL, business_skipped_item_count BIGINT NOT NULL, last_page INT NOT NULL,
    applied_at DATETIME(3) NOT NULL COMMENT 'UTC',
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id), UNIQUE KEY uk_dp_snapshot_apply_window (operation_code, scope_key, business_window_key),
    CONSTRAINT chk_dp_snapshot_apply_operation CHECK (operation_code IN ('DP04', 'DP07A')),
    CONSTRAINT chk_dp_snapshot_apply_identity CHECK (CHAR_LENGTH(TRIM(scope_key)) > 0 AND CHAR_LENGTH(TRIM(business_window_key)) > 0 AND applied_fence_epoch > 0 ),
    CONSTRAINT chk_dp_snapshot_apply_authority CHECK (authority_kind IN ('PAGED_GENERATION', 'COMPLETE_EXPORT') AND authority_token_sha256 REGEXP '^[0-9a-f]{64}$' AND declared_collection_count >= 0 ),
    CONSTRAINT chk_dp_snapshot_apply_accounting CHECK (source_item_count = declared_collection_count AND source_item_count = applied_item_count + identity_skipped_item_count + business_skipped_item_count AND applied_item_count >= 0 AND identity_skipped_item_count >= 0 AND business_skipped_item_count >= 0 AND last_page > 0 ),
    CONSTRAINT fk_dp_snapshot_apply_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_dp10_stage_page (
    task_id BIGINT NOT NULL, generation_no BIGINT NOT NULL, scan_pass TINYINT NOT NULL COMMENT '1 or 2 over the same fixed S/T window',
    partition_name VARCHAR(16) NOT NULL COMMENT 'CURRENT or HISTORY', page_no INT NOT NULL, active_fence_epoch BIGINT NOT NULL,
    page_size INT NOT NULL, total_record BIGINT NOT NULL, expected_pages INT NOT NULL,
    raw_row_count INT NOT NULL, state VARCHAR(16) NOT NULL, page_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, generation_no, scan_pass, partition_name, page_no), KEY idx_dp10_stage_page_action (task_id, generation_no, scan_pass, state, partition_name, page_no),
    CONSTRAINT chk_dp10_stage_generation CHECK (generation_no > 0),
    CONSTRAINT chk_dp10_stage_scan_pass CHECK (scan_pass IN (1, 2)),
    CONSTRAINT chk_dp10_stage_partition CHECK (partition_name IN ('CURRENT', 'HISTORY')),
    CONSTRAINT chk_dp10_stage_page_no CHECK (page_no > 0),
    CONSTRAINT chk_dp10_stage_page_fence CHECK (active_fence_epoch > 0),
    CONSTRAINT chk_dp10_stage_page_size CHECK (page_size BETWEEN 1 AND 100),
    CONSTRAINT chk_dp10_stage_total CHECK (total_record >= 0),
    CONSTRAINT chk_dp10_stage_expected_pages CHECK (expected_pages > 0),
    CONSTRAINT chk_dp10_stage_raw_rows CHECK (raw_row_count BETWEEN 0 AND page_size AND page_no <= expected_pages AND expected_pages = CASE WHEN total_record = 0 THEN 1 ELSE ((total_record - 1) DIV page_size) + 1 END AND raw_row_count = CASE WHEN page_no < expected_pages THEN page_size ELSE total_record - (page_size * (expected_pages - 1)) END ),
    CONSTRAINT chk_dp10_stage_page_state CHECK (state IN ('LISTED', 'READY', 'VERIFYING', 'VERIFIED', 'APPLIED') ),
    CONSTRAINT chk_dp10_stage_page_pass_state CHECK (scan_pass = 2 OR state = 'LISTED' ),
    CONSTRAINT chk_dp10_stage_page_fingerprint CHECK (page_fingerprint REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT fk_dp10_stage_page_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_dp10_stage_item (
    task_id BIGINT NOT NULL, generation_no BIGINT NOT NULL, scan_pass TINYINT NOT NULL,
    partition_name VARCHAR(16) NOT NULL, page_no INT NOT NULL, item_ordinal INT NOT NULL,
    provider_order_no VARCHAR(120) NULL, provider_modified_at DATETIME(3) NULL COMMENT 'UTC', state VARCHAR(40) NOT NULL,
    validation_code VARCHAR(80) NULL, list_content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload MEDIUMTEXT NULL COMMENT 'app-enforced UTF-8 max 16711680 bytes; oversized item is skipped', verification_state VARCHAR(20) NOT NULL COMMENT 'NOT_APPLICABLE, PENDING, VERIFIED', apply_state VARCHAR(20) NOT NULL COMMENT 'NOT_APPLICABLE, BLOCKED, READY, SKIPPED, APPLIED',
    apply_item_cursor INT NOT NULL DEFAULT 0, gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, generation_no, scan_pass, partition_name, page_no, item_ordinal), KEY idx_dp10_stage_pending (task_id, generation_no, scan_pass, state, partition_name, page_no, item_ordinal),
    KEY idx_dp10_stage_apply (task_id, generation_no, scan_pass, apply_state, partition_name, page_no, item_ordinal),
    CONSTRAINT chk_dp10_stage_item_generation CHECK (generation_no > 0),
    CONSTRAINT chk_dp10_stage_item_scan_pass CHECK (scan_pass IN (1, 2)),
    CONSTRAINT chk_dp10_stage_item_partition CHECK (partition_name IN ('CURRENT', 'HISTORY') ),
    CONSTRAINT chk_dp10_stage_item_position CHECK (page_no > 0 AND item_ordinal >= 0),
    CONSTRAINT chk_dp10_stage_item_state CHECK (state IN ( 'PENDING_DETAIL', 'COMPLETE', 'SKIP_BUSINESS_ITEM', 'SKIP_NOT_FOUND', 'SKIP_LATER_IDENTITY_CONFLICT' )),
    CONSTRAINT chk_dp10_stage_verification_state CHECK (verification_state IN ('NOT_APPLICABLE', 'PENDING', 'VERIFIED') ),
    CONSTRAINT chk_dp10_stage_apply_state CHECK (apply_state IN ('NOT_APPLICABLE', 'BLOCKED', 'READY', 'SKIPPED', 'APPLIED') ),
    CONSTRAINT chk_dp10_stage_apply_cursor CHECK (apply_item_cursor >= 0),
    CONSTRAINT chk_dp10_stage_item_lifecycle CHECK ((scan_pass = 1 AND verification_state = 'NOT_APPLICABLE' AND apply_state = 'NOT_APPLICABLE') OR (scan_pass = 2 AND verification_state = 'PENDING' AND apply_state = 'BLOCKED') OR (scan_pass = 2 AND verification_state = 'VERIFIED' AND apply_state IN ('READY', 'SKIPPED', 'APPLIED')) ),
    CONSTRAINT chk_dp10_stage_item_decision CHECK ((state IN ('PENDING_DETAIL', 'COMPLETE') AND validation_code IS NULL) OR (state IN ( 'SKIP_BUSINESS_ITEM', 'SKIP_NOT_FOUND', 'SKIP_LATER_IDENTITY_CONFLICT' ) AND validation_code IS NOT NULL AND validation_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$') ),
    CONSTRAINT chk_dp10_stage_item_apply_decision CHECK (apply_state NOT IN ('READY', 'APPLIED') OR state = 'COMPLETE' ),
    CONSTRAINT chk_dp10_stage_item_skip_decision CHECK (apply_state <> 'SKIPPED' OR state IN ( 'SKIP_BUSINESS_ITEM', 'SKIP_NOT_FOUND', 'SKIP_LATER_IDENTITY_CONFLICT' ) ),
    CONSTRAINT chk_dp10_stage_item_identity CHECK ((provider_order_no IS NULL OR CHAR_LENGTH(TRIM(provider_order_no)) > 0) AND (state NOT IN ( 'PENDING_DETAIL', 'COMPLETE', 'SKIP_NOT_FOUND', 'SKIP_LATER_IDENTITY_CONFLICT' ) OR provider_order_no IS NOT NULL) ),
    CONSTRAINT chk_dp10_stage_item_fingerprints CHECK (list_content_fingerprint REGEXP '^[0-9a-f]{64}$' AND content_fingerprint REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp10_stage_item_payload CHECK (OCTET_LENGTH(payload) <= 16711680 AND (state NOT IN ('PENDING_DETAIL', 'COMPLETE') OR payload IS NOT NULL) ),
    CONSTRAINT fk_dp10_stage_item_page FOREIGN KEY (task_id, generation_no, scan_pass, partition_name, page_no ) REFERENCES dp_pull_dp10_stage_page (task_id, generation_no, scan_pass, partition_name, page_no )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_dp10_stage_fingerprint_count (
    task_id BIGINT NOT NULL, generation_no BIGINT NOT NULL, partition_name VARCHAR(16) NOT NULL COMMENT 'CURRENT or HISTORY',
    list_content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, pass_one_count BIGINT NOT NULL DEFAULT 0, pass_two_count BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, generation_no, partition_name, list_content_fingerprint),
    CONSTRAINT chk_dp10_fingerprint_generation CHECK (generation_no > 0),
    CONSTRAINT chk_dp10_fingerprint_partition CHECK (partition_name IN ('CURRENT', 'HISTORY') ),
    CONSTRAINT chk_dp10_fingerprint_shape CHECK (list_content_fingerprint REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp10_fingerprint_pass_one CHECK (pass_one_count >= 0),
    CONSTRAINT chk_dp10_fingerprint_pass_two CHECK (pass_two_count >= 0),
    CONSTRAINT chk_dp10_fingerprint_nonempty CHECK (pass_one_count > 0 OR pass_two_count > 0 ),
    CONSTRAINT fk_dp10_fingerprint_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_dp10_stage_identity (
    task_id BIGINT NOT NULL, generation_no BIGINT NOT NULL, provider_order_no VARCHAR(120) NOT NULL,
    first_partition VARCHAR(16) NOT NULL, first_page_no INT NOT NULL, first_item_ordinal INT NOT NULL,
    active_fence_epoch BIGINT NOT NULL, gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, generation_no, provider_order_no),
    CONSTRAINT chk_dp10_identity_generation CHECK (generation_no > 0),
    CONSTRAINT chk_dp10_identity_partition CHECK (first_partition IN ('CURRENT', 'HISTORY')),
    CONSTRAINT chk_dp10_identity_page CHECK (first_page_no > 0),
    CONSTRAINT chk_dp10_identity_ordinal CHECK (first_item_ordinal >= 0),
    CONSTRAINT chk_dp10_identity_fence CHECK (active_fence_epoch > 0),
    CONSTRAINT chk_dp10_identity_value CHECK (CHAR_LENGTH(TRIM(provider_order_no)) > 0 ),
    CONSTRAINT fk_dp10_stage_identity_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_dp10_stage_cleanup (
    task_id BIGINT NOT NULL, generation_no BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL, active_fence_epoch BIGINT NOT NULL,
    gmt_create DATETIME(3) NOT NULL, gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, generation_no), UNIQUE KEY uk_dp10_cleanup_task (task_id),
    CONSTRAINT chk_dp10_cleanup_generation CHECK (generation_no > 0),
    CONSTRAINT chk_dp10_cleanup_reason CHECK (reason IN ('CURRENT_GENERATION', 'OLDER_GENERATION', 'FAILED_RETENTION')),
    CONSTRAINT chk_dp10_cleanup_fence CHECK (active_fence_epoch > 0),
    CONSTRAINT fk_dp10_cleanup_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_report_artifact (
    artifact_key VARCHAR(96) NOT NULL, task_id BIGINT NOT NULL, stable_request_key VARCHAR(96) NOT NULL,
    remote_handle VARCHAR(512) NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, content_length BIGINT NOT NULL DEFAULT 0,
    content_bytes LONGBLOB NULL, download_state VARCHAR(20) NOT NULL DEFAULT 'LEGACY_COMPLETE', persisted_chunk_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL COMMENT 'UTC', updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC',
    PRIMARY KEY (artifact_key), KEY idx_dp_report_artifact_retention (task_id, created_at, artifact_key), KEY idx_dp_report_artifact_download_state (download_state, updated_at, artifact_key),
    CONSTRAINT chk_dp_report_artifact_identity CHECK (CHAR_LENGTH(TRIM(artifact_key)) > 0 AND CHAR_LENGTH(TRIM(stable_request_key)) > 0 AND CHAR_LENGTH(TRIM(remote_handle)) > 0 ),
    CONSTRAINT chk_dp_report_artifact_digest CHECK (content_sha256 IS NULL OR content_sha256 REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp_report_artifact_length CHECK (content_length BETWEEN 0 AND 2251799812636672),
    CONSTRAINT chk_dp_report_artifact_download_state CHECK (download_state IN ('LEGACY_COMPLETE', 'DOWNLOADING', 'COMPLETE')),
    CONSTRAINT chk_dp_report_artifact_chunk_count CHECK (persisted_chunk_count BETWEEN 0 AND 2147483647),
    CONSTRAINT chk_dp_report_artifact_storage_shape CHECK ((download_state = 'LEGACY_COMPLETE' AND content_sha256 IS NOT NULL AND content_length > 0 AND content_bytes IS NOT NULL AND content_length = OCTET_LENGTH(content_bytes) AND persisted_chunk_count = 0) OR (download_state = 'DOWNLOADING' AND content_sha256 IS NULL AND content_length = 0 AND content_bytes IS NULL AND persisted_chunk_count = 0) OR (download_state = 'COMPLETE' AND content_sha256 IS NOT NULL AND content_length > 0 AND content_bytes IS NULL AND persisted_chunk_count = CEIL(content_length / 1048576))),
    CONSTRAINT fk_dp_report_artifact_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_report_download_locator (
    locator_ref VARCHAR(64) NOT NULL, task_id BIGINT NOT NULL, stable_request_key VARCHAR(96) NOT NULL,
    remote_handle_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, iv VARBINARY(12) NOT NULL, encrypted_locator MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (locator_ref), KEY idx_dp_report_locator_retention (task_id, created_at, locator_ref),
    CONSTRAINT chk_dp_report_locator_identity CHECK (CHAR_LENGTH(TRIM(locator_ref)) > 0 AND CHAR_LENGTH(TRIM(stable_request_key)) > 0 ),
    CONSTRAINT chk_dp_report_locator_digest CHECK (remote_handle_sha256 REGEXP '^[0-9a-f]{64}$' ),
    CONSTRAINT chk_dp_report_locator_iv CHECK (OCTET_LENGTH(iv) = 12),
    CONSTRAINT chk_dp_report_locator_ciphertext CHECK (OCTET_LENGTH(encrypted_locator) BETWEEN 17 AND 16400 ),
    CONSTRAINT fk_dp_report_locator_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_report_apply (
    task_id BIGINT NOT NULL, operation_code VARCHAR(16) NOT NULL, scope_key VARCHAR(96) NOT NULL,
    business_window_key VARCHAR(160) NOT NULL, applied_fence_epoch BIGINT NOT NULL, applied_at DATETIME(3) NOT NULL COMMENT 'UTC',
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id), UNIQUE KEY uk_dp_report_apply_window (operation_code, scope_key, business_window_key),
    CONSTRAINT chk_dp_report_apply_operation CHECK (operation_code IN ('DP01', 'DP02', 'DP03', 'DP07B') ),
    CONSTRAINT chk_dp_report_apply_identity CHECK (CHAR_LENGTH(TRIM(scope_key)) > 0 AND CHAR_LENGTH(TRIM(business_window_key)) > 0 AND applied_fence_epoch > 0 ),
    CONSTRAINT fk_dp_report_apply_task FOREIGN KEY (task_id) REFERENCES dp_pull_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO noon_pull_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
VALUES ('dp_pull_task', GREATEST(0, COALESCE((SELECT MAX(id) FROM dp_pull_task), 0)), NOW(3), NOW(3)) AS incoming
ON DUPLICATE KEY UPDATE next_id = GREATEST(noon_pull_id_sequence.next_id, incoming.next_id), gmt_updated = NOW(3);

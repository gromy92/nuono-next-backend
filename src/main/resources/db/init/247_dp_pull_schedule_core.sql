-- Migration 247: bounded schedule rotation, source epochs, manifests, and scope staging.
-- Schedule-core predecessor: existing dp_pull_schedule_cutover schema.
-- Owns rotation, epoch allocation, manifest seal, source epochs and immutable source scopes.
-- DP08 member-set storage is intentionally owned by successor migration 248.
SET NAMES utf8mb4;

-- Runtime contract: reserve at most 3 operations/tick; advance one phase and at most 64 logical
-- scopes per operation transaction; every operation transaction has a 10-second deadline.
-- DP08 missing-binding closes are forbidden until both source passes match and the complete
-- sealed present-binding pass has moved binding_close_state from PENDING to RUNNING.

CREATE TABLE IF NOT EXISTS dp_pull_schedule_rotation (
    runtime_name VARCHAR(32) NOT NULL,
    next_operation_ordinal TINYINT UNSIGNED NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (runtime_name),
    CONSTRAINT chk_dp_schedule_rotation_singleton
        CHECK (runtime_name = 'daily_pull'),
    CONSTRAINT chk_dp_schedule_rotation_ordinal
        CHECK (next_operation_ordinal < 11 AND version_no >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO dp_pull_schedule_rotation (
    runtime_name, next_operation_ordinal, version_no, gmt_create, gmt_updated
) VALUES ('daily_pull', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)) AS incoming
ON DUPLICATE KEY UPDATE runtime_name = incoming.runtime_name;

CREATE TABLE IF NOT EXISTS dp_pull_schedule_epoch_sequence (
    operation_code VARCHAR(16) NOT NULL,
    last_epoch_no BIGINT NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code),
    CONSTRAINT chk_dp_schedule_epoch_sequence_operation CHECK (operation_code IN (
        'DP01','DP02','DP03','DP04','DP05','DP06','DP07A','DP07B','DP08A','DP08B','DP10'
    )),
    CONSTRAINT chk_dp_schedule_epoch_sequence_value
        CHECK (last_epoch_no >= 0 AND version_no >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO dp_pull_schedule_epoch_sequence (
    operation_code, last_epoch_no, version_no, gmt_create, gmt_updated
) VALUES
    ('DP01',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP02',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP03',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP04',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP05',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP06',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP07A',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP07B',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP08A',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP08B',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
    ('DP10',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) AS incoming
ON DUPLICATE KEY UPDATE operation_code = incoming.operation_code;

CREATE TABLE IF NOT EXISTS dp_pull_schedule_manifest_seal (
    operation_code VARCHAR(16) NOT NULL,
    cutover_key VARCHAR(96) NOT NULL,
    expected_scope_count INT NOT NULL,
    expected_manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seal_state VARCHAR(16) NOT NULL,
    next_scope_key VARCHAR(96) NULL,
    scanned_scope_count INT NOT NULL DEFAULT 0,
    resumable_sha256_state VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verified_manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    sealed_at_utc DATETIME(3) NULL,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, cutover_key),
    KEY idx_dp_schedule_manifest_state (seal_state, operation_code, cutover_key),
    CONSTRAINT chk_dp_schedule_manifest_operation CHECK (operation_code IN (
        'DP01','DP02','DP03','DP04','DP05','DP06','DP07A','DP07B','DP08A','DP08B','DP10'
    )),
    CONSTRAINT chk_dp_schedule_manifest_count
        CHECK (expected_scope_count >= 0 AND scanned_scope_count BETWEEN 0 AND expected_scope_count),
    CONSTRAINT chk_dp_schedule_manifest_digest CHECK (
        expected_manifest_sha256 REGEXP '^[0-9a-f]{64}$'
        AND (verified_manifest_sha256 IS NULL
             OR verified_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_dp_schedule_manifest_state CHECK (
        (seal_state = 'VERIFYING' AND sealed_at_utc IS NULL
         AND verified_manifest_sha256 IS NULL)
        OR
        (seal_state = 'SEALED' AND sealed_at_utc IS NOT NULL
         AND verified_manifest_sha256 IS NOT NULL
         AND verified_manifest_sha256 = expected_manifest_sha256
         AND scanned_scope_count = expected_scope_count)
        OR
        (seal_state = 'REJECTED' AND sealed_at_utc IS NULL
         AND verified_manifest_sha256 IS NULL)
    ),
    CONSTRAINT fk_dp_schedule_manifest_cutover
        FOREIGN KEY (operation_code, cutover_key)
        REFERENCES dp_pull_schedule_cutover (operation_code, cutover_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_schedule_source_epoch (
    operation_code VARCHAR(16) NOT NULL,
    epoch_no BIGINT NOT NULL,
    cutover_key VARCHAR(96) NOT NULL COMMENT 'verified official cutover manifest identity',
    active_operation_slot VARCHAR(16) NULL,
    epoch_state VARCHAR(24) NOT NULL,
    reconcile_until_utc DATETIME(3) NOT NULL COMMENT 'immutable UTC upper bound',
    pass_one_cursor VARCHAR(512) NULL,
    pass_one_scope_count BIGINT NOT NULL DEFAULT 0,
    pass_one_ordered_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pass_two_cursor VARCHAR(512) NULL,
    pass_two_scope_count BIGINT NOT NULL DEFAULT 0,
    pass_two_ordered_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admission_cursor_scope_key VARCHAR(96) NULL,
    binding_cursor_scope_key VARCHAR(96) NULL,
    missing_binding_cursor_scope_key VARCHAR(96) NULL,
    schedule_cursor_scope_key VARCHAR(96) NULL,
    binding_close_state VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    version_no BIGINT NOT NULL DEFAULT 0,
    sealed_at_utc DATETIME(3) NULL,
    terminal_at_utc DATETIME(3) NULL,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, epoch_no),
    UNIQUE KEY uk_dp_schedule_epoch_active (active_operation_slot),
    KEY idx_dp_schedule_epoch_retention (operation_code, terminal_at_utc, epoch_no),
    CONSTRAINT chk_dp_schedule_epoch_operation CHECK (operation_code IN (
        'DP01','DP02','DP03','DP04','DP05','DP06','DP07A','DP07B','DP08A','DP08B','DP10'
    )),
    CONSTRAINT chk_dp_schedule_epoch_number CHECK (epoch_no > 0 AND version_no >= 0),
    CONSTRAINT chk_dp_schedule_epoch_count
        CHECK (pass_one_scope_count >= 0 AND pass_two_scope_count >= 0),
    CONSTRAINT chk_dp_schedule_epoch_digest CHECK (
        pass_one_ordered_sha256 REGEXP '^[0-9a-f]{64}$'
        AND pass_two_ordered_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_dp_schedule_epoch_state CHECK (epoch_state IN (
        'PASS_ONE','PASS_TWO','SEALED','ADMITTING','BINDING_PRESENT',
        'BINDING_MISSING','SCHEDULING','COMPLETE','ABORTED'
    )),
    CONSTRAINT chk_dp_schedule_epoch_active CHECK (
        (epoch_state IN ('COMPLETE','ABORTED')
         AND active_operation_slot IS NULL)
        OR
        (epoch_state NOT IN ('COMPLETE','ABORTED')
         AND active_operation_slot IS NOT NULL
         AND active_operation_slot = operation_code)
    ),
    CONSTRAINT chk_dp_schedule_epoch_seal CHECK (
        (epoch_state IN ('PASS_ONE','PASS_TWO','ABORTED') AND sealed_at_utc IS NULL)
        OR
        (epoch_state IN ('SEALED','ADMITTING','BINDING_PRESENT','BINDING_MISSING',
                         'SCHEDULING','COMPLETE')
         AND sealed_at_utc IS NOT NULL
         AND pass_one_scope_count = pass_two_scope_count
         AND pass_one_ordered_sha256 = pass_two_ordered_sha256)
    ),
    CONSTRAINT chk_dp_schedule_epoch_terminal CHECK (
        (epoch_state IN ('COMPLETE','ABORTED') AND terminal_at_utc IS NOT NULL)
        OR (epoch_state NOT IN ('COMPLETE','ABORTED') AND terminal_at_utc IS NULL)
    ),
    CONSTRAINT chk_dp_schedule_epoch_binding_close CHECK (
        (operation_code IN ('DP08A','DP08B')
         AND binding_close_state IN ('PENDING','RUNNING','COMPLETE'))
        OR
        (operation_code NOT IN ('DP08A','DP08B')
         AND binding_close_state = 'NOT_REQUIRED')
    ),
    CONSTRAINT fk_dp_schedule_epoch_manifest
        FOREIGN KEY (operation_code, cutover_key)
        REFERENCES dp_pull_schedule_manifest_seal (operation_code, cutover_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS dp_pull_schedule_source_scope (
    operation_code VARCHAR(16) NOT NULL,
    epoch_no BIGINT NOT NULL,
    source_cursor VARCHAR(512) NOT NULL COMMENT 'native source keyset tuple; never OFFSET',
    source_cursor_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_key VARCHAR(96) NOT NULL,
    scope_namespace VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    logical_store_id BIGINT NULL,
    account_key VARCHAR(160) NOT NULL,
    egress_key VARCHAR(160) NULL,
    project_code VARCHAR(100) NULL,
    store_code VARCHAR(100) NULL,
    site_code VARCHAR(20) NULL,
    immutable_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_payload_type VARCHAR(64) NULL,
    binding_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    binding_payload MEDIUMTEXT NULL,
    binding_effective_from_utc DATETIME(3) NULL,
    admission_anchor_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    binding_state VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    reconcile_after_utc DATETIME(3) NULL,
    schedule_after_utc DATETIME(3) NULL,
    schedule_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_code, epoch_no, scope_key),
    UNIQUE KEY uk_dp_schedule_scope_cursor (
        operation_code, epoch_no, source_cursor_sha256
    ),
    KEY idx_dp_schedule_scope_admission (
        operation_code, epoch_no, admission_anchor_state, scope_key
    ),
    KEY idx_dp_schedule_scope_binding (
        operation_code, epoch_no, binding_state, scope_key
    ),
    KEY idx_dp_schedule_scope_schedule (
        operation_code, epoch_no, schedule_state, scope_key
    ),
    CONSTRAINT chk_dp_schedule_scope_identity CHECK (
        owner_user_id > 0 AND (logical_store_id IS NULL OR logical_store_id > 0)
        AND CHAR_LENGTH(TRIM(source_cursor)) > 0
        AND CHAR_LENGTH(TRIM(scope_key)) > 0
        AND CHAR_LENGTH(TRIM(scope_namespace)) > 0
        AND CHAR_LENGTH(TRIM(account_key)) > 0
    ),
    CONSTRAINT chk_dp_schedule_scope_digest CHECK (
        source_cursor_sha256 REGEXP '^[0-9a-f]{64}$'
        AND immutable_payload_sha256 REGEXP '^[0-9a-f]{64}$'
        AND (binding_payload_sha256 IS NULL
             OR binding_payload_sha256 REGEXP '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_dp_schedule_scope_binding CHECK (
        (operation_code IN ('DP08A','DP08B')
         AND binding_payload_type IS NOT NULL
         AND binding_payload_sha256 IS NOT NULL
         AND binding_payload IS NOT NULL
         AND binding_effective_from_utc IS NOT NULL
         AND binding_state IN ('PENDING','COMPLETE'))
        OR
        (operation_code NOT IN ('DP08A','DP08B')
         AND binding_payload_type IS NULL
         AND binding_payload_sha256 IS NULL
         AND binding_payload IS NULL
         AND binding_effective_from_utc IS NULL
         AND binding_state = 'NOT_REQUIRED')
    ),
    CONSTRAINT chk_dp_schedule_scope_state CHECK (
        admission_anchor_state IN ('PENDING','COMPLETE')
        AND schedule_state IN ('PENDING','RUNNING','COMPLETE')
        AND (admission_anchor_state = 'PENDING' OR reconcile_after_utc IS NOT NULL)
    ),
    CONSTRAINT fk_dp_schedule_scope_epoch
        FOREIGN KEY (operation_code, epoch_no)
        REFERENCES dp_pull_schedule_source_epoch (operation_code, epoch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Retention contract (implemented by bounded keyset maintenance, not an unbounded DELETE):
-- * a terminal header is eligible when older than seven days OR older than the newest two;
-- * therefore keep at most two terminal epochs per operation and none beyond seven days;
-- * delete staged source scopes, then terminal epoch headers, in keyset batches of 64;
-- * epoch numbers come only from the durable per-operation sequence and are never reused;
-- * never delete an active_operation_slot or a VERIFYING/SEALED manifest seal.

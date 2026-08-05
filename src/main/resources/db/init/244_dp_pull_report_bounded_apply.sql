-- Migration 244: bounded, resumable DP01/02/03/07B report validation and apply.
SET @dp244_column_name_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dp_pull_report_artifact'
      AND column_name IN ('download_fence_epoch', 'downloaded_byte_count',
          'downloaded_chunk_count', 'resumable_sha256_state',
          'expected_content_length', 'source_validator')
);
SET @dp244_column_shape_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dp_pull_report_artifact'
      AND extra = '' AND generation_expression = '' AND (
        (column_name = 'download_fence_epoch' AND ordinal_position = 10
            AND LOWER(column_type) = 'bigint' AND is_nullable = 'NO'
            AND column_default = '0' AND character_set_name IS NULL)
        OR (column_name = 'downloaded_byte_count' AND ordinal_position = 11
            AND LOWER(column_type) = 'bigint' AND is_nullable = 'NO'
            AND column_default = '0' AND character_set_name IS NULL)
        OR (column_name = 'downloaded_chunk_count' AND ordinal_position = 12
            AND LOWER(column_type) = 'int' AND is_nullable = 'NO'
            AND column_default = '0' AND character_set_name IS NULL)
        OR (column_name = 'resumable_sha256_state' AND ordinal_position = 13
            AND LOWER(column_type) = 'varchar(220)' AND is_nullable = 'NO'
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
            AND column_default = 'v1:0:6a09e667bb67ae853c6ef372a54ff53a510e527f9b05688c1f83d9ab5be0cd19:')
        OR (column_name = 'expected_content_length' AND ordinal_position = 14
            AND LOWER(column_type) = 'bigint' AND is_nullable = 'YES'
            AND column_default IS NULL AND character_set_name IS NULL)
        OR (column_name = 'source_validator' AND ordinal_position = 15
            AND LOWER(column_type) = 'varchar(512)' AND is_nullable = 'YES'
            AND column_default IS NULL AND character_set_name = 'utf8mb4'
            AND collation_name = 'utf8mb4_bin')
      )
);
SET @dp244_storage_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'dp_pull_report_artifact'
      AND constraint_type = 'CHECK'
      AND constraint_name = 'chk_dp_report_artifact_storage_shape'
);
SET @dp244_progress_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'dp_pull_report_artifact'
      AND constraint_type = 'CHECK'
      AND constraint_name = 'chk_dp_report_artifact_download_progress'
);
SET @dp244_storage_clause := (
    SELECT REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REGEXP_REPLACE(LOWER(cc.check_clause), '[`[:space:]()]', ''), CONCAT(CHAR(92),CHAR(39)), CHAR(39)), '_utf8mb4', ''), 'octet_length', 'length'), 'ceiling', 'ceil'), ',', '')
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE() AND tc.table_name = 'dp_pull_report_artifact'
      AND tc.constraint_type = 'CHECK'
      AND tc.constraint_name = 'chk_dp_report_artifact_storage_shape'
);
SET @dp244_progress_clause := (
    SELECT REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REGEXP_REPLACE(LOWER(cc.check_clause), '[`[:space:]()]', ''), CONCAT(CHAR(92),CHAR(39)), CHAR(39)), '_utf8mb4', ''), 'octet_length', 'length'), 'ceiling', 'ceil'), 'character_length', 'char_length'), ',', '')
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE() AND tc.table_name = 'dp_pull_report_artifact'
      AND tc.constraint_type = 'CHECK'
      AND tc.constraint_name = 'chk_dp_report_artifact_download_progress'
);
SET @dp244_old_storage := 'download_state=''legacy_complete''andcontent_sha256isnotnullandcontent_length>0andcontent_bytesisnotnullandcontent_length=lengthcontent_bytesandpersisted_chunk_count=0ordownload_state=''downloading''andcontent_sha256isnullandcontent_length=0andcontent_bytesisnullandpersisted_chunk_count=0ordownload_state=''complete''andcontent_sha256isnotnullandcontent_length>0andcontent_bytesisnullandpersisted_chunk_count=ceilcontent_length/1048576';
SET @dp244_new_storage := 'download_state=''legacy_complete''andcontent_sha256isnotnullandcontent_length>0andcontent_bytesisnotnullandcontent_length=lengthcontent_bytesandpersisted_chunk_count=0anddownload_fence_epoch=0anddownloaded_byte_count=0anddownloaded_chunk_count=0andexpected_content_lengthisnullordownload_state=''downloading''andcontent_sha256isnullandcontent_length=0andcontent_bytesisnullandpersisted_chunk_count=0ordownload_state=''complete''andcontent_sha256isnotnullandcontent_length>0andcontent_bytesisnullandpersisted_chunk_count=ceilcontent_length/1048576anddownload_fence_epoch>0anddownloaded_byte_count=content_lengthanddownloaded_chunk_count=persisted_chunk_countandexpected_content_length=content_length';
SET @dp244_new_progress := 'download_fence_epoch>=0anddownloaded_byte_countbetween0and2251799812636672anddownloaded_chunk_countbetween0and2147483647anddownloaded_chunk_count=ceildownloaded_byte_count/1048576andregexp_likeresumable_sha256_state''^v1:[0-9]+:[0-9a-f]{64}:[0-9a-f]{0,126}$''andexpected_content_lengthisnullorexpected_content_lengthbetween1and2251799812636672anddownloaded_byte_count<=expected_content_lengthandsource_validatorisnullorchar_lengthtrimsource_validatorbetween1and512';
SET @dp244_all_absent := @dp244_column_name_count = 0
    AND @dp244_progress_check_count = 0 AND @dp244_storage_check_count = 1
    AND @dp244_storage_clause = @dp244_old_storage;
SET @dp244_all_present := @dp244_column_name_count = 6
    AND @dp244_column_shape_count = 6
    AND @dp244_progress_check_count = 1 AND @dp244_storage_check_count = 1
    AND @dp244_progress_clause = @dp244_new_progress
    AND @dp244_storage_clause = @dp244_new_storage;
DROP TEMPORARY TABLE IF EXISTS nuono_dp244_shape_guard;
CREATE TEMPORARY TABLE nuono_dp244_shape_guard (valid_shape BIT(1) NOT NULL, CONSTRAINT chk_dp244_shape_guard CHECK (valid_shape=b'1')) ENGINE=InnoDB;
INSERT INTO nuono_dp244_shape_guard VALUES (IF(@dp244_all_absent OR @dp244_all_present,b'1',b'0'));
DROP TEMPORARY TABLE nuono_dp244_shape_guard;
SET @dp244_ddl := IF(@dp244_all_absent,
  'ALTER TABLE dp_pull_report_artifact
    DROP CHECK chk_dp_report_artifact_storage_shape,
    ADD COLUMN download_fence_epoch BIGINT NOT NULL DEFAULT 0 AFTER persisted_chunk_count,
    ADD COLUMN downloaded_byte_count BIGINT NOT NULL DEFAULT 0 AFTER download_fence_epoch,
    ADD COLUMN downloaded_chunk_count INT NOT NULL DEFAULT 0 AFTER downloaded_byte_count,
    ADD COLUMN resumable_sha256_state VARCHAR(220)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT ''v1:0:6a09e667bb67ae853c6ef372a54ff53a510e527f9b05688c1f83d9ab5be0cd19:''
        AFTER downloaded_chunk_count,
    ADD COLUMN expected_content_length BIGINT NULL AFTER resumable_sha256_state,
    ADD COLUMN source_validator VARCHAR(512) NULL AFTER expected_content_length,
    ADD CONSTRAINT chk_dp_report_artifact_download_progress
        CHECK (download_fence_epoch >= 0
            AND downloaded_byte_count BETWEEN 0 AND 2251799812636672
            AND downloaded_chunk_count BETWEEN 0 AND 2147483647
            AND downloaded_chunk_count = CEIL(downloaded_byte_count / 1048576)
            AND resumable_sha256_state
                REGEXP ''^v1:[0-9]+:[0-9a-f]{64}:[0-9a-f]{0,126}$''
            AND (expected_content_length IS NULL
                OR (expected_content_length BETWEEN 1 AND 2251799812636672
                    AND downloaded_byte_count <= expected_content_length))
            AND (source_validator IS NULL
                OR CHAR_LENGTH(TRIM(source_validator)) BETWEEN 1 AND 512)),
    ADD CONSTRAINT chk_dp_report_artifact_storage_shape
        CHECK (
            (download_state = ''LEGACY_COMPLETE''
                AND content_sha256 IS NOT NULL
                AND content_length > 0
                AND content_bytes IS NOT NULL
                AND content_length = OCTET_LENGTH(content_bytes)
                AND persisted_chunk_count = 0
                AND download_fence_epoch = 0
                AND downloaded_byte_count = 0
                AND downloaded_chunk_count = 0
                AND expected_content_length IS NULL)
            OR (download_state = ''DOWNLOADING''
                AND content_sha256 IS NULL
                AND content_length = 0
                AND content_bytes IS NULL
                AND persisted_chunk_count = 0)
            OR (download_state = ''COMPLETE''
                AND content_sha256 IS NOT NULL
                AND content_length > 0
                AND content_bytes IS NULL
                AND persisted_chunk_count = CEIL(content_length / 1048576)
                AND download_fence_epoch > 0
                AND downloaded_byte_count = content_length
                AND downloaded_chunk_count = persisted_chunk_count
                AND expected_content_length = content_length)
        )',
  'DO 0');
PREPARE dp244_stmt FROM @dp244_ddl;
EXECUTE dp244_stmt;
DEALLOCATE PREPARE dp244_stmt;
CREATE TABLE IF NOT EXISTS dp_pull_report_artifact_chunk (
    artifact_key VARCHAR(96) NOT NULL,
    chunk_no INT NOT NULL,
    byte_offset BIGINT NOT NULL,
    content_length INT NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (artifact_key, chunk_no),
    UNIQUE KEY uk_dp_report_artifact_chunk_offset (artifact_key, byte_offset),
    CONSTRAINT chk_dp_report_artifact_chunk_number
        CHECK (chunk_no BETWEEN 0 AND 2147483646),
    CONSTRAINT chk_dp_report_artifact_chunk_offset
        CHECK (byte_offset = chunk_no * 1048576),
    CONSTRAINT chk_dp_report_artifact_chunk_length
        CHECK (content_length BETWEEN 1 AND 1048576
            AND content_length = OCTET_LENGTH(content_bytes)),
    CONSTRAINT chk_dp_report_artifact_chunk_digest
        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT fk_dp_report_artifact_chunk_manifest
        FOREIGN KEY (artifact_key)
        REFERENCES dp_pull_report_artifact (artifact_key)
        ON DELETE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_report_stage (
    task_id BIGINT NOT NULL,
    operation_code VARCHAR(16) NOT NULL,
    artifact_key VARCHAR(96) NOT NULL,
    artifact_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_fence_epoch BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    header_json MEDIUMTEXT NOT NULL,
    next_byte_offset BIGINT NOT NULL,
    declared_row_count BIGINT NOT NULL,
    source_row_count BIGINT NOT NULL DEFAULT 0,
    accepted_row_count BIGINT NOT NULL DEFAULT 0,
    business_skipped_row_count BIGINT NOT NULL DEFAULT 0,
    identity_skipped_row_count BIGINT NOT NULL DEFAULT 0,
    apply_row_cursor BIGINT NOT NULL DEFAULT 0,
    applied_row_count BIGINT NOT NULL DEFAULT 0,
    applied_warning_count BIGINT NOT NULL DEFAULT 0,
    fact_container_id BIGINT NULL,
    poison_code VARCHAR(80) NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    gmt_create DATETIME(3) NOT NULL,
    gmt_updated DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    KEY idx_dp_report_stage_artifact (artifact_key, task_id),
    KEY idx_dp_report_stage_retention (state, gmt_updated, task_id),
    CONSTRAINT chk_dp_report_stage_operation
        CHECK (operation_code IN ('DP01', 'DP02', 'DP03', 'DP07B')),
    CONSTRAINT chk_dp_report_stage_identity
        CHECK (CHAR_LENGTH(TRIM(artifact_key)) > 0
            AND artifact_sha256 REGEXP '^[0-9a-f]{64}$'
            AND active_fence_epoch > 0
            AND version_no >= 0),
    CONSTRAINT chk_dp_report_stage_header
        CHECK (OCTET_LENGTH(header_json) BETWEEN 2 AND 16711680
            AND JSON_VALID(header_json)
            AND JSON_TYPE(header_json) = 'ARRAY'),
    CONSTRAINT chk_dp_report_stage_counters
        CHECK (next_byte_offset >= 0
            AND declared_row_count >= 0
            AND source_row_count >= 0
            AND accepted_row_count >= 0
            AND business_skipped_row_count >= 0
            AND identity_skipped_row_count >= 0
            AND apply_row_cursor >= 0
            AND applied_row_count >= 0
            AND applied_warning_count >= 0
            AND source_row_count = accepted_row_count
                + business_skipped_row_count + identity_skipped_row_count
            AND source_row_count <= declared_row_count
            AND apply_row_cursor <= source_row_count
            AND applied_row_count <= accepted_row_count
            AND applied_warning_count <= applied_row_count),
    CONSTRAINT chk_dp_report_stage_state
        CHECK (state IN (
            'VALIDATING', 'SEALED', 'EMPTY_UNPROVEN', 'POISONED', 'APPLIED'
        )),
    CONSTRAINT chk_dp_report_stage_lifecycle
        CHECK (
            (state = 'VALIDATING'
                AND poison_code IS NULL
                AND apply_row_cursor = 0
                AND applied_row_count = 0
                AND applied_warning_count = 0)
            OR (state = 'SEALED'
                AND poison_code IS NULL
                AND source_row_count = declared_row_count
                AND source_row_count > 0)
            OR (state = 'EMPTY_UNPROVEN'
                AND poison_code IS NULL
                AND declared_row_count = 0
                AND source_row_count = 0
                AND apply_row_cursor = 0
                AND applied_row_count = 0
                AND applied_warning_count = 0)
            OR (state = 'POISONED'
                AND poison_code IS NOT NULL
                AND apply_row_cursor = 0
                AND applied_row_count = 0
                AND applied_warning_count = 0)
            OR (state = 'APPLIED'
                AND poison_code IS NULL
                AND source_row_count = declared_row_count
                AND applied_row_count = accepted_row_count)
        ),
    CONSTRAINT chk_dp_report_stage_poison
        CHECK (poison_code IS NULL
            OR poison_code REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{0,79}$'),
    CONSTRAINT chk_dp_report_stage_container
        CHECK (
            (operation_code <> 'DP07B' AND fact_container_id IS NULL)
            OR (operation_code = 'DP07B'
                AND (fact_container_id IS NULL OR fact_container_id > 0)
                AND (state <> 'APPLIED' OR accepted_row_count = 0
                    OR fact_container_id IS NOT NULL))
        ),
    CONSTRAINT fk_dp_report_stage_task
        FOREIGN KEY (task_id) REFERENCES dp_pull_task (id),
    CONSTRAINT fk_dp_report_stage_artifact
        FOREIGN KEY (artifact_key) REFERENCES dp_pull_report_artifact (artifact_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS dp_pull_report_stage_row (
    task_id BIGINT NOT NULL,
    `row_number` BIGINT NOT NULL,
    decision VARCHAR(32) NOT NULL,
    identity_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    accepted_identity_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    payload_json MEDIUMTEXT NULL,
    gmt_create DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, `row_number`),
    UNIQUE KEY uk_dp_report_stage_accepted_identity (
        task_id, accepted_identity_sha256
    ),
    KEY idx_dp_report_stage_row_apply (task_id, decision, `row_number`),
    CONSTRAINT chk_dp_report_stage_row_number CHECK (`row_number` > 0),
    CONSTRAINT chk_dp_report_stage_row_decision
        CHECK (decision IN ('ACCEPTED', 'BUSINESS_SKIP', 'LATER_IDENTITY_CONFLICT')),
    CONSTRAINT chk_dp_report_stage_row_shape
        CHECK (
            (decision = 'ACCEPTED'
                AND identity_sha256 IS NOT NULL
                AND identity_sha256 REGEXP '^[0-9a-f]{64}$'
                AND accepted_identity_sha256 IS NOT NULL
                AND accepted_identity_sha256 = identity_sha256
                AND payload_json IS NOT NULL
                AND OCTET_LENGTH(payload_json) BETWEEN 2 AND 16711680
                AND JSON_VALID(payload_json)
                AND JSON_TYPE(payload_json) = 'OBJECT')
            OR (decision = 'BUSINESS_SKIP'
                AND identity_sha256 IS NULL
                AND accepted_identity_sha256 IS NULL
                AND payload_json IS NULL)
            OR (decision = 'LATER_IDENTITY_CONFLICT'
                AND identity_sha256 IS NOT NULL
                AND identity_sha256 REGEXP '^[0-9a-f]{64}$'
                AND accepted_identity_sha256 IS NULL
                AND payload_json IS NULL)
        ),
    CONSTRAINT fk_dp_report_stage_row_stage
        FOREIGN KEY (task_id) REFERENCES dp_pull_report_stage (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

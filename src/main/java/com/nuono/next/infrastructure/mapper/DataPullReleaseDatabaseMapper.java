package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullReleaseDatabaseBinding;
import org.apache.ibatis.annotations.Select;

/** Computes stable schema/cutover identities without exposing business facts. */
public interface DataPullReleaseDatabaseMapper {
    @Select({
            "WITH required_migration AS (",
            "  SELECT '243_dp_pull_runtime.sql' migration_key UNION ALL",
            "  SELECT '244_dp_pull_report_bounded_apply.sql' UNION ALL",
            "  SELECT '245_dp_pull_snapshot_bounded_apply.sql' UNION ALL",
            "  SELECT '246_dp_pull_advertising_generation.sql' UNION ALL",
            "  SELECT '247_dp_pull_schedule_core.sql' UNION ALL",
            "  SELECT '248_dp_pull_dp08_member_retention.sql'",
            "), schema_binding AS (",
            "SELECT",
            "  COUNT(*) AS migration_count,",
            "  SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|', h.migration_key,",
            "    h.checksum_sha256, h.postcheck_sha256, h.state, h.attempt_no,",
            "    a.checksum_sha256, a.postcheck_sha256, a.state, a.attempt_no), 256)",
            "    ORDER BY BINARY h.migration_key SEPARATOR ''), 256) AS binding_sha256",
            "FROM required_migration required",
            "JOIN nuono_schema_migration h ON h.migration_key = required.migration_key",
            "JOIN nuono_schema_migration_attempt a",
            "  ON a.migration_key = h.migration_key AND a.attempt_no = h.attempt_no",
            "WHERE h.state = 'APPLIED' AND a.state = 'APPLIED'",
            ") SELECT",
            "  schema_binding.binding_sha256 AS schemaBindingSha256,",
            "  cutover.binding_sha256 AS cutoverBindingSha256,",
            "  cutover.operation_count AS cutoverOperationCount",
            "FROM schema_binding",
            "CROSS JOIN (",
            "  SELECT COUNT(*) AS operation_count,",
            "    SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|', operation_code,",
            "      HEX(cutover_key), expected_scope_count, anchor_manifest_sha256,",
            "      DATE_FORMAT(activated_at_utc, '%Y-%m-%dT%H:%i:%s.%f')), 256)",
            "      ORDER BY BINARY operation_code SEPARATOR ''), 256) AS binding_sha256",
            "  FROM dp_pull_schedule_cutover WHERE state = 'ACTIVE'",
            ") cutover",
            "WHERE schema_binding.migration_count = 6"
    })
    DataPullReleaseDatabaseBinding selectBinding();
}

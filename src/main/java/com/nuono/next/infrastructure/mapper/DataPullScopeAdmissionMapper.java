package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.DataPullScheduleCutover;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** SQL seam for immutable global DP scope admission and first-observation insertion. */
public interface DataPullScopeAdmissionMapper {

    String COLUMNS = "scope_key AS scopeKey, scope_namespace AS scopeNamespace, "
            + "owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, "
            + "account_key AS accountKey, egress_key AS egressKey, "
            + "project_code AS projectCode, store_code AS storeCode, site_code AS siteCode, "
            + "admission_kind AS admissionKind, first_eligible_at_utc AS firstEligibleAtUtc, "
            + "source_binding_sha256 AS sourceBindingSha256, cutover_key AS cutoverKey, "
            + "gmt_create AS admittedAtUtc";

    @Select({
            "SELECT operation_code AS operationCode, cutover_key AS cutoverKey, state,",
            "  expected_scope_count AS expectedScopeCount,",
            "  anchor_manifest_sha256 AS anchorManifestSha256,",
            "  activated_at_utc AS activatedAtUtc",
            "FROM dp_pull_schedule_cutover",
            "WHERE operation_code = #{operationCode} AND state = 'ACTIVE'",
            "LIMIT 1 FOR UPDATE"
    })
    DataPullScheduleCutover lockActiveCutover(
            @Param("operationCode") OperationCode operationCode
    );

    @Select("SELECT UTC_TIMESTAMP(3)")
    LocalDateTime selectDatabaseNowUtc();

    @Select({
            "<script>",
            "SELECT", COLUMNS,
            "FROM dp_pull_scope_admission",
            "WHERE BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            "  #{scopeKey}",
            "</foreach>",
            "ORDER BY BINARY scope_key ASC",
            "</script>"
    })
    List<DataPullScopeAdmission> listByScopeKeys(
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Select({
            "<script>",
            "SELECT", COLUMNS,
            "FROM dp_pull_scope_admission",
            "WHERE BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            "  #{scopeKey}",
            "</foreach>",
            "ORDER BY BINARY scope_key ASC FOR UPDATE",
            "</script>"
    })
    List<DataPullScopeAdmission> lockByScopeKeys(
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Insert({
            "INSERT INTO dp_pull_scope_admission (",
            "  scope_key, scope_namespace, owner_user_id, logical_store_id, account_key,",
            "  egress_key, project_code, store_code, site_code, admission_kind,",
            "  first_eligible_at_utc, source_binding_sha256, cutover_key, gmt_create",
            ") SELECT",
            "  #{admission.scopeKey}, #{admission.scopeNamespace}, #{admission.ownerUserId},",
            "  #{admission.logicalStoreId}, #{admission.accountKey}, #{admission.egressKey},",
            "  #{admission.projectCode}, #{admission.storeCode}, #{admission.siteCode},",
            "  'POST_CUTOVER', #{admission.firstEligibleAtUtc},",
            "  #{admission.sourceBindingSha256}, cutover.cutover_key, #{admission.admittedAtUtc}",
            "FROM dp_pull_schedule_cutover cutover",
            "WHERE cutover.operation_code = #{operationCode}",
            "  AND cutover.state = 'ACTIVE'",
            "  AND BINARY cutover.cutover_key = BINARY #{admission.cutoverKey}",
            "  AND cutover.activated_at_utc <= #{admission.firstEligibleAtUtc}",
            "ON DUPLICATE KEY UPDATE scope_key = scope_key"
    })
    int insertPostCutoverAdmission(
            @Param("operationCode") OperationCode operationCode,
            @Param("admission") DataPullScopeAdmission admission
    );
}

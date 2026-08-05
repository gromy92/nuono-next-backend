package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleCutover;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** SQL seam for immutable DP schedule anchors and the managed cutover seal. */
public interface DataPullScheduleAnchorMapper {

    String ANCHOR_COLUMNS = "anchor.operation_code AS operationCode, "
            + "anchor.scope_key AS scopeKey, anchor.cutover_key AS cutoverKey, "
            + "anchor.anchor_kind AS anchorKind, "
            + "anchor.reconcile_after_utc AS reconcileAfterUtc, "
            + "anchor.gmt_create AS createdAtUtc, "
            + "admission.admission_kind AS admissionKind, "
            + "admission.first_eligible_at_utc AS firstEligibleAtUtc, "
            + "admission.source_binding_sha256 AS sourceBindingSha256, "
            + "anchor.anchor_evidence_sha256 AS anchorEvidenceSha256";

    @Select({
            "SELECT operation_code AS operationCode, cutover_key AS cutoverKey, state,",
            "  expected_scope_count AS expectedScopeCount,",
            "  anchor_manifest_sha256 AS anchorManifestSha256,",
            "  activated_at_utc AS activatedAtUtc",
            "FROM dp_pull_schedule_cutover",
            "WHERE operation_code = #{operationCode}",
            "  AND state = 'ACTIVE'",
            "LIMIT 1"
    })
    DataPullScheduleCutover selectActiveCutover(
            @Param("operationCode") OperationCode operationCode
    );

    @Select({
            "SELECT", ANCHOR_COLUMNS,
            "FROM dp_pull_schedule_anchor anchor",
            "JOIN dp_pull_schedule_cutover cutover",
            "  ON cutover.operation_code = anchor.operation_code",
            " AND BINARY cutover.cutover_key = BINARY anchor.cutover_key",
            " AND cutover.state = 'ACTIVE'",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY anchor.scope_key",
            " AND BINARY admission.cutover_key = BINARY anchor.cutover_key",
            "WHERE anchor.operation_code = #{operationCode}",
            "  AND BINARY anchor.scope_key = BINARY #{scopeKey}",
            "LIMIT 1"
    })
    DataPullScheduleAnchor selectActiveAnchor(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey
    );

    @Select({
            "<script>",
            "SELECT", ANCHOR_COLUMNS,
            "FROM dp_pull_schedule_anchor anchor",
            "JOIN dp_pull_schedule_cutover cutover",
            "  ON cutover.operation_code = anchor.operation_code",
            " AND BINARY cutover.cutover_key = BINARY anchor.cutover_key",
            " AND cutover.state = 'ACTIVE'",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY anchor.scope_key",
            " AND BINARY admission.cutover_key = BINARY anchor.cutover_key",
            "WHERE anchor.operation_code = #{operationCode}",
            " AND BINARY anchor.scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            " #{scopeKey}",
            "</foreach>",
            "ORDER BY BINARY anchor.scope_key",
            "</script>"
    })
    List<DataPullScheduleAnchor> listActiveAnchorsByScopeKeys(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Select({
            "SELECT", ANCHOR_COLUMNS,
            "FROM dp_pull_schedule_anchor anchor",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY anchor.scope_key",
            " AND BINARY admission.cutover_key = BINARY anchor.cutover_key",
            "WHERE anchor.operation_code = #{operationCode}",
            "  AND BINARY anchor.cutover_key = BINARY #{cutoverKey}",
            "  AND anchor.anchor_kind = 'CUTOVER_RECONCILED'",
            "ORDER BY BINARY anchor.scope_key ASC"
    })
    List<DataPullScheduleAnchor> listCutoverAnchors(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey
    );

    @Select({
            "<script>",
            "SELECT", ANCHOR_COLUMNS,
            "FROM dp_pull_schedule_anchor anchor",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY anchor.scope_key",
            " AND BINARY admission.cutover_key = BINARY anchor.cutover_key",
            "WHERE anchor.operation_code = #{operationCode}",
            "  AND BINARY anchor.cutover_key = BINARY #{cutoverKey}",
            "  AND anchor.anchor_kind = 'CUTOVER_RECONCILED'",
            "<if test='afterScopeKey != null'>",
            "  AND BINARY anchor.scope_key &gt; BINARY #{afterScopeKey}",
            "</if>",
            "ORDER BY BINARY anchor.scope_key ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<DataPullScheduleAnchor> listCutoverAnchorsAfter(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey,
            @Param("afterScopeKey") String afterScopeKey,
            @Param("limit") int limit
    );

    @Select({
            "SELECT", ANCHOR_COLUMNS,
            "FROM dp_pull_schedule_anchor anchor",
            "JOIN dp_pull_schedule_cutover cutover",
            "  ON cutover.operation_code = anchor.operation_code",
            " AND BINARY cutover.cutover_key = BINARY anchor.cutover_key",
            " AND cutover.state = 'ACTIVE'",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY anchor.scope_key",
            " AND BINARY admission.cutover_key = BINARY anchor.cutover_key",
            "WHERE anchor.operation_code = #{operationCode}",
            "  AND BINARY anchor.cutover_key = BINARY #{cutoverKey}",
            "ORDER BY BINARY anchor.scope_key ASC"
    })
    List<DataPullScheduleAnchor> listActiveAnchors(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey
    );

    @Insert({
            "INSERT INTO dp_pull_schedule_anchor (",
            "  operation_code, scope_key, cutover_key, anchor_kind,",
            "  reconcile_after_utc, anchor_evidence_sha256, gmt_create",
            ")",
            "SELECT cutover.operation_code, #{scopeKey}, cutover.cutover_key,",
            "  'POST_CUTOVER_SCOPE', #{reconcileAfterUtc}, #{anchorEvidenceSha256},",
            "  admission.gmt_create",
            "FROM dp_pull_schedule_cutover cutover",
            "JOIN dp_pull_scope_admission admission",
            "  ON BINARY admission.scope_key = BINARY #{scopeKey}",
            " AND BINARY admission.cutover_key = BINARY cutover.cutover_key",
            " AND admission.admission_kind = 'POST_CUTOVER'",
            " AND admission.first_eligible_at_utc = #{firstEligibleAtUtc}",
            " AND admission.first_eligible_at_utc = #{reconcileAfterUtc}",
            " AND admission.source_binding_sha256 = #{sourceBindingSha256}",
            " AND admission.gmt_create = #{admittedAtUtc}",
            "WHERE cutover.operation_code = #{operationCode}",
            "  AND BINARY cutover.cutover_key = BINARY #{cutoverKey}",
            "  AND cutover.state = 'ACTIVE'",
            "  AND admission.first_eligible_at_utc >= cutover.activated_at_utc",
            "ON DUPLICATE KEY UPDATE operation_code = operation_code"
    })
    int insertPostCutoverAnchorIfActive(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey,
            @Param("cutoverKey") String cutoverKey,
            @Param("reconcileAfterUtc") LocalDateTime reconcileAfterUtc,
            @Param("anchorEvidenceSha256") String anchorEvidenceSha256,
            @Param("firstEligibleAtUtc") LocalDateTime firstEligibleAtUtc,
            @Param("sourceBindingSha256") String sourceBindingSha256,
            @Param("admittedAtUtc") LocalDateTime admittedAtUtc
    );
}

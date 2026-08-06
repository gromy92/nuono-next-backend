package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.ScheduleAnchorStageUpdate;
import com.nuono.next.datapull.schedule.ScheduleSourceStageRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Fixed-call batch SQL for consuming only a sealed source epoch. */
public interface DataPullScheduleApplyMapper {
    String STAGE_COLUMNS = "operation_code AS operationCode, epoch_no AS epochNo, "
            + "source_cursor AS sourceCursor, source_cursor_sha256 AS sourceCursorSha256, "
            + "scope_key AS scopeKey, scope_namespace AS scopeNamespace, "
            + "owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, "
            + "account_key AS accountKey, egress_key AS egressKey, "
            + "project_code AS projectCode, store_code AS storeCode, site_code AS siteCode, "
            + "immutable_payload_sha256 AS immutablePayloadSha256, "
            + "binding_payload_type AS bindingPayloadType, "
            + "binding_payload_sha256 AS bindingPayloadSha256, "
            + "binding_payload AS bindingPayload, "
            + "binding_effective_from_utc AS bindingEffectiveFromUtc, "
            + "admission_anchor_state AS admissionAnchorState, binding_state AS bindingState, "
            + "reconcile_after_utc AS reconcileAfterUtc, schedule_after_utc AS scheduleAfterUtc, "
            + "schedule_state AS scheduleState";

    @Select({
            "<script>",
            "SELECT", STAGE_COLUMNS, "FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND admission_anchor_state = 'PENDING'",
            "<if test='afterScopeKey != null'>",
            " AND BINARY scope_key &gt; BINARY #{afterScopeKey}",
            "</if>",
            "ORDER BY BINARY scope_key LIMIT #{limit}",
            "</script>"
    })
    List<ScheduleSourceStageRow> listAdmissionStageAfter(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("afterScopeKey") String afterScopeKey,
            @Param("limit") int limit
    );

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_scope_admission (",
            " scope_key, scope_namespace, owner_user_id, logical_store_id, account_key,",
            " egress_key, project_code, store_code, site_code, admission_kind,",
            " first_eligible_at_utc, source_binding_sha256, cutover_key, gmt_create)",
            "SELECT candidate.scope_key, candidate.scope_namespace, candidate.owner_user_id,",
            " candidate.logical_store_id, candidate.account_key, candidate.egress_key,",
            " candidate.project_code, candidate.store_code, candidate.site_code,",
            " 'POST_CUTOVER', #{observedAtUtc}, candidate.source_binding_sha256,",
            " cutover.cutover_key, #{observedAtUtc}",
            "FROM (",
            "<foreach collection='rows' item='row' separator=' UNION ALL '>",
            " SELECT #{row.scopeKey} scope_key, #{row.scopeNamespace} scope_namespace,",
            " #{row.ownerUserId} owner_user_id, #{row.logicalStoreId} logical_store_id,",
            " #{row.accountKey} account_key, #{row.egressKey} egress_key,",
            " #{row.projectCode} project_code, #{row.storeCode} store_code,",
            " #{row.siteCode} site_code, #{row.sourceBindingSha256} source_binding_sha256",
            "</foreach>",
            ") candidate",
            "JOIN dp_pull_schedule_cutover cutover ON cutover.operation_code = #{operationCode}",
            " AND cutover.state = 'ACTIVE' AND BINARY cutover.cutover_key = BINARY #{cutoverKey}",
            " AND cutover.activated_at_utc &lt;= #{observedAtUtc}",
            "ON DUPLICATE KEY UPDATE scope_key = scope_key",
            "</script>"
    })
    int insertPostCutoverAdmissions(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey,
            @Param("observedAtUtc") LocalDateTime observedAtUtc,
            @Param("rows") List<ScheduleSourceStageRow> rows
    );

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_schedule_anchor (",
            " operation_code, scope_key, cutover_key, anchor_kind, reconcile_after_utc,",
            " anchor_evidence_sha256, gmt_create) VALUES",
            "<foreach collection='anchors' item='anchor' separator=','>",
            "(#{anchor.operationCode},#{anchor.scopeKey},#{anchor.cutoverKey},",
            " #{anchor.anchorKind},#{anchor.reconcileAfterUtc},",
            " #{anchor.anchorEvidenceSha256},#{anchor.createdAtUtc})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE operation_code = operation_code",
            "</script>"
    })
    int insertPostCutoverAnchors(
            @Param("anchors") List<DataPullScheduleAnchor> anchors
    );

    @Update({
            "<script>",
            "UPDATE dp_pull_schedule_source_scope",
            "SET reconcile_after_utc = CASE BINARY scope_key",
            "<foreach collection='updates' item='item'>",
            " WHEN BINARY #{item.scopeKey} THEN #{item.reconcileAfterUtc}",
            "</foreach>",
            " ELSE reconcile_after_utc END, admission_anchor_state = 'COMPLETE',",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND admission_anchor_state = 'PENDING' AND BINARY scope_key IN",
            "<foreach collection='updates' item='item' open='(' separator=',' close=')'>",
            " #{item.scopeKey}",
            "</foreach>",
            "</script>"
    })
    int completeAdmissionStage(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("updates") List<ScheduleAnchorStageUpdate> updates
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET admission_cursor_scope_key = #{nextCursor}, epoch_state = #{nextState},",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state IN ('SEALED','ADMITTING') AND version_no = #{expectedVersion}",
            " AND (admission_cursor_scope_key &lt;=&gt; #{expectedCursor})"
    })
    int advanceAdmissionPhase(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("nextCursor") String nextCursor,
            @Param("nextState") String nextState
    );

    @Select({
            "<script>",
            "SELECT", STAGE_COLUMNS, "FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND binding_state = 'PENDING'",
            "<if test='afterScopeKey != null'>",
            " AND BINARY scope_key &gt; BINARY #{afterScopeKey}",
            "</if>",
            "ORDER BY BINARY scope_key LIMIT #{limit}",
            "</script>"
    })
    List<ScheduleSourceStageRow> listBindingStageAfter(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("afterScopeKey") String afterScopeKey,
            @Param("limit") int limit
    );

    @Update({
            "<script>",
            "UPDATE dp_pull_schedule_source_scope",
            "SET binding_state = 'COMPLETE', gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND binding_state = 'PENDING' AND BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            " #{scopeKey}",
            "</foreach>",
            "</script>"
    })
    int completeBindingStage(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET binding_cursor_scope_key = #{nextCursor}, epoch_state = #{nextState},",
            " binding_close_state = CASE WHEN #{nextState} = 'BINDING_MISSING'",
            "                            THEN 'RUNNING' ELSE binding_close_state END,",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state = 'BINDING_PRESENT' AND version_no = #{expectedVersion}",
            " AND (binding_cursor_scope_key &lt;=&gt; #{expectedCursor})"
    })
    int advanceBindingPresentPhase(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("nextCursor") String nextCursor,
            @Param("nextState") String nextState
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET missing_binding_cursor_scope_key = #{nextCursor}, epoch_state = #{nextState},",
            " binding_close_state = CASE WHEN #{nextState} = 'SCHEDULING'",
            "                            THEN 'COMPLETE' ELSE 'RUNNING' END,",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state = 'BINDING_MISSING' AND binding_close_state = 'RUNNING'",
            " AND version_no = #{expectedVersion}",
            " AND (missing_binding_cursor_scope_key &lt;=&gt; #{expectedCursor})"
    })
    int advanceBindingMissingPhase(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("nextCursor") String nextCursor,
            @Param("nextState") String nextState
    );

}

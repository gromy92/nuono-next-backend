package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleEpochSequenceRow;
import com.nuono.next.datapull.schedule.ScheduleManifestSealRow;
import com.nuono.next.datapull.schedule.ScheduleRotationRow;
import com.nuono.next.datapull.schedule.ScheduleSourceEpochRow;
import com.nuono.next.datapull.schedule.ScheduleSourceStageRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SQL seam for bounded schedule rotation, source epochs and cutover-manifest seals. */
public interface DataPullScheduleScanMapper {
    String EPOCH_COLUMNS = "operation_code AS operationCode, epoch_no AS epochNo, "
            + "cutover_key AS cutoverKey, "
            + "epoch_state AS epochState, reconcile_until_utc AS reconcileUntilUtc, "
            + "pass_one_cursor AS passOneCursor, pass_one_scope_count AS passOneScopeCount, "
            + "pass_one_ordered_sha256 AS passOneOrderedSha256, "
            + "pass_two_cursor AS passTwoCursor, pass_two_scope_count AS passTwoScopeCount, "
            + "pass_two_ordered_sha256 AS passTwoOrderedSha256, "
            + "admission_cursor_scope_key AS admissionCursorScopeKey, "
            + "binding_cursor_scope_key AS bindingCursorScopeKey, "
            + "missing_binding_cursor_scope_key AS missingBindingCursorScopeKey, "
            + "schedule_cursor_scope_key AS scheduleCursorScopeKey, "
            + "binding_close_state AS bindingCloseState, version_no AS version, "
            + "sealed_at_utc AS sealedAtUtc, terminal_at_utc AS terminalAtUtc";

    @Select({
            "SELECT next_operation_ordinal AS nextOperationOrdinal, version_no AS version",
            "FROM dp_pull_schedule_rotation WHERE runtime_name = 'daily_pull' FOR UPDATE"
    })
    ScheduleRotationRow lockRotation();

    @Update({
            "UPDATE dp_pull_schedule_rotation",
            "SET next_operation_ordinal = #{nextOrdinal}, version_no = version_no + 1,",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE runtime_name = 'daily_pull' AND version_no = #{expectedVersion}"
    })
    int advanceRotation(
            @Param("nextOrdinal") int nextOrdinal,
            @Param("expectedVersion") long expectedVersion
    );

    @Select({
            "SELECT", EPOCH_COLUMNS, "FROM dp_pull_schedule_source_epoch",
            "WHERE operation_code = #{operationCode} AND active_operation_slot = #{operationCode}",
            "LIMIT 1 FOR UPDATE"
    })
    ScheduleSourceEpochRow lockActiveEpoch(@Param("operationCode") OperationCode operationCode);

    @Select({
            "SELECT", EPOCH_COLUMNS, "FROM dp_pull_schedule_source_epoch",
            "WHERE operation_code = #{operationCode}",
            "ORDER BY epoch_no DESC LIMIT 1 FOR UPDATE"
    })
    ScheduleSourceEpochRow lockLatestEpoch(@Param("operationCode") OperationCode operationCode);

    @Select({
            "SELECT last_epoch_no AS lastEpochNo, version_no AS version",
            "FROM dp_pull_schedule_epoch_sequence",
            "WHERE operation_code = #{operationCode} FOR UPDATE"
    })
    ScheduleEpochSequenceRow lockEpochSequence(
            @Param("operationCode") OperationCode operationCode
    );

    @Update({
            "UPDATE dp_pull_schedule_epoch_sequence",
            "SET last_epoch_no = #{nextEpochNo}, version_no = version_no + 1,",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode}",
            " AND last_epoch_no = #{expectedEpochNo} AND version_no = #{expectedVersion}"
    })
    int advanceEpochSequence(
            @Param("operationCode") OperationCode operationCode,
            @Param("expectedEpochNo") long expectedEpochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextEpochNo") long nextEpochNo
    );

    @Insert({
            "INSERT INTO dp_pull_schedule_source_epoch (",
            " operation_code, epoch_no, cutover_key, active_operation_slot, epoch_state,",
            " reconcile_until_utc,",
            " pass_one_scope_count, pass_one_ordered_sha256,",
            " pass_two_scope_count, pass_two_ordered_sha256, binding_close_state,",
            " version_no, gmt_create, gmt_updated",
            ") VALUES (",
            " #{operationCode}, #{epochNo}, #{cutoverKey}, #{operationCode}, 'PASS_ONE',",
            " #{reconcileUntilUtc},",
            " 0, #{initialDigest}, 0, #{initialDigest}, #{bindingCloseState},",
            " 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
    })
    int insertEpoch(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("cutoverKey") String cutoverKey,
            @Param("reconcileUntilUtc") LocalDateTime reconcileUntilUtc,
            @Param("initialDigest") String initialDigest,
            @Param("bindingCloseState") String bindingCloseState
    );

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_schedule_source_scope (",
            " operation_code, epoch_no, source_cursor, source_cursor_sha256, scope_key,",
            " scope_namespace, owner_user_id, logical_store_id, account_key, egress_key,",
            " project_code, store_code, site_code, immutable_payload_sha256,",
            " binding_payload_type, binding_payload_sha256, binding_payload,",
            " binding_effective_from_utc, admission_anchor_state, binding_state,",
            " schedule_state, gmt_create, gmt_updated) VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            "(#{row.operationCode},#{row.epochNo},#{row.sourceCursor},#{row.sourceCursorSha256},",
            " #{row.scopeKey},#{row.scopeNamespace},#{row.ownerUserId},#{row.logicalStoreId},",
            " #{row.accountKey},#{row.egressKey},#{row.projectCode},#{row.storeCode},",
            " #{row.siteCode},#{row.immutablePayloadSha256},#{row.bindingPayloadType},",
            " #{row.bindingPayloadSha256},#{row.bindingPayload},#{row.bindingEffectiveFromUtc},",
            " 'PENDING',#{row.bindingState},'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "</foreach>",
            "</script>"
    })
    int insertStageRows(@Param("rows") List<ScheduleSourceStageRow> rows);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND (BINARY scope_key IN",
            "<foreach collection='rows' item='row' open='(' separator=',' close=')'>",
            " #{row.scopeKey}",
            "</foreach>",
            " OR source_cursor_sha256 IN",
            "<foreach collection='rows' item='row' open='(' separator=',' close=')'>",
            " #{row.sourceCursorSha256}",
            "</foreach>)",
            "</script>"
    })
    int countStageConflicts(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("rows") List<ScheduleSourceStageRow> rows
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET pass_one_cursor = #{nextCursor}, pass_one_scope_count = #{nextCount},",
            " pass_one_ordered_sha256 = #{nextDigest}, epoch_state = #{nextState},",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state = 'PASS_ONE' AND version_no = #{expectedVersion}",
            " AND pass_one_scope_count = #{expectedCount}",
            " AND pass_one_ordered_sha256 = #{expectedDigest}",
            " AND (pass_one_cursor &lt;=&gt; #{expectedCursor})"
    })
    int advancePassOne(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("expectedCount") long expectedCount,
            @Param("expectedDigest") String expectedDigest,
            @Param("nextCursor") String nextCursor,
            @Param("nextCount") long nextCount,
            @Param("nextDigest") String nextDigest,
            @Param("nextState") String nextState
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET pass_two_cursor = #{nextCursor}, pass_two_scope_count = #{nextCount},",
            " pass_two_ordered_sha256 = #{nextDigest}, epoch_state = #{nextState},",
            " active_operation_slot = CASE WHEN #{nextState} = 'ABORTED' THEN NULL",
            "                              ELSE active_operation_slot END,",
            " sealed_at_utc = CASE WHEN #{nextState} = 'SEALED' THEN UTC_TIMESTAMP(3)",
            "                           ELSE sealed_at_utc END,",
            " terminal_at_utc = CASE WHEN #{nextState} = 'ABORTED' THEN UTC_TIMESTAMP(3)",
            "                            ELSE terminal_at_utc END,",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state = 'PASS_TWO' AND version_no = #{expectedVersion}",
            " AND pass_two_scope_count = #{expectedCount}",
            " AND pass_two_ordered_sha256 = #{expectedDigest}",
            " AND (pass_two_cursor &lt;=&gt; #{expectedCursor})"
    })
    int advancePassTwo(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("expectedCount") long expectedCount,
            @Param("expectedDigest") String expectedDigest,
            @Param("nextCursor") String nextCursor,
            @Param("nextCount") long nextCount,
            @Param("nextDigest") String nextDigest,
            @Param("nextState") String nextState
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET epoch_state = 'ABORTED', active_operation_slot = NULL,",
            " terminal_at_utc = UTC_TIMESTAMP(3),",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND active_operation_slot = #{operationCode} AND version_no = #{expectedVersion}"
    })
    int abortEpoch(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion
    );

    @Select({
            "SELECT operation_code AS operationCode, cutover_key AS cutoverKey,",
            " expected_scope_count AS expectedScopeCount,",
            " expected_manifest_sha256 AS expectedManifestSha256, seal_state AS sealState,",
            " next_scope_key AS nextScopeKey, scanned_scope_count AS scannedScopeCount,",
            " resumable_sha256_state AS resumableSha256State,",
            " verified_manifest_sha256 AS verifiedManifestSha256,",
            " version_no AS version, sealed_at_utc AS sealedAtUtc",
            "FROM dp_pull_schedule_manifest_seal",
            "WHERE operation_code = #{operationCode} AND cutover_key = #{cutoverKey}",
            "LIMIT 1 FOR UPDATE"
    })
    ScheduleManifestSealRow lockManifestSeal(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey
    );

    @Insert({
            "INSERT INTO dp_pull_schedule_manifest_seal (",
            " operation_code, cutover_key, expected_scope_count, expected_manifest_sha256,",
            " seal_state, scanned_scope_count, resumable_sha256_state, version_no,",
            " gmt_create, gmt_updated) VALUES (",
            " #{operationCode},#{cutoverKey},#{expectedCount},#{expectedDigest},",
            " 'VERIFYING',0,#{initialState},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "ON DUPLICATE KEY UPDATE operation_code = operation_code"
    })
    int insertManifestSeal(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey,
            @Param("expectedCount") int expectedCount,
            @Param("expectedDigest") String expectedDigest,
            @Param("initialState") String initialState
    );

    @Update({
            "UPDATE dp_pull_schedule_manifest_seal",
            "SET next_scope_key = #{nextScopeKey}, scanned_scope_count = #{nextCount},",
            " resumable_sha256_state = #{nextState}, seal_state = #{sealState},",
            " verified_manifest_sha256 = #{verifiedDigest},",
            " sealed_at_utc = CASE WHEN #{sealState} = 'SEALED' THEN UTC_TIMESTAMP(3) ELSE NULL END,",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND cutover_key = #{cutoverKey}",
            " AND seal_state = 'VERIFYING' AND version_no = #{expectedVersion}",
            " AND scanned_scope_count = #{expectedCount}",
            " AND (next_scope_key &lt;=&gt; #{expectedScopeKey})"
    })
    int advanceManifestSeal(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoverKey") String cutoverKey,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCount") int expectedCount,
            @Param("expectedScopeKey") String expectedScopeKey,
            @Param("nextScopeKey") String nextScopeKey,
            @Param("nextCount") int nextCount,
            @Param("nextState") String nextState,
            @Param("sealState") String sealState,
            @Param("verifiedDigest") String verifiedDigest
    );

}

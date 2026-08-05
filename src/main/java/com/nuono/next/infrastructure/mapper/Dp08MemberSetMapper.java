package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetItem;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetRecord;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberStageHead;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberStageItem;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskMemberProgress;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Bounded SQL seam for nested source staging and immutable DP08 member sets. */
public interface Dp08MemberSetMapper {
    String HEAD_COLUMNS = "operation_code AS operationCode, epoch_no AS epochNo, "
            + "scan_pass AS scanPass, scope_key AS scopeKey, source_cursor AS sourceCursor, "
            + "member_count AS memberCount, member_ordered_sha256 AS memberOrderedSha256, "
            + "base_payload AS basePayload, effective_from_utc AS effectiveFromUtc, "
            + "stage_state AS stageState, member_set_id AS memberSetId, version_no AS version";
    String SET_COLUMNS = "member_set_id AS memberSetId, operation_code AS operationCode, "
            + "scope_key AS scopeKey, member_count AS memberCount, "
            + "member_ordered_sha256 AS memberOrderedSha256, handle_payload_type AS handlePayloadType, "
            + "handle_payload_sha256 AS handlePayloadSha256, handle_payload AS handlePayload, "
            + "effective_from_utc AS effectiveFromUtc, set_state AS setState, "
            + "copy_cursor AS copyCursor, copied_member_count AS copiedMemberCount, version_no AS version";
    String ITEM_COLUMNS = "member_set_id AS memberSetId, member_key AS memberKey, "
            + "member_kind AS memberKind, watch_product_id AS watchProductId, "
            + "competitor_product_id AS competitorProductId, noon_product_code AS noonProductCode, "
            + "source_updated_at_utc AS sourceUpdatedAtUtc";

    @Select({"SELECT",HEAD_COLUMNS,"FROM dp_pull_schedule_dp08_member_stage_head",
            "WHERE operation_code=#{operationCode} AND epoch_no=#{epochNo}",
            "AND scan_pass=#{scanPass} AND BINARY scope_key=BINARY #{scopeKey}","LIMIT 1 FOR UPDATE"})
    Dp08MemberStageHead lockStageHead(@Param("operationCode") OperationCode operation,
            @Param("epochNo") long epochNo,@Param("scanPass") int pass,@Param("scopeKey") String scopeKey);

    @Insert({"INSERT INTO dp_pull_schedule_dp08_member_stage_head (operation_code,epoch_no,scan_pass,scope_key,",
            "source_cursor,member_count,member_ordered_sha256,base_payload,effective_from_utc,stage_state,",
            "member_set_id,version_no,gmt_create,gmt_updated) VALUES (#{operationCode},#{epochNo},#{scanPass},",
            "#{scopeKey},#{sourceCursor},#{memberCount},#{memberOrderedSha256},#{basePayload},#{effectiveFromUtc},",
            "#{stageState},#{memberSetId},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))"})
    int insertStageHead(Dp08MemberStageHead head);

    @Update({"UPDATE dp_pull_schedule_dp08_member_stage_head SET source_cursor=#{nextCursor},",
            "member_count=#{nextCount},member_ordered_sha256=#{nextDigest},effective_from_utc=#{nextEffective},",
            "stage_state=#{nextState},member_set_id=#{memberSetId},version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE operation_code=#{operationCode} AND epoch_no=#{epochNo} AND scan_pass=#{scanPass}",
            "AND BINARY scope_key=BINARY #{scopeKey} AND version_no=#{expectedVersion}",
            "AND (source_cursor <=> #{expectedCursor}) AND member_count=#{expectedCount}",
            "AND BINARY member_ordered_sha256=BINARY #{expectedDigest}"})
    int advanceStageHead(@Param("operationCode") OperationCode operation,@Param("epochNo") long epochNo,
            @Param("scanPass") int pass,@Param("scopeKey") String scopeKey,@Param("expectedVersion") long version,
            @Param("expectedCursor") String cursor,@Param("expectedCount") long count,@Param("expectedDigest") String digest,
            @Param("nextCursor") String nextCursor,@Param("nextCount") long nextCount,@Param("nextDigest") String nextDigest,
            @Param("nextEffective") LocalDateTime nextEffective,@Param("nextState") String state,
            @Param("memberSetId") String memberSetId);

    @Insert({"<script>","INSERT INTO dp_pull_schedule_dp08_member_stage_item (operation_code,epoch_no,scan_pass,scope_key,",
            "member_key,member_kind,watch_product_id,competitor_product_id,noon_product_code,source_updated_at_utc,",
            "gmt_create) VALUES","<foreach collection='items' item='i' separator=','>",
            "(#{i.operationCode},#{i.epochNo},#{i.scanPass},#{i.scopeKey},#{i.memberKey},#{i.memberKind},",
            "#{i.watchProductId},#{i.competitorProductId},#{i.noonProductCode},#{i.sourceUpdatedAtUtc},UTC_TIMESTAMP(3))",
            "</foreach>","</script>"})
    int insertStageItems(@Param("items") List<Dp08MemberStageItem> items);

    @Select({"SELECT NULL AS memberSetId, member_key AS memberKey,member_kind AS memberKind,",
            "watch_product_id AS watchProductId,competitor_product_id AS competitorProductId,",
            "noon_product_code AS noonProductCode,source_updated_at_utc AS sourceUpdatedAtUtc",
            "FROM dp_pull_schedule_dp08_member_stage_item WHERE operation_code=#{operationCode}",
            "AND epoch_no=#{epochNo} AND scan_pass=#{scanPass} AND BINARY scope_key=BINARY #{scopeKey}",
            "AND (#{afterMemberKey} IS NULL OR BINARY member_key>BINARY #{afterMemberKey})",
            "ORDER BY BINARY member_key LIMIT #{limit}"})
    List<Dp08MemberSetItem> listStageItemsAfter(@Param("operationCode") OperationCode operation,
            @Param("epochNo") long epochNo,@Param("scanPass") int pass,@Param("scopeKey") String scopeKey,
            @Param("afterMemberKey") String after,@Param("limit") int limit);

    @Select({"SELECT",SET_COLUMNS,"FROM dp_pull_dp08_member_set WHERE BINARY member_set_id=BINARY #{id}",
            "LIMIT 1 FOR UPDATE"}) Dp08MemberSetRecord lockMemberSet(@Param("id") String id);

    @Insert({"INSERT INTO dp_pull_dp08_member_set (member_set_id,operation_code,scope_key,member_count,",
            "member_ordered_sha256,handle_payload_type,handle_payload_sha256,handle_payload,effective_from_utc,",
            "set_state,copy_cursor,copied_member_count,version_no,gmt_create,gmt_updated) VALUES (#{memberSetId},",
            "#{operationCode},#{scopeKey},#{memberCount},#{memberOrderedSha256},#{handlePayloadType},",
            "#{handlePayloadSha256},#{handlePayload},#{effectiveFromUtc},'BUILDING',NULL,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "ON DUPLICATE KEY UPDATE member_set_id=member_set_id"})
    int insertMemberSet(Dp08MemberSetRecord record);

    @Insert({"<script>","INSERT INTO dp_pull_dp08_member_set_item (member_set_id,member_key,member_kind,",
            "watch_product_id,competitor_product_id,noon_product_code,source_updated_at_utc,gmt_create) VALUES",
            "<foreach collection='items' item='i' separator=','>","(#{i.memberSetId},#{i.memberKey},#{i.memberKind},",
            "#{i.watchProductId},#{i.competitorProductId},#{i.noonProductCode},#{i.sourceUpdatedAtUtc},UTC_TIMESTAMP(3))",
            "</foreach>","ON DUPLICATE KEY UPDATE member_key=member_key","</script>"})
    int insertMemberItems(@Param("items") List<Dp08MemberSetItem> items);

    @Update({"UPDATE dp_pull_dp08_member_set SET copy_cursor=#{nextCursor},copied_member_count=#{nextCount},",
            "set_state=CASE WHEN #{nextCount}=member_count THEN 'SEALED' ELSE 'BUILDING' END,",
            "version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) WHERE BINARY member_set_id=BINARY #{id}",
            "AND set_state='BUILDING' AND version_no=#{version} AND (copy_cursor <=> #{expectedCursor})",
            "AND copied_member_count=#{expectedCount} AND #{nextCount}<=member_count"})
    int advanceMemberSet(@Param("id") String id,@Param("version") long version,
            @Param("expectedCursor") String cursor,@Param("expectedCount") long count,
            @Param("nextCursor") String nextCursor,@Param("nextCount") long nextCount);

    @Select({"SELECT",ITEM_COLUMNS,"FROM dp_pull_dp08_member_set_item",
            "WHERE BINARY member_set_id=BINARY #{memberSetId}",
            "AND (#{afterMemberKey} IS NULL OR BINARY member_key>BINARY #{afterMemberKey})",
            "ORDER BY BINARY member_key LIMIT #{limit}"})
    List<Dp08MemberSetItem> listMemberItemsAfter(@Param("memberSetId") String id,
            @Param("afterMemberKey") String after,@Param("limit") int limit);

    @Insert({"INSERT INTO dp_pull_dp08_task_member_progress (task_id,operation_code,member_set_id,",
            "evidence_member_count,evidence_complete,exact_search_required,applied_member_count,",
            "apply_complete,rank_fact_count,version_no,gmt_create,gmt_updated) VALUES (#{taskId},",
            "#{operationCode},#{memberSetId},0,CASE WHEN #{operationCode}='DP08A' THEN b'1' ELSE b'0' END,",
            "b'0',0,b'0',0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE task_id=task_id"})
    int insertTaskProgress(@Param("taskId") long taskId,@Param("operationCode") OperationCode operation,
            @Param("memberSetId") String memberSetId);

    @Select({"SELECT task_id AS taskId,operation_code AS operationCode,member_set_id AS memberSetId,",
            "evidence_cursor AS evidenceCursor,evidence_member_count AS evidenceMemberCount,",
            "evidence_complete AS evidenceComplete,exact_search_required AS exactSearchRequired,",
            "apply_cursor AS applyCursor,applied_member_count AS appliedMemberCount,apply_complete AS applyComplete,",
            "search_run_id AS searchRunId,keyword_run_id AS keywordRunId,rank_fact_count AS rankFactCount,",
            "version_no AS version FROM dp_pull_dp08_task_member_progress WHERE task_id=#{taskId} LIMIT 1 FOR UPDATE"})
    Dp08TaskMemberProgress lockTaskProgress(@Param("taskId") long taskId);

    @Update({"UPDATE dp_pull_dp08_task_member_progress SET evidence_cursor=#{nextCursor},",
            "evidence_member_count=#{nextCount},exact_search_required=#{required},evidence_complete=#{complete},",
            "version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) WHERE task_id=#{taskId}",
            "AND operation_code='DP08B' AND version_no=#{version} AND (evidence_cursor <=> #{expectedCursor})",
            "AND evidence_member_count=#{expectedCount} AND evidence_complete=b'0'"})
    int advanceTaskEvidence(@Param("taskId") long taskId,@Param("version") long version,
            @Param("expectedCursor") String expectedCursor,@Param("expectedCount") long expectedCount,
            @Param("nextCursor") String nextCursor,@Param("nextCount") long nextCount,
            @Param("required") boolean required,@Param("complete") boolean complete);

    @Update({"UPDATE dp_pull_dp08_task_member_progress SET apply_cursor=#{nextCursor},",
            "applied_member_count=#{nextCount},apply_complete=#{complete},rank_fact_count=#{rankFactCount},",
            "search_run_id=COALESCE(search_run_id,#{searchRunId}),keyword_run_id=COALESCE(keyword_run_id,#{keywordRunId}),",
            "version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) WHERE task_id=#{taskId}",
            "AND operation_code=#{operationCode} AND version_no=#{version} AND (apply_cursor <=> #{expectedCursor})",
            "AND applied_member_count=#{expectedCount} AND apply_complete=b'0'"})
    int advanceTaskApply(@Param("taskId") long taskId,@Param("operationCode") OperationCode operation,
            @Param("version") long version,@Param("expectedCursor") String expectedCursor,
            @Param("expectedCount") long expectedCount,@Param("nextCursor") String nextCursor,
            @Param("nextCount") long nextCount,@Param("complete") boolean complete,
            @Param("rankFactCount") int rankFactCount,@Param("searchRunId") Long searchRunId,
            @Param("keywordRunId") Long keywordRunId);
}

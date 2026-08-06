package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleLatestSlotRow;
import com.nuono.next.datapull.schedule.ScheduleSourceStageRow;
import com.nuono.next.datapull.schedule.ScheduleStageProgressUpdate;
import com.nuono.next.datapull.schedule.ScheduleTaskBindingRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Fixed-call SQL for one bounded schedule/task planning step. */
public interface DataPullScheduleTaskPlanMapper {
    @Select({
            "<script>",
            "SELECT request.scopeKey, request.scheduleSlot, binding.binding_id AS bindingId,",
            " binding.payload_type AS payloadType, binding.payload_sha256 AS payloadSha256,",
            " binding.payload, binding.effective_from_utc AS effectiveFromUtc",
            "FROM (",
            "<foreach collection='tasks' item='task' separator=' UNION ALL '>",
            " SELECT #{task.scopeKey} scopeKey, #{task.scheduleSlot} scheduleSlot,",
            "  #{task.operationCode} operationCode",
            "</foreach>",
            ") request",
            "JOIN dp_pull_scope_binding_epoch binding",
            " ON binding.operation_code = request.operationCode",
            " AND BINARY binding.scope_key = BINARY request.scopeKey",
            " AND binding.effective_from_utc &lt;= request.scheduleSlot",
            " AND (binding.effective_until_utc IS NULL",
            "      OR request.scheduleSlot &lt; binding.effective_until_utc)",
            "WHERE NOT EXISTS (SELECT 1 FROM dp_pull_scope_binding_epoch duplicate",
            " WHERE duplicate.operation_code = request.operationCode",
            "  AND BINARY duplicate.scope_key = BINARY request.scopeKey",
            "  AND duplicate.effective_from_utc &lt;= request.scheduleSlot",
            "  AND (duplicate.effective_until_utc IS NULL",
            "       OR request.scheduleSlot &lt; duplicate.effective_until_utc)",
            "  AND BINARY duplicate.binding_id &lt;&gt; BINARY binding.binding_id)",
            "ORDER BY BINARY request.scopeKey, request.scheduleSlot",
            "</script>"
    })
    List<ScheduleTaskBindingRow> listBindingsForSlots(
            @Param("tasks") List<DataPullTask> tasks
    );

    @Select({
            "<script>",
            "SELECT", DataPullScheduleApplyMapper.STAGE_COLUMNS,
            "FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND schedule_state IN ('PENDING','RUNNING')",
            "<if test='afterScopeKey != null'>",
            " AND BINARY scope_key &gt; BINARY #{afterScopeKey}",
            "</if>",
            "ORDER BY BINARY scope_key LIMIT #{limit}",
            "</script>"
    })
    List<ScheduleSourceStageRow> listScheduleStageAfter(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("afterScopeKey") String afterScopeKey,
            @Param("limit") int limit
    );

    @Select({
            "<script>",
            "SELECT request.scopeKey, (SELECT task.schedule_slot FROM dp_pull_task task",
            " WHERE task.operation_code = #{operationCode}",
            "  AND BINARY task.scope_key = BINARY request.scopeKey",
            " ORDER BY task.schedule_slot DESC, task.id DESC LIMIT 1) AS latestScheduleSlot",
            "FROM (",
            "<foreach collection='scopeKeys' item='scopeKey' separator=' UNION ALL '>",
            " SELECT #{scopeKey} scopeKey",
            "</foreach>",
            ") request ORDER BY BINARY request.scopeKey",
            "</script>"
    })
    List<ScheduleLatestSlotRow> listLatestSlots(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Update({
            "<script>",
            "UPDATE dp_pull_schedule_source_scope",
            "SET schedule_after_utc = CASE BINARY scope_key",
            "<foreach collection='updates' item='item'>",
            " WHEN BINARY #{item.scopeKey} THEN #{item.scheduleAfterUtc}",
            "</foreach>",
            " ELSE schedule_after_utc END, schedule_state = CASE BINARY scope_key",
            "<foreach collection='updates' item='item'>",
            " WHEN BINARY #{item.scopeKey} THEN 'RUNNING'",
            "</foreach>",
            " ELSE schedule_state END, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND schedule_state IN ('PENDING','RUNNING') AND BINARY scope_key IN",
            "<foreach collection='updates' item='item' open='(' separator=',' close=')'>",
            " #{item.scopeKey}",
            "</foreach>",
            "</script>"
    })
    int updateRunningScheduleStages(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("updates") List<ScheduleStageProgressUpdate> updates
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND admission_anchor_state = 'COMPLETE'",
            " AND binding_state IN ('COMPLETE','NOT_REQUIRED')",
            " AND schedule_state IN ('PENDING','RUNNING') AND BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            " #{scopeKey}",
            "</foreach>",
            "LIMIT 64",
            "</script>"
    })
    int deleteCompletedScheduleStages(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Select({
            "<script>",
            "SELECT 1 FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND schedule_state IN ('PENDING','RUNNING')",
            "<if test='atOrBeforeScopeKey != null'>",
            " AND BINARY scope_key &lt;= BINARY #{atOrBeforeScopeKey}",
            "</if>",
            "ORDER BY BINARY scope_key LIMIT 1",
            "</script>"
    })
    Integer findPendingScheduleAtOrBefore(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("atOrBeforeScopeKey") String atOrBeforeScopeKey
    );

    @Update({
            "UPDATE dp_pull_schedule_source_epoch",
            "SET schedule_cursor_scope_key = #{nextCursor}, epoch_state = #{nextState},",
            " active_operation_slot = CASE WHEN #{nextState} = 'COMPLETE' THEN NULL",
            "                              ELSE active_operation_slot END,",
            " terminal_at_utc = CASE WHEN #{nextState} = 'COMPLETE'",
            "                        THEN UTC_TIMESTAMP(3) ELSE terminal_at_utc END,",
            " version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND epoch_state = 'SCHEDULING' AND version_no = #{expectedVersion}",
            " AND (schedule_cursor_scope_key &lt;=&gt; #{expectedCursor})",
            " AND (#{nextState} &lt;&gt; 'COMPLETE' OR NOT EXISTS (",
            "  SELECT 1 FROM dp_pull_schedule_source_scope child",
            "  WHERE child.operation_code = #{operationCode}",
            "   AND child.epoch_no = #{epochNo}))"
    })
    int advanceSchedulePhase(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedCursor") String expectedCursor,
            @Param("nextCursor") String nextCursor,
            @Param("nextState") String nextState
    );
}

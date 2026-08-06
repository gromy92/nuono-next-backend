package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleSourceEpochRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** SQL Adapter for restart-safe, child-first schedule epoch retention. */
public interface DataPullScheduleEpochRetentionMapper {

    @Delete({
            "DELETE FROM dp_pull_schedule_dp08_member_stage_item",
            "WHERE operation_code=#{operationCode} AND epoch_no=#{epochNo}",
            "AND EXISTS (SELECT 1 FROM dp_pull_schedule_source_epoch terminal",
            " WHERE terminal.operation_code=#{operationCode} AND terminal.epoch_no=#{epochNo}",
            " AND terminal.active_operation_slot IS NULL",
            " AND terminal.epoch_state IN ('COMPLETE','ABORTED')",
            " AND terminal.version_no=#{expectedVersion})",
            "ORDER BY scan_pass,BINARY scope_key,BINARY member_key LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalDp08MemberStageItems(@Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,@Param("expectedVersion") long expectedVersion,
            @Param("limit") int limit);

    @Delete({
            "DELETE FROM dp_pull_schedule_dp08_member_stage_head",
            "WHERE operation_code=#{operationCode} AND epoch_no=#{epochNo}",
            "AND EXISTS (SELECT 1 FROM dp_pull_schedule_source_epoch terminal",
            " WHERE terminal.operation_code=#{operationCode} AND terminal.epoch_no=#{epochNo}",
            " AND terminal.active_operation_slot IS NULL",
            " AND terminal.epoch_state IN ('COMPLETE','ABORTED')",
            " AND terminal.version_no=#{expectedVersion})",
            "AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_dp08_member_stage_item child",
            " WHERE child.operation_code=#{operationCode} AND child.epoch_no=#{epochNo}",
            " AND child.scan_pass=dp_pull_schedule_dp08_member_stage_head.scan_pass",
            " AND BINARY child.scope_key=BINARY dp_pull_schedule_dp08_member_stage_head.scope_key)",
            "ORDER BY scan_pass,BINARY scope_key LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalDp08MemberStageHeads(@Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,@Param("expectedVersion") long expectedVersion,
            @Param("limit") int limit);

    @Select({
            "SELECT", DataPullScheduleScanMapper.EPOCH_COLUMNS,
            "FROM dp_pull_schedule_source_epoch",
            "WHERE operation_code = #{operationCode} AND active_operation_slot IS NULL",
            " AND epoch_state IN ('COMPLETE','ABORTED')",
            " AND terminal_at_utc < #{cutoffUtc}",
            "ORDER BY terminal_at_utc ASC, epoch_no ASC LIMIT 1"
    })
    ScheduleSourceEpochRow findExpiredTerminalEpoch(
            @Param("operationCode") OperationCode operationCode,
            @Param("cutoffUtc") LocalDateTime cutoffUtc
    );

    @Select({
            "SELECT", DataPullScheduleScanMapper.EPOCH_COLUMNS,
            "FROM dp_pull_schedule_source_epoch",
            "WHERE operation_code = #{operationCode} AND active_operation_slot IS NULL",
            " AND epoch_state IN ('COMPLETE','ABORTED')",
            "ORDER BY epoch_no DESC LIMIT 1 OFFSET 2"
    })
    ScheduleSourceEpochRow findThirdNewestTerminalEpoch(
            @Param("operationCode") OperationCode operationCode
    );

    @Delete({
            "DELETE FROM dp_pull_schedule_source_scope",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND EXISTS (SELECT 1 FROM dp_pull_schedule_source_epoch terminal",
            "  WHERE terminal.operation_code = #{operationCode}",
            "   AND terminal.epoch_no = #{epochNo}",
            "   AND terminal.active_operation_slot IS NULL",
            "   AND terminal.epoch_state IN ('COMPLETE','ABORTED')",
            "   AND terminal.version_no = #{expectedVersion})",
            "ORDER BY BINARY scope_key LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalScopeRows(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_schedule_source_epoch",
            "WHERE operation_code = #{operationCode} AND epoch_no = #{epochNo}",
            " AND active_operation_slot IS NULL",
            " AND epoch_state IN ('COMPLETE','ABORTED')",
            " AND version_no = #{expectedVersion}",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_source_scope child",
            "  WHERE child.operation_code = #{operationCode}",
            "   AND child.epoch_no = #{epochNo})",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_dp08_member_stage_head nested",
            "  WHERE nested.operation_code=#{operationCode} AND nested.epoch_no=#{epochNo})",
            "LIMIT 1"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalEpochIfEmpty(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("expectedVersion") long expectedVersion
    );
}

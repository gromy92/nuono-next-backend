package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Row locks and CAS reserved for atomic DP catch-up compaction. */
public interface DataPullTaskCompactionMapper {

    @Select({
            "SELECT next_id",
            "FROM noon_pull_id_sequence",
            "WHERE sequence_name = 'dp_pull_task'",
            "FOR UPDATE"
    })
    Long lockCompactionAnchor();

    @Select({
            "SELECT", DataPullRuntimeMapper.COLUMNS,
            "FROM dp_pull_task",
            "WHERE operation_code = #{operationCode}",
            "  AND BINARY scope_key = BINARY #{scopeKey}",
            "  AND state = 'QUEUED'",
            "  AND fence_epoch = 0",
            "  AND checkpoint IS NULL",
            "  AND remote_handle IS NULL",
            "  AND lease_owner IS NULL",
            "  AND lease_until IS NULL",
            "ORDER BY schedule_slot ASC, id ASC",
            "FOR UPDATE"
    })
    List<DataPullTask> lockStrictlyNeverStarted(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey
    );

    @Select({
            "<script>",
            "SELECT", DataPullRuntimeMapper.COLUMNS,
            "FROM dp_pull_task",
            "WHERE operation_code = #{operationCode}",
            " AND BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            " #{scopeKey}",
            "</foreach>",
            " AND state = 'QUEUED' AND fence_epoch = 0",
            " AND checkpoint IS NULL AND remote_handle IS NULL",
            " AND lease_owner IS NULL AND lease_until IS NULL",
            "ORDER BY BINARY scope_key, schedule_slot, id LIMIT 65 FOR UPDATE",
            "</script>"
    })
    List<DataPullTask> lockStrictlyNeverStartedBatch(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Update({
            "<script>",
            "UPDATE dp_pull_task",
            "SET state = 'SUPERSEDED', finished_at = #{now},",
            " version_no = version_no + 1, gmt_updated = #{now}",
            "WHERE state = 'QUEUED' AND fence_epoch = 0",
            " AND checkpoint IS NULL AND remote_handle IS NULL",
            " AND lease_owner IS NULL AND lease_until IS NULL AND (",
            "<foreach collection='tasks' item='task' separator=' OR '>",
            " (id = #{task.id} AND version_no = #{task.version})",
            "</foreach>",
            ")",
            "</script>"
    })
    int supersedeStrictlyNeverStartedBatch(
            @Param("tasks") List<DataPullTask> tasks,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE dp_pull_task",
            "SET state = 'SUPERSEDED',",
            "    finished_at = #{now},",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{taskId}",
            "  AND version_no = #{expectedVersion}",
            "  AND state = 'QUEUED'",
            "  AND fence_epoch = 0",
            "  AND checkpoint IS NULL",
            "  AND remote_handle IS NULL",
            "  AND lease_owner IS NULL",
            "  AND lease_until IS NULL"
    })
    int supersedeStrictlyNeverStarted(
            @Param("taskId") long taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now
    );
}

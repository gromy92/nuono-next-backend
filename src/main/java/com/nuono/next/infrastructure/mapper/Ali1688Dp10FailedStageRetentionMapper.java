package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FailedStageCandidate;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FailedTaskFence;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Chooses and locks one oldest eligible FAILED DP-10 task/generation per run. */
public interface Ali1688Dp10FailedStageRetentionMapper {

    String ELIGIBLE_TASK = " task.operation_code='DP10' AND task.state='FAILED'"
            + " AND task.finished_at IS NOT NULL AND task.finished_at<#{cutoffUtc}"
            + " AND task.lease_owner IS NULL AND task.lease_until IS NULL";

    @Select({
            "SELECT marker.task_id AS taskId,marker.generation_no AS generationNo,",
            "TRUE AS markerCandidate",
            "FROM dp_pull_dp10_stage_cleanup marker",
            "INNER JOIN dp_pull_task task ON task.id=marker.task_id WHERE",
            ELIGIBLE_TASK,
            "ORDER BY task.finished_at ASC,marker.task_id ASC,marker.generation_no ASC",
            "LIMIT 1"
    })
    Ali1688Dp10FailedStageCandidate selectOldestEligibleMarker(
            @Param("cutoffUtc") LocalDateTime cutoffUtc);

    @Select({
            "SELECT candidate.task_id AS taskId,candidate.generation_no AS generationNo,",
            "FALSE AS markerCandidate",
            "FROM (SELECT task_id,generation_no FROM dp_pull_dp10_stage_fingerprint_count",
            " UNION SELECT task_id,generation_no FROM dp_pull_dp10_stage_identity",
            " UNION SELECT task_id,generation_no FROM dp_pull_dp10_stage_item",
            " UNION SELECT task_id,generation_no FROM dp_pull_dp10_stage_page) candidate",
            "INNER JOIN dp_pull_task task ON task.id=candidate.task_id WHERE",
            ELIGIBLE_TASK,
            "ORDER BY task.finished_at ASC,candidate.task_id ASC,candidate.generation_no ASC",
            "LIMIT 1"
    })
    Ali1688Dp10FailedStageCandidate selectOldestEligibleGeneration(
            @Param("cutoffUtc") LocalDateTime cutoffUtc);

    @Select({
            "SELECT task.id AS taskId,task.fence_epoch AS fenceEpoch,",
            "task.step_code AS stepCode,task.checkpoint",
            "FROM dp_pull_task task WHERE task.id=#{taskId} AND",
            ELIGIBLE_TASK,
            "FOR UPDATE"
    })
    Ali1688Dp10FailedTaskFence lockEligibleTaskFence(
            @Param("taskId") long taskId,
            @Param("cutoffUtc") LocalDateTime cutoffUtc
    );
}

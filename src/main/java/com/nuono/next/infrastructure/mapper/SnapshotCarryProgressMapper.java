package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.SnapshotCarryMode;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Fenced progress transitions for bounded effective-generation carry-forward. */
public interface SnapshotCarryProgressMapper {
    @Update({
            "UPDATE dp_pull_snapshot_apply_progress progress",
            "JOIN dp_pull_task task ON task.id=progress.task_id",
            "SET progress.state='CARRYING', progress.carry_mode=#{carryMode},",
            "  progress.carry_source_task_id=#{sourceTaskId},",
            "  progress.carry_source_head_version=#{sourceHeadVersion},",
            "  progress.carry_cursor_identity=NULL, progress.gmt_updated=#{nowUtc}",
            "WHERE progress.task_id=#{snapshot.taskId}",
            "  AND progress.active_fence_epoch=#{snapshot.fenceEpoch}",
            "  AND progress.state='PREPARING'",
            "  AND progress.prepared_item_count=#{snapshot.appliedItemCount}",
            "  AND task.state='RUNNING' AND task.fence_epoch=#{snapshot.fenceEpoch}",
            "  AND BINARY task.lease_owner=BINARY #{snapshot.leaseOwner}",
            "  AND task.lease_until>#{nowUtc}"
    })
    int startCarry(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("carryMode") SnapshotCarryMode carryMode,
            @Param("sourceTaskId") long sourceTaskId,
            @Param("sourceHeadVersion") long sourceHeadVersion,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_snapshot_apply_progress progress",
            "JOIN dp_pull_task task ON task.id=progress.task_id",
            "SET progress.carry_cursor_identity=#{lastStableIdentity},",
            "  progress.effective_item_count=progress.effective_item_count+#{effectiveDelta},",
            "  progress.gmt_updated=#{nowUtc}",
            "WHERE progress.task_id=#{snapshot.taskId}",
            "  AND progress.active_fence_epoch=#{snapshot.fenceEpoch}",
            "  AND progress.state='CARRYING'",
            "  AND progress.carry_source_task_id=#{sourceTaskId}",
            "  AND progress.carry_source_head_version=#{sourceHeadVersion}",
            "  AND progress.carry_cursor_identity <=> #{expectedStableIdentity}",
            "  AND task.state='RUNNING' AND task.fence_epoch=#{snapshot.fenceEpoch}",
            "  AND BINARY task.lease_owner=BINARY #{snapshot.leaseOwner}",
            "  AND task.lease_until>#{nowUtc}"
    })
    int advanceCarry(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("sourceTaskId") long sourceTaskId,
            @Param("sourceHeadVersion") long sourceHeadVersion,
            @Param("expectedStableIdentity") String expectedStableIdentity,
            @Param("lastStableIdentity") String lastStableIdentity,
            @Param("effectiveDelta") int effectiveDelta,
            @Param("nowUtc") LocalDateTime nowUtc
    );
}

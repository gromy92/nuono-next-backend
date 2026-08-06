package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Fenced pre-call checkpoint CAS for side-effecting report export creation. */
public interface ReportCreateAttemptMapper {
    @Update({
            "UPDATE dp_pull_task",
            "SET step_code = #{reconcileStep}, checkpoint = #{reconcileCheckpoint},",
            "    version_no = version_no + 1, gmt_updated = #{nowUtc}",
            "WHERE id = #{taskId}",
            "  AND state = 'RUNNING'",
            "  AND fence_epoch = #{fenceEpoch}",
            "  AND version_no = #{version}",
            "  AND BINARY lease_owner = BINARY #{leaseOwner}",
            "  AND lease_until > #{nowUtc}",
            "  AND BINARY checkpoint = BINARY #{expectedCheckpoint}"
    })
    int prepareReadbackBeforeCreate(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("version") long version,
            @Param("leaseOwner") String leaseOwner,
            @Param("expectedCheckpoint") String expectedCheckpoint,
            @Param("reconcileStep") String reconcileStep,
            @Param("reconcileCheckpoint") String reconcileCheckpoint,
            @Param("nowUtc") LocalDateTime nowUtc
    );
}

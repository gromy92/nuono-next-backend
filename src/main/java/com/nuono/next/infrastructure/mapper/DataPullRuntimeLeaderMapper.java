package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Atomic MySQL statements for the singleton daily-pull scheduler leader. */
public interface DataPullRuntimeLeaderMapper {

    @Update({
            "UPDATE dp_pull_runtime_leader",
            "SET leader_epoch = CASE",
            "      WHEN leader_owner IS NOT NULL",
            "       AND BINARY leader_owner = BINARY #{owner}",
            "       AND lease_until > NOW(3)",
            "      THEN leader_epoch",
            "      ELSE leader_epoch + 1",
            "    END,",
            "    leader_owner = #{owner},",
            "    lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW(3)),",
            "    gmt_updated = NOW(3)",
            "WHERE runtime_name = 'daily_pull'",
            "  AND (",
            "    leader_owner IS NULL",
            "    OR BINARY leader_owner = BINARY #{owner}",
            "    OR lease_until <= NOW(3)",
            "  )",
            "  AND (",
            "    (BINARY leader_owner = BINARY #{owner} AND lease_until > NOW(3))",
            "    OR leader_epoch < 9223372036854775807",
            "  )"
    })
    int acquireOrRenew(
            @Param("owner") String owner,
            @Param("leaseSeconds") int leaseSeconds
    );

    @Select({
            "SELECT leader_owner AS owner, leader_epoch AS epoch,",
            "       lease_until AS leaseUntil, NOW(3) AS databaseTime",
            "FROM dp_pull_runtime_leader",
            "WHERE runtime_name = 'daily_pull'",
            "  AND BINARY leader_owner = BINARY #{owner}",
            "  AND lease_until > NOW(3)",
            "LIMIT 1"
    })
    DataPullRuntimeLeaderRow selectOwnedLive(@Param("owner") String owner);

    @Select({
            "SELECT COUNT(*)",
            "FROM dp_pull_runtime_leader",
            "WHERE runtime_name = 'daily_pull'",
            "  AND BINARY leader_owner = BINARY #{owner}",
            "  AND leader_epoch = #{epoch}",
            "  AND lease_until > NOW(3)"
    })
    int countCurrent(@Param("owner") String owner, @Param("epoch") long epoch);

    @Update({
            "UPDATE dp_pull_runtime_leader",
            "SET leader_owner = NULL, lease_until = NULL, gmt_updated = NOW(3)",
            "WHERE runtime_name = 'daily_pull'",
            "  AND BINARY leader_owner = BINARY #{owner}",
            "  AND leader_epoch = #{epoch}"
    })
    int release(@Param("owner") String owner, @Param("epoch") long epoch);
}

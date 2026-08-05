package com.nuono.next.datapull.leader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.DataPullRuntimeLeaderMapper;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class DataPullRuntimeLeaderMapperSqlTest {

    @Test
    void acquisitionUsesDatabaseTimeAndOnlyTakeoverAdvancesTheEpoch() {
        String sql = sql(DataPullRuntimeLeaderMapper.class, "acquireOrRenew", Update.class);

        assertTrue(sql.contains("BINARY leader_owner = BINARY #{owner}"));
        assertTrue(sql.contains("lease_until > NOW(3) THEN leader_epoch"));
        assertTrue(sql.contains("ELSE leader_epoch + 1"));
        assertTrue(sql.contains("TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW(3))"));
        assertTrue(sql.contains("lease_until <= NOW(3)"));
        assertFalse(sql.contains("#{now}"));
    }

    @Test
    void validationAndReleaseAreOwnerEpochCasUsingDatabaseTime() {
        String current = sql(DataPullRuntimeLeaderMapper.class, "countCurrent", Select.class);
        String release = sql(DataPullRuntimeLeaderMapper.class, "release", Update.class);

        assertTrue(current.contains("leader_epoch = #{epoch}"));
        assertTrue(current.contains("lease_until > NOW(3)"));
        assertTrue(release.contains("BINARY leader_owner = BINARY #{owner}"));
        assertTrue(release.contains("leader_epoch = #{epoch}"));
        assertTrue(release.contains("leader_owner = NULL, lease_until = NULL"));
    }

    @Test
    void taskClaimAtomicallyRejectsExpiredOwnerAndEveryOldEpoch() {
        String claim = sql(DataPullRuntimeMapper.class, "tryClaim", Update.class);

        assertTrue(claim.contains("UPDATE dp_pull_task candidate INNER JOIN dp_pull_runtime_leader"));
        assertTrue(claim.contains("runtime_leader.runtime_name = 'daily_pull'"));
        assertTrue(claim.contains(
                "BINARY runtime_leader.leader_owner = BINARY #{leaderLease.owner}"
        ));
        assertTrue(claim.contains("runtime_leader.leader_epoch = #{leaderLease.epoch}"));
        assertTrue(claim.contains("runtime_leader.lease_until > NOW(3)"));
        assertFalse(claim.contains("SELECT leader_epoch"));
    }

    private String sql(
            Class<?> mapper,
            String methodName,
            Class<? extends Annotation> annotation
    ) {
        Method method = Arrays.stream(mapper.getDeclaredMethods())
                .filter((candidate) -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        String[] fragments = annotation == Select.class
                ? method.getAnnotation(Select.class).value()
                : method.getAnnotation(Update.class).value();
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}

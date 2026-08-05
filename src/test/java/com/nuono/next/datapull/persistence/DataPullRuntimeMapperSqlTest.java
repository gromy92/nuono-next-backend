package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.DataPullEmergencyClaimHoldMapper;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCreationMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class DataPullRuntimeMapperSqlTest {

    @Test
    void taskIdAllocationRequiresThePreinitializedDedicatedSequence() {
        String sql = sql("allocateTaskId", Update.class);

        assertTrue(sql.contains("UPDATE noon_pull_id_sequence"));
        assertTrue(sql.contains("sequence_name = 'dp_pull_task'"));
        assertTrue(sql.contains("LAST_INSERT_ID(next_id + 1)"));
        assertFalse(sql.contains("INSERT INTO noon_pull_id_sequence"));
    }

    @Test
    void enqueueNeverOverwritesAnExistingStableTask() {
        String insert = sql("insertTaskIfAbsent", Insert.class);
        String lookup = sql("selectByStableKey", Select.class);

        assertTrue(insert.contains("provider_channel, owner_user_id, logical_store_id"));
        assertTrue(insert.contains("account_key, egress_key, project_code, store_code, site_code, scope_key"));
        assertTrue(insert.contains("LEFT JOIN dp_pull_scope_binding_epoch binding"));
        assertTrue(insert.contains("binding.effective_from_utc <= #{scheduleSlot}"));
        assertTrue(insert.contains("#{scheduleSlot} < binding.effective_until_utc"));
        assertTrue(insert.contains("NOT EXISTS"));
        assertTrue(insert.contains("dp_pull_scope_binding_epoch duplicate"));
        assertTrue(insert.contains("ON DUPLICATE KEY UPDATE id = id"));
        assertFalse(insert.contains("state = VALUES(state)"));
        assertTrue(lookup.contains("operation_code = #{operationCode}"));
        assertTrue(lookup.contains("BINARY scope_key = BINARY #{scopeKey}"));
        assertTrue(lookup.contains("BINARY business_window_key = BINARY #{businessWindowKey}"));
    }

    @Test
    void dueSelectionExcludesFutureWaitsAndLiveLeases() {
        String sql = sql("selectDueCandidatesAfter", Select.class);

        assertTrue(sql.contains("schedule_slot <= #{now}"));
        assertTrue(sql.contains("candidate.schedule_slot > #{afterScheduleSlot}"));
        assertTrue(sql.contains("candidate.id > #{afterTaskId}"));
        assertTrue(sql.contains(
                "candidate.lease_until IS NULL OR candidate.lease_until <= #{now}"
        ));
        assertTrue(sql.contains("state IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH')"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("FROM dp_pull_backoff_hold hold"));
        assertTrue(sql.contains("hold.blocked_until > #{now}"));
        assertTrue(sql.contains("hold.share_level = 'EXACT'"));
        assertTrue(sql.contains("hold.share_level = 'ACCOUNT'"));
        assertTrue(sql.contains("hold.share_level = 'EXIT'"));
        assertTrue(sql.contains("predecessor.operation_code = candidate.operation_code"));
        assertTrue(sql.contains("predecessor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')"));
        assertPendingAuthRecoveryPreventsClaim(sql);
        assertTrue(sql.contains("retry_not_before <= #{now}"));
        assertTrue(sql.contains("ORDER BY candidate.schedule_slot ASC, candidate.id ASC"));
    }

    @Test
    void claimUsesVersionCasAndCreatesANewFenceEpoch() {
        String sql = sql("tryClaim", Update.class);

        assertTrue(sql.contains("version_no = #{expectedVersion}"));
        assertTrue(sql.contains("candidate.fence_epoch = candidate.fence_epoch + 1"));
        assertTrue(sql.contains("candidate.version_no = candidate.version_no + 1"));
        assertFalse(sql.contains("attempt ="));
        assertTrue(sql.contains("candidate.lease_until IS NULL OR candidate.lease_until <= #{now}"));
        assertTrue(sql.contains("candidate.retry_not_before <= #{now}"));
        assertTrue(sql.contains("#{leaseUntil} > #{now}"));
        assertTrue(sql.contains("LEFT JOIN dp_pull_task predecessor"));
        assertTrue(sql.contains("predecessor.state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED')"));
        assertTrue(sql.contains("LEFT JOIN dp_pull_backoff_hold hold"));
        assertTrue(sql.contains("hold.blocked_until > #{now}"));
        assertTrue(sql.contains("LEFT JOIN dp_pull_emergency_claim_hold emergency_hold"));
        assertTrue(sql.contains("emergency_hold.blocked_until > #{now}"));
        assertTrue(sql.contains("emergency_hold.hold_scope = 'GLOBAL'"));
        assertTrue(sql.contains("emergency_hold.hold_scope = 'OPERATION'"));
        assertTrue(sql.contains("emergency_hold.hold_scope = 'SCOPE'"));
        assertTrue(sql.contains("predecessor.id IS NULL"));
        assertTrue(sql.contains("hold.hold_key IS NULL"));
        assertTrue(sql.contains("emergency_hold.hold_key IS NULL"));
        assertPendingAuthRecoveryPreventsClaim(sql);
    }

    @Test
    void emergencyClaimHoldSqlPersistsExpiryAndLoadsOneActiveSnapshot() {
        String upsert = sql(
                DataPullEmergencyClaimHoldMapper.class,
                "upsert",
                Insert.class
        );
        String active = sql(
                DataPullEmergencyClaimHoldMapper.class,
                "selectActive",
                Select.class
        );

        assertTrue(upsert.contains("hold_key, hold_scope, operation_code, scope_key, blocked_until"));
        assertTrue(upsert.contains("sanitized_reason, version_no, gmt_create, gmt_updated"));
        assertTrue(upsert.contains("blocked_until = GREATEST(blocked_until, VALUES(blocked_until))"));
        assertTrue(upsert.contains("version_no = version_no + 1"));
        assertTrue(upsert.contains("gmt_updated = GREATEST(gmt_updated, VALUES(gmt_updated))"));
        assertFalse(upsert.toLowerCase().contains("enabled"));
        assertTrue(active.contains("hold.blocked_until > #{now}"));
        assertTrue(active.contains("hold_scope AS holdScope"));
        assertTrue(active.contains("operation_code AS operationCode"));
        assertTrue(active.contains("scope_key AS scopeKey"));
        assertTrue(active.contains("ORDER BY hold.hold_key ASC"));
    }

    @Test
    void transitionAndHeartbeatRejectEveryStaleWorkerEpoch() {
        String transition = sql("transitionTask", Update.class);
        String heartbeat = sql("heartbeat", Update.class);

        for (String sql : new String[]{transition, heartbeat}) {
            assertTrue(sql.contains("state = 'RUNNING'"));
            assertTrue(sql.contains("fence_epoch = #"));
            assertTrue(sql.contains("version_no = #"));
            assertTrue(sql.contains("BINARY lease_owner = BINARY #"));
            assertTrue(sql.contains("lease_until > #"));
        }
        assertTrue(transition.contains("lease_owner = NULL"));
        assertTrue(transition.contains("lease_until = NULL"));
        assertTrue(transition.contains("'WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH'"));
        assertTrue(transition.contains("THEN CASE WHEN attempt < 2147483647 THEN attempt + 1"));
        assertTrue(transition.contains("WHEN #{transition.nextState} IN ('QUEUED', 'SUCCEEDED') THEN 0"));
        assertFalse(transition.contains("'SUPERSEDED'"));
        assertTrue(heartbeat.contains("#{leaseUntil} > lease_until"));
    }

    @Test
    void unstartedClaimReleaseIsAnExactCasAndPreservesProgressEvidence() {
        String release = sql("releaseUnstartedClaim", Update.class);

        assertTrue(release.contains("state = 'QUEUED'"));
        assertTrue(release.contains("lease_owner = NULL"));
        assertTrue(release.contains("lease_until = NULL"));
        assertTrue(release.contains("version_no = version_no + 1"));
        assertTrue(release.contains("state = 'RUNNING'"));
        assertTrue(release.contains("id = #{taskId}"));
        assertTrue(release.contains("fence_epoch = #{expectedFenceEpoch}"));
        assertTrue(release.contains("version_no = #{expectedVersion}"));
        assertTrue(release.contains("BINARY lease_owner = BINARY #{leaseOwner}"));
        assertTrue(release.contains("lease_until > #{now}"));
        assertFalse(release.contains("step_code ="));
        assertFalse(release.contains("remote_handle ="));
        assertFalse(release.contains("checkpoint ="));
    }

    private String sql(String methodName, Class<? extends Annotation> annotationType) {
        Class<?> mapper = methodName.equals("allocateTaskId")
                || methodName.equals("insertTaskIfAbsent")
                || methodName.equals("selectByStableKey")
                || methodName.equals("selectLatestScheduleSlot")
                ? DataPullTaskCreationMapper.class
                : DataPullRuntimeMapper.class;
        return sql(mapper, methodName, annotationType);
    }

    private void assertPendingAuthRecoveryPreventsClaim(String sql) {
        assertTrue(sql.contains("candidate.state <> 'WAITING_AUTH' OR NOT EXISTS"));
        assertTrue(sql.contains("FROM noon_auth_identity_recovery_item auth_item"));
        assertTrue(sql.contains("auth_item.source_task_id = candidate.id"));
        assertTrue(sql.contains("auth_item.source_domain = BINARY 'DP_RUNTIME'"));
        assertTrue(sql.contains("auth_item.status = 'PENDING'"));
    }

    private String sql(
            Class<?> mapperType,
            String methodName,
            Class<? extends Annotation> annotationType
    ) {
        Method method = Arrays.stream(mapperType.getDeclaredMethods())
                .filter((candidate) -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        if (annotationType == Insert.class) {
            return compact(method.getAnnotation(Insert.class).value());
        }
        if (annotationType == Select.class) {
            return compact(method.getAnnotation(Select.class).value());
        }
        return compact(method.getAnnotation(Update.class).value());
    }

    private String compact(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }
}

package com.nuono.next.noonauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonAuthRateLimitRecoveryMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonAuthRateLimitPersistenceContractTest {

    @Test
    void singleSendRateLimitHoldHasANarrowBudgetPreservingReleasePath() {
        String recoverySql = updateSql("releaseEligibleRateLimitedManualHold");
        String projectSql = updateSql("releaseRateLimitedProjectHolds");

        assertThat(recoverySql)
                .contains("status = 'WAITING_COOLDOWN'")
                .contains("next_attempt_at = #{nextAttemptAt}")
                .contains("id = #{recoveryId}")
                .contains("version_no = #{expectedVersion}")
                .contains("identity_key = #{identityKey}")
                .contains("status = 'MANUAL_HOLD'")
                .contains("failure_code = 'SEND_RATE_LIMITED'")
                .contains("config_fingerprint <=> #{expectedConfigFingerprint}")
                .contains("send_attempt_count = 1")
                .contains("second_send_at IS NULL")
                .contains("COALESCE(second_send_at, first_send_at) <= #{cooldownCutoff}")
                .contains("active_identity_slot IS NOT NULL")
                .contains("version_no = version_no + 1")
                .doesNotContain("send_attempt_count = 0")
                .doesNotContain("first_send_at = NULL")
                .doesNotContain("second_send_at = NULL")
                .doesNotContain("send_budget_epoch = send_budget_epoch + 1");
        assertThat(projectSql)
                .contains("status = 'REAUTH_REQUIRED'")
                .contains("active_recovery_id = #{recoveryId}")
                .contains("status = 'MANUAL_HOLD'")
                .contains("last_failure_code = 'SEND_RATE_LIMITED'")
                .contains("manual_hold_reason = NULL");
    }

    private static String updateSql(String methodName) {
        Method method = Arrays.stream(NoonAuthRateLimitRecoveryMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }
}

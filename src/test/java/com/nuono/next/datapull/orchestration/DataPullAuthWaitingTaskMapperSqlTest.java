package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullAuthWaitingTaskMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DataPullAuthWaitingTaskMapperSqlTest {

    @Test
    void resumeIsOneJoinUpdateBoundToBothTaskAndRecoveryFences() throws Exception {
        assertThat(sql("resumeAfterAuthorization"))
                .contains("UPDATE dp_pull_task task")
                .contains("JOIN noon_auth_identity_recovery_item item")
                .contains("JOIN noon_auth_identity_recovery recovery")
                .contains("item.id = #{itemId}")
                .contains("item.recovery_id = #{recoveryId}")
                .contains("item.source_task_id = #{sourceTaskId}")
                .contains("BINARY item.source_domain = BINARY 'DP_RUNTIME'")
                .contains("BINARY item.source_checkpoint = BINARY #{expectedTaskVersionText}")
                .contains("item.resume_policy = 'AUTO_RESUME'")
                .contains("item.status = 'PENDING'")
                .contains("task.state = 'WAITING_AUTH'")
                .contains("task.version_no = #{expectedTaskVersion}")
                .contains("task.owner_user_id = item.owner_user_id")
                .contains("BINARY task.project_code = BINARY item.project_code")
                .contains("BINARY task.store_code = BINARY item.store_code")
                .contains("recovery.status = #{expectedRecoveryStatus}")
                .contains("recovery.version_no = #{expectedRecoveryVersion}")
                .contains("BINARY recovery.lease_token = BINARY #{expectedLeaseToken}")
                .contains("recovery.lease_until > #{now}")
                .contains("recovery.active_identity_slot IS NOT NULL")
                .contains("task.state = 'QUEUED'")
                .contains("task.attempt = 0")
                .contains("task.version_no = task.version_no + 1");
    }

    @Test
    void failedRecoveryHoldsTheSameTaskForManualReviewWithoutTerminatingIt() throws Exception {
        assertThat(sql("holdAuthorizationManualReview"))
                .contains("UPDATE dp_pull_task task")
                .contains("JOIN noon_auth_identity_recovery_item item")
                .contains("JOIN noon_auth_identity_recovery recovery")
                .contains("SET task.retry_not_before = NULL")
                .contains("task.sanitized_failure_code = #{sanitizedFailureCode}")
                .contains("item.source_checkpoint = BINARY #{expectedTaskVersionText}")
                .contains("item.resume_policy = 'AUTO_RESUME'")
                .contains("task.state = 'WAITING_AUTH'")
                .contains("task.version_no = #{expectedTaskVersion}")
                .contains("lease_owner IS NULL")
                .contains("lease_until IS NULL")
                .contains("recovery.lease_until > #{now}")
                .doesNotContain("state = 'FAILED'")
                .doesNotContain("finished_at =")
                .doesNotContain("version_no = version_no + 1");
    }

    private String sql(String methodName) throws Exception {
        Method method = Arrays.stream(DataPullAuthWaitingTaskMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String raw = String.join("\n", update.value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}

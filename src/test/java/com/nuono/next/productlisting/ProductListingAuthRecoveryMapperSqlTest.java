package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingAuthRecoveryMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductListingAuthRecoveryMapperSqlTest {
    @Test
    void schedulerSelectsOnlyTerminalAuthBlockedRealRuns() {
        String sql = String.join(" ", sqlMethod("selectPendingAuthRecoveryTasks")
                .getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("mode = 'REAL_RUN'"));
        assertTrue(sql.contains("status IN ('failed', 'written_verify_failed')"));
        assertTrue(sql.contains("failure_code IN ('noon_auth_required', 'noon_auth_recovered')"));
        assertTrue(sql.contains("$.recoveryId"));
        assertTrue(sql.contains("IS NOT NULL"));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertTrue(sql.contains(
                "PARTITION BY task.owner_user_id, BINARY task.store_code"));
        assertTrue(sql.contains("pending.store_recovery_rank = 1"));
        assertTrue(sql.contains(
                "CASE WHEN pending.id > #{afterTaskId} THEN 0 ELSE 1 END"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void manualReviewTransitionCannotRewriteAnUnrelatedTask() {
        String sql = updateSql("markAuthRecoveryManualReview");

        assertTrue(sql.contains("status = 'written_verify_failed'"));
        assertTrue(sql.contains("failure_code = 'noon_auth_required'"));
        assertTrue(sql.contains("failure_code = 'listing_auth_recovery_manual_review'"));
        assertTrue(sql.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("noon_result_json = #{expectedNoonResultJson}"));
        assertTrue(sql.contains("= #{recoveryId}"));
    }

    @Test
    void supersededTransitionOnlyReleasesPreWriteAuthTasks() {
        String sql = updateSql("markPreWriteAuthRecoverySuperseded");

        assertTrue(sql.contains("failure_code = 'listing_auth_recovery_superseded'"));
        assertTrue(sql.contains("failure_code = 'noon_auth_required'"));
        assertTrue(sql.contains("noon_result_json = #{expectedNoonResultJson}"));
        assertTrue(sql.contains("= #{recoveryId}"));
        assertTrue(sql.contains("$.writeMayHaveOccurred"));
        assertTrue(sql.contains("= FALSE"));
    }

    private String updateSql(String methodName) {
        Method method = sqlMethod(methodName);
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }

    private Method sqlMethod(String methodName) {
        return Arrays.stream(ProductListingAuthRecoveryMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}

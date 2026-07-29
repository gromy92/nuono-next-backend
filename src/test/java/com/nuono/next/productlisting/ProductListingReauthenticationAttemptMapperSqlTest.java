package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductListingReauthenticationAttemptMapperSqlTest {

    @Test
    void migrationStoresExactRecoveryItemVersionAndResumeAction()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/"
                        + "205_product_listing_reauthentication_attempt.sql"
        ));

        assertTrue(sql.contains("`real_run_task_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`recovery_item_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`requested_auth_version` BIGINT NOT NULL"));
        assertTrue(sql.contains("`resume_action` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`version_no` BIGINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("PRIMARY KEY (`real_run_task_id`)"));
    }

    @Test
    void insertIsIdempotentAndDoesNotOverwriteAnExistingBinding()
            throws Exception {
        Method method = ProductListingReauthenticationAttemptMapper.class
                .getMethod(
                        "insertPendingAttempt",
                        ProductListingReauthenticationAttemptRecord.class
                );
        String sql = String.join(" ", method.getAnnotation(Insert.class).value());

        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains(
                "real_run_task_id = VALUES(real_run_task_id)"
        ));
        assertFalse(sql.contains(
                "recovery_id = VALUES(recovery_id)"
        ));
    }

    @Test
    void sourceItemAndCompletionCasUseTheExactRecoveredFence()
            throws Exception {
        Method source = ProductListingReauthenticationAttemptMapper.class
                .getMethod(
                        "selectSourceLessRecoveryItem",
                        Long.class,
                        Long.class,
                        String.class
                );
        String sourceSql = String.join(
                " ",
                source.getAnnotation(Select.class).value()
        );
        Method claim = ProductListingReauthenticationAttemptMapper.class
                .getMethod(
                        "claimRecoveredAttempt",
                        Long.class,
                        Long.class,
                        Long.class,
                        Long.class,
                        Long.class
                );
        String claimSql = String.join(
                " ",
                claim.getAnnotation(Update.class).value()
        );

        assertTrue(sourceSql.contains("source_task_id IS NULL"));
        assertTrue(claimSql.contains(
                "item.id = attempt.recovery_item_id"
        ));
        assertTrue(claimSql.contains("item.status = 'RECOVERED'"));
        assertTrue(claimSql.contains("item.recovered_at IS NOT NULL"));
        assertTrue(claimSql.contains(
                "project_state.active_recovery_id IS NULL"
        ));
        assertTrue(claimSql.contains(
                "project_state.auth_version = "
                        + "attempt.requested_auth_version + 1"
        ));
    }

    @Test
    void terminalAttemptRebindCasAllowsOnlyFailedOrCompletedBindings()
            throws Exception {
        Method method = ProductListingReauthenticationAttemptMapper.class
                .getMethod(
                        "rebindTerminalAttemptCas",
                        ProductListingReauthenticationAttemptRecord.class,
                        Long.class,
                        Long.class,
                        Long.class
                );
        String sql = String.join(
                " ",
                method.getAnnotation(Update.class).value()
        );

        assertTrue(sql.contains(
                "status IN ('FAILED', 'COMPLETED')"
        ));
        assertTrue(sql.contains(
                "recovery_id = #{expectedRecoveryId}"
        ));
        assertTrue(sql.contains(
                "recovery_item_id = #{expectedRecoveryItemId}"
        ));
        assertTrue(sql.contains(
                "version_no = #{expectedVersionNo}"
        ));
    }
}

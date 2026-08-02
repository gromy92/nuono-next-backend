package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonAuthProductTaskMapper;
import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import com.nuono.next.infrastructure.mapper.SalesSyncTaskMapper;
import com.nuono.next.infrastructure.mapper.StoreInitializationSnapshotMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonAuthBusinessWaitPersistenceContractTest {

    @Test
    void migrationStoresBusinessIdentityCheckpointAndResumePolicy() throws Exception {
        String migration = resource("/db/init/238_noon_auth_business_wait_queue.sql");
        String postcheck = resource("/db/postcheck/238_noon_auth_business_wait_queue.sql");

        assertTrue(migration.contains("source_checkpoint"));
        assertTrue(migration.contains("resume_policy"));
        assertTrue(migration.contains("source_task_key"));
        assertTrue(migration.contains("CONCAT(COALESCE(NULLIF(UPPER(TRIM(source_domain))"));
        assertTrue(migration.contains("uk_noon_auth_recovery_item_business_source"));
        assertTrue(postcheck.contains("required_column_count"));
        assertTrue(postcheck.contains("business_source_index_valid"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS product_listing_reauthentication_attempt"));
        assertTrue(postcheck.contains("obsolete_listing_reauthentication_table_count"));
    }

    @Test
    void recoveryItemPersistsTheGenericBusinessContract() throws Exception {
        Method method = NoonAuthRecoveryMapper.class.getDeclaredMethod(
                "coalesceRecoveryItem",
                NoonAuthRecoveryItemRecord.class
        );
        String sql = String.join(" ", method.getAnnotation(Insert.class).value());

        assertTrue(sql.contains("source_domain"));
        assertTrue(sql.contains("source_checkpoint"));
        assertTrue(sql.contains("resume_policy"));
        assertTrue(sql.contains("#{sourceCheckpoint}"));
        assertTrue(sql.contains("#{resumePolicy}"));
    }

    @Test
    void productResumeCasRequiresSafeCheckpointAndLiveRecoveryFence() throws Exception {
        Method method = NoonAuthProductTaskMapper.class.getDeclaredMethod(
                "resumeSafeProductTask",
                Long.class,
                Long.class,
                NoonAuthRecoveryStatus.class,
                Long.class,
                String.class,
                java.time.LocalDateTime.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("item.resume_policy = 'auto_resume'"));
        assertTrue(sql.contains("writeMayHaveOccurred".toLowerCase(Locale.ROOT)));
        assertTrue(sql.contains("= 'false'"));
        assertTrue(sql.contains("recovery.version_no = #{expectedrecoveryversion}"));
        assertTrue(sql.contains("recovery.lease_token = #{expectedleasetoken}"));
        assertFalse(sql.contains("retry_count = retry_count + 1"));
    }

    @Test
    void additionalBusinessResumesUseExactSourceAndLiveRecoveryFence() throws Exception {
        Method salesMethod = SalesSyncTaskMapper.class.getDeclaredMethod(
                "resumeAfterAuthorization",
                Long.class,
                Long.class,
                NoonAuthRecoveryStatus.class,
                Long.class,
                String.class,
                java.time.LocalDateTime.class
        );
        String salesSql = String.join(" ", salesMethod.getAnnotation(Update.class).value())
                .toLowerCase(Locale.ROOT);
        assertTrue(salesSql.contains("item.source_domain = 'sales_sync'"));
        assertTrue(salesSql.contains("item.resume_policy = 'auto_resume'"));
        assertTrue(salesSql.contains("recovery.lease_token = #{expectedleasetoken}"));

        Method initializationMethod = StoreInitializationSnapshotMapper.class.getDeclaredMethod(
                "resumeAfterAuthorization",
                Long.class,
                Long.class,
                NoonAuthRecoveryStatus.class,
                Long.class,
                String.class,
                java.time.LocalDateTime.class
        );
        String initializationSql = String.join(
                " ", initializationMethod.getAnnotation(Update.class).value()
        ).toLowerCase(Locale.ROOT);
        assertTrue(initializationSql.contains("item.source_domain = 'store_initialization'"));
        assertTrue(initializationSql.contains("item.resume_policy = 'auto_resume'"));
        assertTrue(initializationSql.contains("recovery.lease_token = #{expectedleasetoken}"));
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseObjectScopeSql;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentConcurrencySqlTest {

    @Test
    void firstCreateLocksTheAuthorizedParentAndThenLocksTheActiveSlot() throws Exception {
        Method parentLock = OfficialWarehouseMapper.class.getMethod(
                "lockAsnForAppointment", Long.class, Long.class
        );
        String parentSql = sql(parentLock.getAnnotation(Select.class).value());

        assertThat(parentSql).contains("FROM official_warehouse_asn");
        assertThat(parentSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(parentSql).contains("is_deleted = b'0'");
        assertThat(parentSql).contains("FOR UPDATE");

        String activeSql = compact(
                OfficialWarehouseObjectScopeSql.selectActiveAppointmentByAsnForUpdate()
        );
        assertThat(activeSql).contains("status<>'CANCELED'");
        assertThat(activeSql).contains("FORUPDATE");
    }

    @Test
    void claimsUseExpectedVersionAndAllocateANewExecutionVersion() throws Exception {
        Method manual = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentRunning",
                Long.class, Long.class, Long.class, Long.class
        );
        Method scheduler = OfficialWarehouseMapper.class.getMethod(
                "claimDueAppointmentForRun",
                Long.class, Long.class, Long.class, Long.class
        );

        for (Method method : new Method[]{manual, scheduler}) {
            String sql = sql(method.getAnnotation(Update.class).value());
            assertThat(sql).contains("execution_version = execution_version + 1");
            assertThat(sql).contains("execution_version = #{expectedExecutionVersion}");
            assertThat(sql).contains("status = 'RUNNING'");
        }
        assertThat(sql(scheduler.getAnnotation(Update.class).value()))
                .contains("status = 'PENDING'")
                .contains("(next_attempt_at IS NULL OR next_attempt_at <= NOW())");
    }

    @Test
    void everyWorkerCompletionIsFencedByRunningStateAndExecutionVersion() throws Exception {
        Method scheduled = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentScheduled",
                Long.class, Long.class, Long.class, java.time.LocalDate.class,
                Integer.class, String.class, Long.class
        );
        Method retry = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentPendingRetry",
                Long.class, Long.class, Long.class, int.class,
                String.class, String.class, String.class, Long.class
        );
        Method failed = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentFailed",
                Long.class, Long.class, Long.class,
                String.class, String.class, String.class, Long.class
        );

        for (Method method : new Method[]{scheduled, retry, failed}) {
            String sql = sql(method.getAnnotation(Update.class).value());
            assertThat(sql).contains("status = 'RUNNING'");
            assertThat(sql).contains("execution_version = #{runExecutionVersion}");
            assertThat(sql).contains("execution_version = execution_version + 1");
        }
    }

    @Test
    void heartbeatAndAsnProjectionShareTheSameExecutionFence() throws Exception {
        Method heartbeat = OfficialWarehouseMapper.class.getMethod(
                "heartbeatAppointmentExecution",
                Long.class, Long.class, Long.class, Long.class
        );
        Method projection = OfficialWarehouseMapper.class.getMethod(
                "updateAsnCurrentWarehouseForAppointment",
                Long.class, Long.class, Long.class, Long.class,
                String.class, String.class, String.class, Long.class
        );

        String heartbeatSql = sql(heartbeat.getAnnotation(Update.class).value());
        String projectionSql = sql(projection.getAnnotation(Update.class).value());
        assertThat(heartbeatSql)
                .contains("status = 'RUNNING'")
                .contains("execution_version = #{runExecutionVersion}");
        assertThat(projectionSql)
                .contains("appointment.status = 'RUNNING'")
                .contains("appointment.execution_version = #{runExecutionVersion}");
    }

    @Test
    void staleExecutionIsQuarantinedInsteadOfAutomaticallyReplayed() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markStaleAppointmentsForReconciliation", int.class, Long.class
        );
        String sql = sql(method.getAnnotation(Update.class).value());

        assertThat(sql).contains("status = 'FAILED'");
        assertThat(sql).contains("STALE_EXECUTION_RECONCILIATION_REQUIRED");
        assertThat(sql).contains("execution_version = execution_version + 1");
        assertThat(sql).doesNotContain("status = 'PENDING'");
    }

    @Test
    void releaseMigrationAddsOneActiveSlotAndIsManagedFailClosed() throws Exception {
        String catalog = read("src/main/resources/db/init/release-migrations.tsv");
        String migration = read(
                "src/main/resources/db/init/234_official_warehouse_appointment_concurrency.sql"
        );
        String postcheck = read(
                "src/main/resources/db/postcheck/234_official_warehouse_appointment_concurrency.sql"
        );

        assertThat(catalog).contains(
                "234\t234_official_warehouse_appointment_concurrency.sql\tMANAGED"
        );
        assertThat(migration)
                .contains("MODIFY COLUMN `is_deleted` BIT(1) NOT NULL DEFAULT b''0''")
                .contains("`execution_version` BIGINT NOT NULL DEFAULT 0")
                .contains("`active_asn_slot` BIGINT GENERATED ALWAYS AS")
                .contains("`active_remote_slot` VARCHAR(384)")
                .contains("character_maximum_length = 100")
                .contains("character_maximum_length = 80")
                .contains("character_maximum_length = 20")
                .contains("character_maximum_length = 120")
                .contains("@appointment_parent_scope_column_count = 5")
                .contains("NOT (parent_asn.owner_user_id <=> appointment.owner_user_id)")
                .contains("CHAR_LENGTH(UPPER(TRIM(COALESCE(`noon_asn_nr`")
                .contains("`status` <> 'CANCELED'")
                .contains("'^[()]*casewhen")
                .contains("status[)]*<>[(]*@canceled@")
                .contains("char_length[(]+upper[(]+trim[(]+coalesce")
                .contains("@zero@")
                .contains("@empty@")
                .contains("(_utf8mb4)?''canceled''', '@canceled@', 1, 0, 'c')")
                .contains("'(_binary|b)?''0''', '@zero@'")
                .contains("CONCAT(CHAR(92), CHAR(39)), CHAR(39)")
                .contains("CONCAT(CHAR(92), '0'), '0'")
                .contains("UNIQUE KEY `uk_official_warehouse_appointment_active_asn`")
                .contains("UNIQUE KEY `uk_official_warehouse_appointment_active_remote`")
                .contains("`status` = 'RUNNING'")
                .contains("HAVING COUNT(*) > 1");
        assertThat(migration).doesNotContain("case.*coalesce");
        assertThat(migration).doesNotContain("CHAR(39), ''");
        assertThat(migration).doesNotContain("CHAR(92), ''");
        assertThat(migration).doesNotContain("REPLACE(generation_expression, '`', '')");
        assertThat(migration).doesNotContain("_[[:alnum:]_]+");
        assertThat(migration).doesNotContain("UPDATE `official_warehouse_appointment`");
        assertThat(migration).doesNotContain("DELETE FROM `official_warehouse_appointment`");
        assertThat(postcheck)
                .contains("generation_expression")
                .contains("'^[()]*casewhen")
                .contains("status[)]*<>[(]*@canceled@")
                .contains("char_length[(]+upper[(]+trim[(]+coalesce")
                .contains("@zero@")
                .contains("@empty@")
                .contains("(_utf8mb4)?''canceled''', '@canceled@', 1, 0, 'c')")
                .contains("'(_binary|b)?''0''', '@zero@'")
                .contains("CONCAT(CHAR(92), CHAR(39)), CHAR(39)")
                .contains("CONCAT(CHAR(92), '0'), '0'")
                .contains("character_maximum_length = 80")
                .contains("character_maximum_length = 120")
                .contains("column_name = 'asn_id'")
                .contains("column_name = 'id'")
                .contains("column_name = 'owner_user_id'")
                .contains("column_name = 'store_code'")
                .contains("column_name = 'attempt_count'")
                .contains("column_name = 'status'")
                .contains("table_name = 'official_warehouse_asn'")
                .contains("NOT (parent_asn.owner_user_id <=> appointment.owner_user_id)")
                .contains("`asn_id` IS NULL")
                .contains("`status` IS NULL")
                .contains("uk_official_warehouse_appointment_active_asn")
                .contains("uk_official_warehouse_appointment_active_remote")
                .contains("`active_remote_slot` <=> CASE")
                .contains("HAVING COUNT(*) > 1");
        assertThat(postcheck).doesNotContain("case.*coalesce");
        assertThat(postcheck).doesNotContain("CHAR(39), ''");
        assertThat(postcheck).doesNotContain("CHAR(92), ''");
        assertThat(postcheck).doesNotContain("REPLACE(generation_expression, '`', '')");
        assertThat(postcheck).doesNotContain("_[[:alnum:]_]+");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ");
    }

    private static String compact(String sql) {
        return sql.replaceAll("[\\s`]", "");
    }
}

package com.nuono.next.noonauth;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryPersistenceContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/init/190_noon_shared_email_auth_recovery.sql"
    );

    @Test
    void migrationCreatesDurableSharedIdentityRecoveryTablesAndBlocksPullTaskInPlace() throws Exception {
        assertInitScriptsInclude("classpath:db/init/190_noon_shared_email_auth_recovery.sql");

        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_identity_recovery`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_identity_recovery_item`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_identity_send_ledger`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_project_auth_state`")
                .contains("`active_identity_slot` CHAR(64)")
                .contains("GENERATED ALWAYS AS")
                .contains("UNIQUE KEY `uk_noon_auth_identity_recovery_active`")
                .contains("`successor_identity_slot` CHAR(64)")
                .contains("UNIQUE KEY `uk_noon_auth_identity_recovery_successor`")
                .contains("`predecessor_recovery_id` BIGINT DEFAULT NULL")
                .contains("`coalesce_until` DATETIME NOT NULL")
                .contains("`first_send_at` DATETIME DEFAULT NULL")
                .contains("`second_send_at` DATETIME DEFAULT NULL")
                .contains("`send_intent_at` DATETIME NOT NULL")
                .contains("UNIQUE KEY `uk_noon_auth_send_ledger_generation`")
                .contains("INSERT IGNORE INTO `noon_auth_identity_send_ledger`")
                .contains("`lease_token` VARCHAR(64) DEFAULT NULL")
                .contains("`version_no` BIGINT NOT NULL DEFAULT 0")
                .contains("`send_budget_epoch` INT NOT NULL DEFAULT 0")
                .contains("COLUMN_NAME = 'send_budget_epoch'")
                .contains("ADD COLUMN `send_budget_epoch` INT NOT NULL DEFAULT 0")
                .contains("`expected_auth_version` BIGINT NOT NULL")
                .contains("`binding_fingerprint` CHAR(64)")
                .contains("`config_fingerprint` CHAR(64)")
                .contains("PRIMARY KEY (`owner_user_id`, `project_code`)")
                .contains("COLUMN_NAME = 'auth_recovery_id'")
                .contains("ADD COLUMN `auth_recovery_id` BIGINT DEFAULT NULL")
                .contains("WHEN `status` IN (''QUEUED'', ''RUNNING'', ''BLOCKED_AUTH'') THEN `active_lock_key`");

        assertThat(sql.toLowerCase())
                .doesNotContain("pkce")
                .doesNotContain("access_token")
                .doesNotContain("otp_code")
                .doesNotContain("mail_auth_code");
    }

    @Test
    void coalesceInsertReturnsExistingActiveRecoveryWithoutMovingItsWindow() {
        String sql = insertSql("coalesceActiveRecovery");

        assertThat(sql)
                .contains("INSERT INTO noon_auth_identity_recovery")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("id = LAST_INSERT_ID(id)")
                .doesNotContain("coalesce_until = VALUES(coalesce_until)")
                .doesNotContain("next_attempt_at = VALUES(next_attempt_at)");
    }

    @Test
    void claimAndTransitionUseLeaseAndVersionFences() {
        String claim = updateSql("tryClaimRecovery");
        String transition = updateSql("transitionRecovery");

        assertThat(claim)
                .contains("version_no = version_no + 1")
                .contains("status = #{expectedStatus}")
                .contains("version_no = #{expectedVersion}")
                .contains("next_attempt_at <= #{now}")
                .contains("lease_until IS NULL OR lease_until <= #{now}");

        assertThat(transition)
                .contains("status = #{targetStatus}")
                .contains("version_no = version_no + 1")
                .contains("status = #{expectedStatus}")
                .contains("version_no = #{expectedVersion}")
                .contains("lease_token = #{expectedLeaseToken}")
                .contains("lease_until > #{now}");
    }

    @Test
    void completionAndSuccessorRebindingCannotOrphanPendingTasks() {
        String completion = updateSql("completeRecoveryIfDrained");
        String promotion = updateSql("promoteSuccessorForPredecessor");

        assertThat(completion)
                .contains("NOT EXISTS")
                .contains("item.status IN ('PENDING', 'VALIDATING')")
                .contains("version_no = #{expectedVersion}")
                .contains("lease_token = #{expectedLeaseToken}");
        assertThat(promotion)
                .contains("predecessor.id = #{predecessorRecoveryId}")
                .contains("predecessor.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')")
                .contains("active.id IS NULL")
                .contains("successor.status = 'COALESCING'");
    }

    @Test
    void projectStateUpsertAndCookiePersistenceAreRecoveryGenerationSafe() {
        String upsert = insertSql("upsertProjectAuthRequired");
        String markRecovering = updateSql("markProjectRecovering");
        String persistCookie = updateSql("persistRecoveredProjectCookieCas");
        String bindingFingerprint = selectSql("selectProjectBindingFingerprint");

        assertThat(upsert)
                .contains("INSERT INTO noon_project_auth_state")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("auth_version")
                .contains("active_recovery_id")
                .contains("binding_fingerprint = CASE")
                .contains("config_fingerprint = CASE")
                .contains("THEN binding_fingerprint")
                .contains("THEN config_fingerprint");

        assertThat(markRecovering)
                .contains("status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')")
                .contains("active_recovery_id = #{recoveryId}")
                .contains("auth_version = #{expectedAuthVersion}")
                .contains("recovery.status = #{expectedRecoveryStatus}")
                .contains("recovery.version_no = #{expectedRecoveryVersion}")
                .contains("recovery.lease_token = #{expectedLeaseToken}")
                .contains("recovery.lease_until > #{now}");

        assertThat(persistCookie)
                .contains("UPDATE user_project up")
                .contains("JOIN noon_project_auth_state state")
                .contains("BINARY state.project_code = BINARY up.project_code")
                .contains("state.active_recovery_id = #{recoveryId}")
                .contains("state.auth_version = #{expectedAuthVersion}")
                .contains("state.auth_version = state.auth_version + 1")
                .contains("state.binding_fingerprint = SHA2(CONCAT(")
                .contains("up.noon_partner_cookie")
                .contains("up.noon_partner_user")
                .contains("up.noon_partner_id")
                .contains("up.bind_status")
                .contains("up.is_authorized")
                .contains("up.is_deleted")
                .contains("recovery.status = #{expectedRecoveryStatus}")
                .contains("recovery.version_no = #{expectedRecoveryVersion}")
                .contains("recovery.lease_token = #{expectedLeaseToken}")
                .contains("recovery.lease_until > #{now}")
                .contains("up.noon_partner_cookie = #{cookie}")
                .contains("AND up.is_deleted = 0")
                .contains("AND up.bind_status = 1")
                .contains("AND up.is_authorized = 1");

        assertThat(bindingFingerprint)
                .contains("SHA2(CONCAT(")
                .contains("up.noon_partner_cookie")
                .contains("up.noon_partner_user")
                .contains("up.noon_partner_id")
                .contains("up.bind_status")
                .contains("up.is_authorized")
                .contains("up.is_deleted")
                .contains("up.is_deleted = 0")
                .contains("up.bind_status = 1")
                .contains("up.is_authorized = 1")
                .doesNotContain("gmt_updated")
                .doesNotContain("cookie_generate_time")
                .doesNotContain("NULLIF(TRIM(up.noon_partner_cookie)");
    }

    @Test
    void explicitSharedEmailBindingInvalidatesAnyStaleProjectCookieBeforeQueueing() throws Exception {
        Method method = StoreSyncMapper.class.getDeclaredMethod(
                "updateProjectEmailBinding",
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("noon_partner_cookie = NULL")
                .contains("cookie_generate_time = NULL")
                .contains("bind_status = 1")
                .contains("is_authorized = 1");
    }

    @Test
    void everyWorkerProjectItemAndFailureTaskWriteCarriesTheLiveRecoveryFence() {
        for (String method : new String[]{
                "markProjectRecovering",
                "markProjectRecoveryFailed",
                "transitionRecoveryItem",
                "transitionProjectItems",
                "failBlockedTaskAfterRecovery"
        }) {
            String sql = updateSql(method);
            assertThat(sql)
                    .as(method)
                    .contains("recovery.status = #{expectedRecoveryStatus}")
                    .contains("recovery.version_no = #{expectedRecoveryVersion}")
                    .contains("recovery.lease_token = #{expectedLeaseToken}")
                    .contains("recovery.lease_until > #{now}")
                    .contains("recovery.active_identity_slot IS NOT NULL");
        }
    }

    @Test
    void featureOffDrainIsGlobalIdempotentAndPreservesPullProgressColumns() {
        String cancel = updateSql("cancelRecoveriesForDrain");
        String items = updateSql("skipItemsForDrainedRecoveries");
        String tasks = updateSql("requeueTasksForDrainedRecoveries");
        String projects = updateSql("releaseProjectsForDrainedRecoveries");

        assertThat(cancel)
                .contains("status = 'CANCELLED'")
                .contains("'WAITING_PREDECESSOR'")
                .contains("'MANUAL_HOLD'")
                .contains("lease_token = NULL")
                .contains("version_no = version_no + 1")
                .contains("#{identityKey} IS NULL OR identity_key = #{identityKey}");
        assertThat(items)
                .contains("item.status = 'SKIPPED'")
                .contains("recovery.status = 'CANCELLED'");
        assertThat(tasks)
                .contains("task.status = 'QUEUED'")
                .contains("task.auth_recovery_id = NULL")
                .contains("task.status = 'BLOCKED_AUTH'")
                .contains("recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')")
                .doesNotContain("checkpoint_cursor")
                .doesNotContain("report_export_id")
                .doesNotContain("report_export_status");
        assertThat(projects)
                .contains("state.status = 'RECOVERY_DISABLED'")
                .contains("state.active_recovery_id = NULL")
                .contains("state.auth_version = state.auth_version + 1")
                .contains("recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')");
    }

    @Test
    void sourceTaskResumeIsFencedByTheSameRecoveryLeaseAndVersion() {
        String sql = updateSql("requeueBlockedTaskAfterRecoveryCas");

        assertThat(sql)
                .contains("task.status = 'QUEUED'")
                .contains("task.status = 'BLOCKED_AUTH'")
                .contains("task.auth_recovery_id = #{recoveryId}")
                .contains("recovery.status = #{expectedRecoveryStatus}")
                .contains("recovery.version_no = #{expectedRecoveryVersion}")
                .contains("recovery.lease_token = #{expectedLeaseToken}")
                .contains("recovery.lease_until > #{now}");
    }

    @Test
    void identityChangeAuditIncludesActiveRecoveriesAndTerminalProjectHolds() {
        String sql = selectSql("listUndrainedIdentityKeysExcept");

        assertThat(sql)
                .contains("SELECT DISTINCT recovery.identity_key")
                .contains("LEFT JOIN noon_project_auth_state state")
                .contains("'WAITING_PREDECESSOR'")
                .contains("'MANUAL_HOLD'")
                .contains("state.active_recovery_id IS NOT NULL")
                .contains("recovery.identity_key <> #{identityKey}");
    }

    @Test
    void manualHoldCanOnlyBeReleasedByARealConfigurationChange() {
        String sql = updateSql("releaseManualHoldOnConfigChange");
        String projectSql = updateSql("releaseProjectManualHolds");
        String itemSql = updateSql("reopenFailedRecoveryItems");
        String terminalProjectSql = updateSql("releaseTerminalProjectHoldsOnConfigChange");

        assertThat(sql)
                .contains("status = CASE")
                .contains("'WAITING_COOLDOWN'")
                .contains("status = 'MANUAL_HOLD'")
                .contains("config_fingerprint <=> #{expectedConfigFingerprint}")
                .contains("NOT (config_fingerprint <=> #{newConfigFingerprint})")
                .contains("generation_no = 0")
                .contains("send_budget_epoch = send_budget_epoch + 1")
                .contains("send_attempt_count = 0")
                .contains("first_send_at = NULL")
                .contains("second_send_at = NULL")
                .contains("next_attempt_at = CASE")
                .contains("ELSE GREATEST(")
                .contains("TIMESTAMPADD(")
                .contains("COALESCE(second_send_at, first_send_at, #{now})")
                .doesNotContain("last_mail_uid_hash = NULL")
                .doesNotContain("last_message_id_hash = NULL")
                .contains("lease_token = NULL")
                .contains("version_no = version_no + 1");

        assertThat(projectSql)
                .contains("status = 'REAUTH_REQUIRED'")
                .contains("config_fingerprint = #{newConfigFingerprint}")
                .contains("active_recovery_id = #{recoveryId}")
                .contains("status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')");
        assertThat(itemSql)
                .contains("status = 'PENDING'")
                .contains("recovery_id = #{recoveryId}")
                .contains("status IN ('FAILED', 'VALIDATING')")
                .contains("failure_code = NULL");
        assertThat(terminalProjectSql)
                .contains("state.status = 'RECOVERY_DISABLED'")
                .contains("state.active_recovery_id = NULL")
                .contains("state.identity_key = #{identityKey}")
                .contains("state.status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')")
                .contains("recovery.status IN ('CANCELLED', 'COMPLETED', 'FAILED_FINAL')")
                .contains("NOT (state.config_fingerprint <=> #{newConfigFingerprint})");
    }

    @Test
    void quotaQueryUsesIndependentAppendOnlySendLedger() {
        String sql = selectSql("countIdentitySendsSince");
        String ledgerInsert = insertSql("insertIdentitySendLedger");

        assertThat(sql)
                .contains("FROM noon_auth_identity_send_ledger")
                .contains("identity_key = #{identityKey}")
                .contains("send_intent_at >= #{since}")
                .doesNotContain("first_send_at")
                .doesNotContain("second_send_at");
        assertThat(ledgerInsert)
                .contains("INSERT INTO noon_auth_identity_send_ledger")
                .contains("config_fingerprint")
                .contains("send_budget_epoch")
                .contains("generation_no")
                .contains("send_intent_at")
                .contains("WHERE id = #{recoveryId}");
    }

    @Test
    void explicitBindingEpochIsRecoveryFencedAndReopensOnlyEligibleProjectItems() {
        String recoveryLock = selectSql("selectRecoveryForUpdate");
        String successorLock = selectSql("selectWaitingSuccessorForUpdate");
        String activeRebase = updateSql("rebaseActiveRecoveryForBindingEpoch");
        String successorRebase = updateSql("rebaseWaitingSuccessorForBindingEpoch");
        String projectRebase = insertSql("rebaseProjectAuthStateForBindingEpoch");
        String sourceLessLock = selectSql("selectSourceLessProjectRecoveryItemForUpdate");
        String reopenItems = updateSql("reopenProjectItemsForBindingEpoch");

        assertThat(recoveryLock).contains("id = #{recoveryId}").contains("FOR UPDATE");
        assertThat(successorLock)
                .contains("successor_identity_slot = #{identityKey}")
                .contains("FOR UPDATE");
        assertThat(activeRebase)
                .contains("status = CASE")
                .contains("'COALESCING'")
                .contains("'WAITING_COOLDOWN'")
                .contains("send_budget_epoch = send_budget_epoch + 1")
                .contains("generation_no = 0")
                .contains("send_attempt_count = 0")
                .contains("first_send_at = NULL")
                .contains("second_send_at = NULL")
                .contains("COALESCE(second_send_at, first_send_at, #{now})")
                .contains("lease_token = NULL")
                .contains("version_no = version_no + 1")
                .doesNotContain("last_mail_uid_hash = NULL")
                .doesNotContain("last_message_id_hash = NULL")
                .doesNotContain("DELETE FROM noon_auth_identity_send_ledger");
        assertThat(successorRebase)
                .contains("status = 'WAITING_PREDECESSOR'")
                .contains("predecessor_recovery_id IS NOT NULL")
                .contains("version_no = version_no + 1")
                .doesNotContain("send_budget_epoch")
                .doesNotContain("predecessor_recovery_id =");
        assertThat(projectRebase)
                .contains("status = 'REAUTH_REQUIRED'")
                .contains("active_recovery_id = VALUES(active_recovery_id)")
                .contains("auth_version = auth_version + 1")
                .contains("binding_fingerprint = VALUES(binding_fingerprint)")
                .contains("config_fingerprint = VALUES(config_fingerprint)")
                .contains("last_failure_code = NULL")
                .contains("manual_hold_reason = NULL");
        assertThat(sourceLessLock)
                .contains("source_task_id IS NULL")
                .contains("FOR UPDATE");
        assertThat(reopenItems)
                .contains("item.status = 'PENDING'")
                .contains("item.expected_auth_version = #{expectedAuthVersion}")
                .contains("item.status IN ('PENDING', 'VALIDATING')")
                .contains("item.source_task_id IS NULL")
                .contains("task.status = 'BLOCKED_AUTH'")
                .contains("task.auth_recovery_id = item.recovery_id")
                .contains("task.is_deleted = b'0'");
    }

    @Test
    void configChangeRebasesEveryActiveStatusAndTerminalTransitionsCannotOrphanBlockedWork() {
        String configRebase = updateSql("releaseChangedManualHolds");
        String projectRebase = updateSql("releaseProjectManualHolds");
        String transitionRecovery = updateSql("transitionRecovery");
        String transitionItem = updateSql("transitionRecoveryItem");
        String requeueTerminalTasks = updateSql("requeueTerminalBlockedProjectTasksForBindingEpoch");
        String staleTerminalItems = updateSql("staleTerminalBlockedProjectItemsForBindingEpoch");
        String requeueTerminalConfigTasks = updateSql("requeueTerminalBlockedTasksOnConfigChange");
        String staleTerminalConfigItems = updateSql("staleTerminalItemsOnConfigChange");

        assertThat(configRebase)
                .contains("active_identity_slot IS NOT NULL")
                .contains("NOT (config_fingerprint <=> #{newConfigFingerprint})")
                .contains("status = CASE")
                .contains("send_budget_epoch = send_budget_epoch + 1")
                .contains("lease_token = NULL")
                .doesNotContain("status = 'MANUAL_HOLD'")
                .doesNotContain("last_mail_uid_hash = NULL")
                .doesNotContain("last_message_id_hash = NULL");
        assertThat(projectRebase)
                .contains("status IN ('REAUTH_REQUIRED', 'RECOVERING', 'MANUAL_HOLD')")
                .contains("config_fingerprint = #{newConfigFingerprint}")
                .contains("last_failure_code = NULL");
        assertThat(transitionRecovery)
                .contains("#{targetStatus} NOT IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')")
                .contains("item.status IN ('PENDING', 'VALIDATING')");
        assertThat(transitionItem)
                .contains("#{targetStatus} IN ('PENDING', 'VALIDATING')")
                .contains("item.source_task_id IS NULL")
                .contains("task.status = 'BLOCKED_AUTH'")
                .contains("task.auth_recovery_id = item.recovery_id");
        assertThat(requeueTerminalTasks)
                .contains("task.status = 'QUEUED'")
                .contains("task.auth_recovery_id = NULL")
                .contains("recovery.status IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')")
                .doesNotContain("checkpoint_cursor")
                .doesNotContain("report_export_id");
        assertThat(staleTerminalItems)
                .contains("item.status = 'STALE'")
                .contains("task.readiness_state = 'binding_epoch_requeued'");
        assertThat(requeueTerminalConfigTasks)
                .contains("task.status = 'QUEUED'")
                .contains("task.auth_recovery_id = NULL")
                .contains("NOT (state.config_fingerprint <=> #{newConfigFingerprint})")
                .contains("task.status = 'BLOCKED_AUTH'");
        assertThat(staleTerminalConfigItems)
                .contains("item.status = 'STALE'")
                .contains("task.readiness_state = 'config_epoch_requeued'");
    }

    @Test
    void persistenceRecordsExposeStableMyBatisProperties() {
        Reflector recovery = new Reflector(NoonAuthIdentityRecoveryRecord.class);
        Reflector item = new Reflector(NoonAuthRecoveryItemRecord.class);
        Reflector project = new Reflector(NoonProjectAuthStateRecord.class);

        assertThat(recovery.hasGetter("identityKey")).isTrue();
        assertThat(recovery.hasGetter("coalesceUntil")).isTrue();
        assertThat(recovery.hasGetter("leaseToken")).isTrue();
        assertThat(recovery.hasGetter("versionNo")).isTrue();
        assertThat(item.hasGetter("expectedAuthVersion")).isTrue();
        assertThat(item.hasGetter("sourceTaskId")).isTrue();
        assertThat(project.hasGetter("activeRecoveryId")).isTrue();
        assertThat(project.hasGetter("authVersion")).isTrue();
        assertThat(project.hasGetter("bindingFingerprint")).isTrue();
        assertThat(project.hasGetter("configFingerprint")).isTrue();
        assertThat(NoonProjectAuthStatus.RECOVERY_DISABLED.blocksProviderCalls()).isFalse();
    }

    private static String insertSql(String methodName) {
        return annotationSql(methodName, Insert.class);
    }

    private static String selectSql(String methodName) {
        return annotationSql(methodName, Select.class);
    }

    private static String updateSql(String methodName) {
        return annotationSql(methodName, Update.class);
    }

    private static <A extends java.lang.annotation.Annotation> String annotationSql(
            String methodName,
            Class<A> annotationType
    ) {
        Method method = Arrays.stream(NoonAuthRecoveryMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        A annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] value;
        if (annotation instanceof Insert) {
            value = ((Insert) annotation).value();
        } else if (annotation instanceof Select) {
            value = ((Select) annotation).value();
        } else if (annotation instanceof Update) {
            value = ((Update) annotation).value();
        } else {
            throw new IllegalArgumentException("Unsupported annotation: " + annotationType.getName());
        }
        return String.join(" ", value).replaceAll("\\s+", " ");
    }
}

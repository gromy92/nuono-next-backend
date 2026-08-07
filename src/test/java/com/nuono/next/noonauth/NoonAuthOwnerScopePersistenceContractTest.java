package com.nuono.next.noonauth;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonAuthOwnerScopeMapper;
import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonAuthOwnerScopePersistenceContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/init/251_noon_auth_owner_scope_successor.sql"
    );

    @Test
    void completionAndBackgroundPromotionSelectOnlyTheManifestSuccessor() {
        String completion = updateSql(NoonAuthRecoveryMapper.class, "promoteSuccessorForPredecessor");
        String background = updateSql(NoonAuthOwnerScopeMapper.class, "promoteReadySuccessors");

        for (String sql : Arrays.asList(completion, background)) {
            assertThat(sql)
                    .contains("noon_auth_owner_scope_manifest owner_scope")
                    .contains("owner_scope.predecessor_recovery_id")
                    .contains("owner_scope.status")
                    .contains("owner_scope.scoped_recovery_id")
                    .contains("successor.id");
        }
    }

    @Test
    void migrationPersistsImmutableManifestAuditAndSeparateSuccessorFence() throws Exception {
        assertInitScriptsInclude("classpath:db/init/251_noon_auth_owner_scope_successor.sql");
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_manifest`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_manifest_item`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_audit`")
                .contains("`scope_owner_user_id` BIGINT DEFAULT NULL")
                .contains("`scoped_successor_slot` CHAR(64)")
                .contains("UNIQUE KEY `uk_noon_auth_recovery_scoped_successor`")
                .contains("UNIQUE KEY `uk_noon_auth_owner_scope_manifest_active`")
                .contains("`manifest_sha256` CHAR(64)")
                .contains("`source_remaining_sha256` CHAR(64)")
                .contains("`selected_for_scope` BIT NOT NULL")
                .contains("`source_item_id` BIGINT NOT NULL")
                .contains("`source_recovery_version` BIGINT NOT NULL")
                .contains("`predecessor_recovery_version` BIGINT NOT NULL")
                .contains("`version_no` BIGINT NOT NULL DEFAULT 0");
    }

    @Test
    void runtimeValidationChecksFrozenItemsProjectsAndSourceBudget() {
        String sql = selectSql(NoonAuthOwnerScopeMapper.class, "isOwnerScopeManifestValid");

        assertThat(sql)
                .contains("manifest.status='ACTIVE'")
                .contains("frozen.source_item_id=item.id")
                .contains("state.active_recovery_id<>recovery.id")
                .contains("source.version_no=manifest.source_recovery_version")
                .contains("source.send_attempt_count=manifest.source_send_attempt_count")
                .contains("source.send_budget_epoch=manifest.source_send_budget_epoch")
                .contains("source.generation_no=manifest.source_generation_no");
    }

    @Test
    void activeOwnerManifestHasALockingSharedIdentityCoalescingFence() {
        String sql = selectSql(NoonAuthOwnerScopeMapper.class, "selectActiveOwnerScopeManifestForUpdate");

        assertThat(sql)
                .contains("noon_auth_owner_scope_manifest")
                .contains("active_identity_slot = #{identityKey}")
                .contains("FOR UPDATE");
    }

    private static String updateSql(Class<?> type, String name) {
        return annotationSql(type, name, Update.class);
    }

    private static String selectSql(Class<?> type, String name) {
        return annotationSql(type, name, Select.class);
    }

    private static <A extends java.lang.annotation.Annotation> String annotationSql(
            Class<?> type, String name, Class<A> annotationType
    ) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        A annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] value = annotation instanceof Update
                ? ((Update) annotation).value() : ((Select) annotation).value();
        return String.join(" ", value).replaceAll("\\s+", " ");
    }
}

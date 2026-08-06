package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class DataPullScopeAdmissionMapperSqlTest {

    @Test
    void readsGlobalImmutableIdentitySnapshotWithoutOperationScopedDuplication() {
        Method method = Arrays.stream(DataPullScopeAdmissionMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("listByScopeKeys"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "FROM dp_pull_scope_admission",
                "scope_namespace AS scopeNamespace",
                "source_binding_sha256 AS sourceBindingSha256",
                "first_eligible_at_utc AS firstEligibleAtUtc",
                "WHERE BINARY scope_key IN"
        );
        assertThat(sql).doesNotContain("operation_code");
    }

    @Test
    void postCutoverInsertIsBoundToTheLockedActiveCutoverAndNeverRewritesIdentity() {
        Method method = Arrays.stream(DataPullScopeAdmissionMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("insertPostCutoverAdmission"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "INSERT INTO dp_pull_scope_admission",
                "'POST_CUTOVER'",
                "FROM dp_pull_schedule_cutover cutover",
                "cutover.state = 'ACTIVE'",
                "cutover.cutover_key = BINARY #{admission.cutoverKey}",
                "ON DUPLICATE KEY UPDATE scope_key = scope_key"
        );
        assertThat(sql).doesNotContain("source_binding_sha256 = VALUES");
    }
}

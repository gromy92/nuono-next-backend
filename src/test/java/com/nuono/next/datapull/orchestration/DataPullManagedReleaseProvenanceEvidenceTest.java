package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullReleaseDatabaseMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

class DataPullManagedReleaseProvenanceEvidenceTest {
    private static final String COMMIT = "c".repeat(40);
    private static final String SCHEMA = "d".repeat(64);
    private static final String CUTOVER = "e".repeat(64);

    @TempDir
    Path directory;

    @Test
    void exactCommitTopologySchemaAndCutoverBindingVerify() throws Exception {
        assertTrue(evidence(environment(), binding(SCHEMA, CUTOVER)).verified());
    }

    @Test
    void anyBindingOrOperationCohortDriftFailsClosed() throws Exception {
        assertFalse(evidence(environment(), binding("f".repeat(64), CUTOVER)).verified());
        DataPullReleaseDatabaseBinding missingOperation = binding(SCHEMA, CUTOVER);
        missingOperation.setCutoverOperationCount(
                (long) OperationCode.values().length - 1L
        );
        assertFalse(evidence(environment(), missingOperation).verified());

        MockEnvironment commitDrift = environment();
        commitDrift.setProperty(
                DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT,
                "a".repeat(40)
        );
        assertFalse(evidence(commitDrift, binding(SCHEMA, CUTOVER)).verified());
    }

    @Test
    void missingDatabaseEvidenceAndWrongProcessDirectoryFailClosed() throws Exception {
        assertFalse(evidence(environment(), null).verified());
        DataPullReleaseDatabaseMapper mapper = Mockito.mock(
                DataPullReleaseDatabaseMapper.class
        );
        when(mapper.selectBinding()).thenReturn(binding(SCHEMA, CUTOVER));
        assertFalse(new DataPullManagedReleaseProvenanceEvidence(
                mapper,
                environment(),
                directory.resolve("different")
        ).verified());
    }

    @Test
    void databaseBindingSqlIncludesAppliedAttemptAndAllActiveCutoverManifests() {
        Method method = DataPullReleaseDatabaseMapper.class.getDeclaredMethods()[0];
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertTrue(sql.contains("'243_dp_pull_runtime.sql'"));
        assertTrue(sql.contains("'244_dp_pull_report_bounded_apply.sql'"));
        assertTrue(sql.contains("'245_dp_pull_snapshot_bounded_apply.sql'"));
        assertTrue(sql.contains("'246_dp_pull_advertising_generation.sql'"));
        assertTrue(sql.contains("'247_dp_pull_schedule_core.sql'"));
        assertTrue(sql.contains("'248_dp_pull_dp08_member_retention.sql'"));
        assertTrue(sql.contains("'250_dp_pull_advertising_campaign_pagination.sql'"));
        assertFalse(sql.contains("'247_dp_pull_schedule_bounded.sql'"));
        assertTrue(sql.contains("schema_binding.migration_count = 7"));
        assertTrue(sql.contains("ORDER BY BINARY h.migration_key"));
        assertTrue(sql.contains("h.state = 'APPLIED' AND a.state = 'APPLIED'"));
        assertTrue(sql.contains("a.attempt_no = h.attempt_no"));
        assertTrue(sql.contains("anchor_manifest_sha256"));
        assertTrue(sql.contains("expected_scope_count"));
        assertTrue(sql.contains("WHERE state = 'ACTIVE'"));
        assertTrue(sql.contains("ORDER BY BINARY operation_code"));
    }

    private DataPullManagedReleaseProvenanceEvidence evidence(
            MockEnvironment environment,
            DataPullReleaseDatabaseBinding binding
    ) {
        DataPullReleaseDatabaseMapper mapper = Mockito.mock(
                DataPullReleaseDatabaseMapper.class
        );
        when(mapper.selectBinding()).thenReturn(binding);
        return new DataPullManagedReleaseProvenanceEvidence(
                mapper, environment, directory
        );
    }

    private MockEnvironment environment() throws Exception {
        Files.writeString(directory.resolve(".env"), "NUONO_MANAGED_DP_RELEASE=1\n");
        Files.write(directory.resolve("app.jar"), new byte[] {1});
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("NUONO_NEXT_APP_DIR", directory.toString());
        environment.setProperty("NUONO_NEXT_JAR", directory.resolve("app.jar").toString());
        environment.setProperty(
                DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT, COMMIT
        );
        environment.setProperty(
                "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT", COMMIT
        );
        environment.setProperty(
                DataPullManagedReleaseProvenanceEvidence.SCHEMA_BINDING, SCHEMA
        );
        environment.setProperty(
                DataPullManagedReleaseProvenanceEvidence.CUTOVER_BINDING, CUTOVER
        );
        return environment;
    }

    private DataPullReleaseDatabaseBinding binding(String schema, String cutover) {
        DataPullReleaseDatabaseBinding value = new DataPullReleaseDatabaseBinding();
        value.setSchemaBindingSha256(schema);
        value.setCutoverBindingSha256(cutover);
        value.setCutoverOperationCount((long) OperationCode.values().length);
        return value;
    }
}

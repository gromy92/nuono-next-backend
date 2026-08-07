package com.nuono.next.datapull.cutover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullJobRegistry;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataPullRuntimeCutoverManifestTest {
    private static final String COMMIT = "c".repeat(40);
    private static final String JAR_SHA = "a".repeat(64);

    @TempDir
    Path temporary;

    @Test
    void stoppedJvmRecheckReusesBoundaryAndKeepsTheExactCohortDigest() throws Exception {
        DataPullRuntimeCutoverManifest manifest = new DataPullRuntimeCutoverManifest();
        Map<OperationCode, List<DataPullScope>> scopes = Map.of(
                OperationCode.DP01, List.of(scope("a"))
        );
        ObjectNode baseline = manifest.build(
                COMMIT, JAR_SHA,
                cohort(LocalDateTime.parse("2026-08-03T12:34:56.789"), scopes), null
        );
        Path baselinePath = temporary.resolve("baseline.json");
        new ObjectMapper().writeValue(baselinePath.toFile(), baseline);

        ObjectNode recheck = manifest.build(
                COMMIT, JAR_SHA,
                cohort(LocalDateTime.parse("2026-08-03T12:40:00.000"), scopes), baselinePath
        );

        assertEquals(baseline.get("cohortSha256"), recheck.get("cohortSha256"));
        assertEquals(11, recheck.get("operationCount").intValue());
        assertEquals(
                "2026-08-01T16:00:00.000Z",
                recheck.withArray("operations").get(0).withArray("scopes")
                        .get(0).get("reconcileAfterUtc").textValue()
        );
    }

    @Test
    void stoppedJvmRecheckRejectsAnyScopeCohortChange() throws Exception {
        DataPullRuntimeCutoverManifest manifest = new DataPullRuntimeCutoverManifest();
        ObjectNode baseline = manifest.build(
                COMMIT, JAR_SHA, cohort(LocalDateTime.parse("2026-08-03T12:34:56.789"), Map.of()), null
        );
        Path baselinePath = temporary.resolve("baseline-drift.json");
        new ObjectMapper().writeValue(baselinePath.toFile(), baseline);
        DataPullScope added = scope("b");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> manifest.build(
                        COMMIT, JAR_SHA,
                        cohort(
                                LocalDateTime.parse("2026-08-03T12:40:00.000"),
                                Map.of(OperationCode.DP01, List.of(added))
                        ),
                        baselinePath
                )
        );

        assertTrue(failure.getMessage().contains("COHORT_DRIFT"));
    }

    @Test
    void initialManifestKeepsTheEarliestSupersededLegacyWindow() throws Exception {
        DataPullScope scope = scope("a");
        LocalDateTime retainedBoundary = LocalDateTime.parse("2026-07-16T16:00:00");

        ObjectNode manifest = new DataPullRuntimeCutoverManifest().build(
                COMMIT,
                JAR_SHA,
                cohort(
                        LocalDateTime.parse("2026-08-07T02:00:00.000"),
                        Map.of(OperationCode.DP01, List.of(scope)),
                        Map.of(OperationCode.DP01, Map.of(
                                scope.getStableScopeKey(), retainedBoundary
                        ))
                ),
                null
        );

        assertEquals(
                "2026-07-16T16:00:00.000Z",
                manifest.withArray("operations").get(0).withArray("scopes")
                        .get(0).get("reconcileAfterUtc").textValue()
        );
    }

    @Test
    void commandParserRequiresAllBindingsAndAllowsOnlyOneBaseline() {
        String[] required = arguments();
        assertEquals(5, DataPullRuntimeCutoverManifestCommand.parse(required).size());
        String[] recheck = java.util.Arrays.copyOf(required, required.length + 2);
        recheck[required.length] = "--baseline-manifest";
        recheck[required.length + 1] = "/release/baseline.json";
        assertEquals(6, DataPullRuntimeCutoverManifestCommand.parse(recheck).size());

        recheck[required.length] = "--manifest-commit";
        assertThrows(
                IllegalArgumentException.class,
                () -> DataPullRuntimeCutoverManifestCommand.parse(recheck)
        );
    }

    private static DataPullRuntimeCutoverSourceCohort cohort(
            LocalDateTime observed,
            Map<OperationCode, List<DataPullScope>> overrides
    ) {
        return cohort(observed, overrides, Map.of());
    }

    private static DataPullRuntimeCutoverSourceCohort cohort(
            LocalDateTime observed,
            Map<OperationCode, List<DataPullScope>> overrides,
            Map<OperationCode, Map<String, LocalDateTime>> boundaries
    ) {
        List<DataPullJob> jobs = new ArrayList<>();
        for (OperationCode operation : OperationCode.values()) {
            jobs.add(job(operation, overrides.getOrDefault(operation, List.of())));
        }
        return new DataPullRuntimeCutoverSourceCohort(
                observed, new DataPullJobRegistry(jobs),
                new EnumMap<>(OperationCode.class), boundaries
        );
    }

    private static DataPullJob job(OperationCode operation, List<DataPullScope> scopes) {
        return new DataPullJob() {
            @Override public OperationCode operationCode() { return operation; }
            @Override public String providerChannel() { return "TEST"; }
            @Override public String initialStep() { return "TEST"; }
            @Override public List<DataPullScope> listScopes() { return scopes; }
            @Override public AdvanceResult advance(ExecutionContext context) { return null; }
        };
    }

    private static DataPullScope scope(String digestCharacter) {
        return new DataPullScope(
                "TEST", 307L, null, "account", null, null, null,
                "TEST-" + digestCharacter.repeat(64)
        );
    }

    private static String[] arguments() {
        return new String[] {
            DataPullRuntimeCutoverManifestCommand.COMMAND,
            "--env-file", "/app/.env",
            "--candidate-jar", "/release/backend.jar",
            "--manifest-commit", COMMIT,
            "--expected-jar-sha256", JAR_SHA,
            "--evidence-file", "/release/evidence.json",
        };
    }
}

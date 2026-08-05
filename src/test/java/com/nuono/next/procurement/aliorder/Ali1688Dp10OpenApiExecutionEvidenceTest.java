package com.nuono.next.procurement.aliorder;

import static com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiProbeEvidenceFixture.COMMIT;
import static com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiProbeEvidenceFixture.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.orchestration.DataPullRuntimeReleaseRequirement;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class Ali1688Dp10OpenApiExecutionEvidenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freshCandidateExpiresButSameSlotLaterRestartRetainsStrictBindings() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceFixture fixture = fixture();
        fixture.write();
        String evidenceSha = fixture.evidenceSha();
        MockEnvironment environment = fixture.runtimeEnvironment(evidenceSha);

        Ali1688Dp10OpenApiExecutionEvidence fresh =
                new Ali1688Dp10OpenApiExecutionEvidence(
                        fixture.properties,
                        fixture.runtimeProperties,
                        environment,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        fixture.appDirectory
                );
        Ali1688Dp10OpenApiExecutionEvidence laterRestart =
                new Ali1688Dp10OpenApiExecutionEvidence(
                        fixture.properties,
                        fixture.runtimeProperties,
                        environment,
                        Clock.fixed(NOW.plusSeconds(601), ZoneOffset.UTC),
                        fixture.appDirectory
                );

        assertEquals(
                DataPullRuntimeReleaseRequirement.DP10_OPEN_API_EXECUTION_CONTRACT,
                fresh.requirement()
        );
        assertTrue(fresh.verified());
        assertTrue(laterRestart.verified());
        assertFalse(fixture.verifyFresh(evidenceSha, COMMIT, NOW.plusSeconds(601)));
        assertTrue(Set.of(DataPullRuntimeReleaseRequirement.values()).contains(
                DataPullRuntimeReleaseRequirement.DP10_MODIFIED_TIME_VISIBILITY_CONTRACT
        ));
    }

    @Test
    void runtimeEvidenceRejectsEffectiveDatabaseSecretAndEvidenceBindingDrift()
            throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceFixture fixture = fixture();
        fixture.write();
        String evidenceSha = fixture.evidenceSha();
        MockEnvironment environment = fixture.runtimeEnvironment(evidenceSha);

        assertTrue(fixture.runtimeEvidence(environment).verified());

        environment.setProperty("spring.datasource.url", "jdbc:mysql://other/db");
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty("spring.datasource.url", "jdbc:mysql://db/nuono");

        environment.setProperty("spring.datasource.hikari.connection-timeout", "30000");
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty("spring.datasource.hikari.connection-timeout", "5000");

        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.connectTimeout",
                "30000"
        );
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.connectTimeout",
                "5000"
        );

        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.socketTimeout",
                "30000"
        );
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.socketTimeout",
                "300000"
        );

        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.queryTimeoutKillsConnection",
                "false"
        );
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.queryTimeoutKillsConnection",
                "true"
        );

        environment.setProperty("mybatis.configuration.default-executor-type", "BATCH");
        assertFalse(fixture.runtimeEvidence(environment).verified());
        environment.setProperty("mybatis.configuration.default-executor-type", "SIMPLE");

        fixture.properties.setAppSecret("runtime-secret-drift");
        assertFalse(fixture.runtimeEvidence(environment).verified());
        fixture.properties.setAppSecret("app-secret");

        fixture.runtimeProperties.setLeaseSeconds(270L);
        assertFalse(fixture.runtimeEvidence(environment).verified());
        fixture.runtimeProperties.setLeaseSeconds(300L);

        environment.setProperty(
                "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE",
                fixture.evidence.resolveSibling("other-evidence.json").toString()
        );
        assertFalse(fixture.runtimeEvidence(environment).verified());
    }

    private Ali1688Dp10OpenApiProbeEvidenceFixture fixture() throws Exception {
        return Ali1688Dp10OpenApiProbeEvidenceFixture.create(temporaryDirectory);
    }
}

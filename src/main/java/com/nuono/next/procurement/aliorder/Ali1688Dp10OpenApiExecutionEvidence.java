package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeReleaseEvidence;
import com.nuono.next.datapull.orchestration.DataPullRuntimeReleaseRequirement;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Verifies durable artifact bindings; cutover freshness and visibility are separate gates. */
@Component
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Ali1688Dp10OpenApiExecutionEvidence
        implements DataPullRuntimeReleaseEvidence {
    private static final String FILE =
            "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE";
    private static final String SHA =
            "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256";
    private static final String COMMIT =
            "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT";
    private static final String ENV_ATTESTATION =
            "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE";
    private static final String APP_DIR = "NUONO_NEXT_APP_DIR";
    private static final String JAR = "NUONO_NEXT_JAR";

    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final DataPullRuntimeProperties runtimeProperties;
    private final Environment environment;
    private final Clock clock;
    private final Path processDirectory;

    @Autowired
    public Ali1688Dp10OpenApiExecutionEvidence(
            Ali1688HistoricalOrderOpenApiProperties properties,
            DataPullRuntimeProperties runtimeProperties,
            Environment environment
    ) {
        this(
                properties,
                runtimeProperties,
                environment,
                Clock.systemUTC(),
                Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        );
    }

    Ali1688Dp10OpenApiExecutionEvidence(
            Ali1688HistoricalOrderOpenApiProperties properties,
            DataPullRuntimeProperties runtimeProperties,
            Environment environment,
            Clock clock
    ) {
        this(
                properties,
                runtimeProperties,
                environment,
                clock,
                Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        );
    }

    Ali1688Dp10OpenApiExecutionEvidence(
            Ali1688HistoricalOrderOpenApiProperties properties,
            DataPullRuntimeProperties runtimeProperties,
            Environment environment,
            Clock clock,
            Path processDirectory
    ) {
        this.properties = properties;
        this.runtimeProperties = runtimeProperties;
        this.environment = environment;
        this.clock = clock;
        this.processDirectory = processDirectory;
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.DP10_OPEN_API_EXECUTION_CONTRACT;
    }

    @Override
    public boolean verified() {
        if (!properties.isRequiredForDp10()
                || !properties.hasProductionDp10Configuration()) return false;
        String file = value(FILE);
        String sha = value(SHA);
        String commit = value(COMMIT);
        String envAttestation = value(ENV_ATTESTATION);
        String appDir = value(APP_DIR);
        String jar = value(JAR);
        if (file.isEmpty() || sha.isEmpty() || commit.isEmpty()
                || envAttestation.isEmpty() || appDir.isEmpty() || jar.isEmpty()) return false;
        Path evidenceFile = path(file);
        Path jarFile = path(jar);
        if (!Ali1688Dp10RuntimeEnvironmentAttestation.verify(
                processDirectory,
                path(appDir),
                jarFile,
                evidenceFile,
                path(envAttestation)
        )) return false;
        if (!Ali1688Dp10RuntimeEnvironmentBinding.verify(
                path(appDir).resolve(".env"),
                environment,
                properties,
                runtimeProperties
        )) return false;
        return Ali1688Dp10OpenApiProbeEvidenceSupport.verifyBound(
                evidenceFile,
                sha,
                commit,
                jarFile,
                properties,
                clock,
                new ObjectMapper()
        );
    }

    private String value(String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.trim();
    }

    private Path path(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException("DP10_RUNTIME_PATH_INVALID");
        return path.normalize();
    }
}

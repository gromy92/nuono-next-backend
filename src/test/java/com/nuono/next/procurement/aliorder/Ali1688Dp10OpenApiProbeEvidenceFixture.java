package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.mock.env.MockEnvironment;

final class Ali1688Dp10OpenApiProbeEvidenceFixture {
    static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");
    static final String COMMIT = "c".repeat(40);
    static final String NONCE = "d".repeat(64);

    final Path appDirectory;
    final Path evidence;
    final Path envAttestation;
    final Path envFile;
    final Path jar;
    final Ali1688HistoricalOrderOpenApiProperties properties;
    final DataPullRuntimeProperties runtimeProperties;

    private Ali1688Dp10OpenApiProbeEvidenceFixture(
            Path appDirectory,
            Path evidence,
            Path envAttestation,
            Path jar,
            Ali1688HistoricalOrderOpenApiProperties properties,
            DataPullRuntimeProperties runtimeProperties
    ) {
        this.appDirectory = appDirectory;
        this.evidence = evidence;
        this.envAttestation = envAttestation;
        this.envFile = appDirectory.resolve(".env");
        this.jar = jar;
        this.properties = properties;
        this.runtimeProperties = runtimeProperties;
    }

    static Ali1688Dp10OpenApiProbeEvidenceFixture create(Path temporaryDirectory)
            throws Exception {
        Path app = secureDirectory(temporaryDirectory.resolve("slot-" + System.nanoTime()));
        Path releaseRoot = secureDirectory(app.resolve(".release-evidence"));
        Path release = secureDirectory(releaseRoot.resolve(COMMIT + "-" + NONCE));
        Path jar = app.resolve("candidate.jar");
        Files.write(jar, "candidate".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(jar, PosixFilePermissions.fromString("rw-------"));
        return new Ali1688Dp10OpenApiProbeEvidenceFixture(
                app,
                release.resolve("dp10-openapi-execution.json"),
                release.resolve("runtime-env.sha256"),
                jar,
                properties(),
                new DataPullRuntimeProperties()
        );
    }

    void write() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceSupport.write(
                evidence,
                NONCE,
                COMMIT,
                jar,
                Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(jar),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    void writeAuthWaitIsolation() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceSupport.writeAuthWaitIsolation(
                evidence,
                NONCE,
                COMMIT,
                jar,
                Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(jar),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    String evidenceSha() throws Exception {
        return Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(evidence);
    }

    boolean verifyFresh(String evidenceSha, String commit, Instant now) {
        return Ali1688Dp10OpenApiProbeEvidenceSupport.verifyFresh(
                evidence,
                evidenceSha,
                commit,
                jar,
                properties,
                Clock.fixed(now, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    boolean verifyBound(String evidenceSha, String commit, Instant now) {
        return Ali1688Dp10OpenApiProbeEvidenceSupport.verifyBound(
                evidence,
                evidenceSha,
                commit,
                jar,
                properties,
                Clock.fixed(now, ZoneOffset.UTC),
                new ObjectMapper()
        );
    }

    MockEnvironment runtimeEnvironment(String evidenceSha) throws Exception {
        Map<String, String> raw = rawEnvironment(evidenceSha);
        StringBuilder content = new StringBuilder();
        raw.forEach((key, value) -> content.append(key).append('=').append(value).append('\n'));
        Files.writeString(envFile, content);
        Files.setPosixFilePermissions(envFile, PosixFilePermissions.fromString("rw-------"));
        String envSha = Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(envFile);
        Files.createFile(
                envAttestation,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")
                )
        );
        Files.writeString(envAttestation, envSha + "\n");
        MockEnvironment environment = new MockEnvironment();
        raw.forEach(environment::setProperty);
        environment.setActiveProfiles("local-db");
        environment.setProperty("nuono.stage", "local-db");
        environment.setProperty("spring.datasource.url", "jdbc:mysql://db/nuono");
        environment.setProperty("spring.datasource.username", "runtime-user");
        environment.setProperty("spring.datasource.password", "runtime-password");
        environment.setProperty(
                "spring.datasource.driver-class-name",
                "com.mysql.cj.jdbc.Driver"
        );
        environment.setProperty("spring.datasource.hikari.connection-timeout", "5000");
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.connectTimeout",
                "5000"
        );
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.socketTimeout",
                "300000"
        );
        environment.setProperty(
                "spring.datasource.hikari.data-source-properties.queryTimeoutKillsConnection",
                "true"
        );
        environment.setProperty("mybatis.configuration.default-executor-type", "SIMPLE");
        return environment;
    }

    Ali1688Dp10OpenApiExecutionEvidence runtimeEvidence(MockEnvironment environment) {
        return new Ali1688Dp10OpenApiExecutionEvidence(
                properties,
                runtimeProperties,
                environment,
                Clock.fixed(NOW, ZoneOffset.UTC),
                appDirectory
        );
    }

    private Map<String, String> rawEnvironment(String evidenceSha) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("NUONO_MANAGED_DP_RELEASE", "1");
        raw.put("SPRING_PROFILES_ACTIVE", "local-db");
        raw.put("NUONO_NEXT_DB_URL", "jdbc:mysql://db/nuono");
        raw.put("NUONO_NEXT_DB_USERNAME", "runtime-user");
        raw.put("NUONO_NEXT_DB_PASSWORD", "runtime-password");
        raw.put("NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_OPEN_API_ENABLED", "true");
        raw.put("NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_OPEN_API_REQUIRED_FOR_DP10", "true");
        raw.put("NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_OPEN_API_APP_KEY", "app-key");
        raw.put("NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_OPEN_API_APP_SECRET", "app-secret");
        raw.put("NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_TOKEN_CIPHER_SECRET", "token-secret");
        raw.put(
                "NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_OPEN_API_REDIRECT_URI",
                "https://www.nuoon.com/ai/api/procurement/ali1688-orders/"
                        + "authorizations/open-api/callback"
        );
        raw.put("NUONO_NEXT_APP_DIR", appDirectory.toString());
        raw.put("NUONO_NEXT_JAR", jar.toString());
        raw.put("NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE", evidence.toString());
        raw.put("NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256", evidenceSha);
        raw.put("NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT", COMMIT);
        raw.put(
                "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE",
                envAttestation.toString()
        );
        return raw;
    }

    private static Path secureDirectory(Path path) throws Exception {
        return Files.createDirectory(
                path,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")
                )
        );
    }

    private static Ali1688HistoricalOrderOpenApiProperties properties() {
        Ali1688HistoricalOrderOpenApiProperties properties =
                new Ali1688HistoricalOrderOpenApiProperties();
        properties.setEnabled(true);
        properties.setRequiredForDp10(true);
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.setTokenCipherSecret("token-secret");
        properties.setRedirectUri(
                "https://www.nuoon.com/ai/api/procurement/ali1688-orders/"
                        + "authorizations/open-api/callback"
        );
        return properties;
    }
}

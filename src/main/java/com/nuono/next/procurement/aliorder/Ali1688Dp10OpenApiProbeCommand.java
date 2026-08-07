package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.web.client.RestTemplateBuilder;

/** Candidate-Jar one-shot command. It returns before Spring, Web, and scheduling can start. */
public final class Ali1688Dp10OpenApiProbeCommand {
    public static final String COMMAND = "dp10-openapi-contract-probe";
    private static final String NONCE_ENV = "NUONO_DP10_OPEN_API_PROBE_NONCE";
    private static final Set<String> OPTIONS = Set.of(
            "--env-file",
            "--candidate-jar",
            "--manifest-commit",
            "--expected-jar-sha256",
            "--evidence-file"
    );
    private static final Set<String> ISOLATED_AUTH_WAIT_FAILURES = Set.of(
            "PROBE_AUTH_REFRESH_REQUIRED",
            "PROBE_AUTH_REFRESH_UNPROVEN",
            "PROBE_AUTH_REFRESH_RISK_CONTROL",
            "PROBE_AUTH_REFRESH_RATE_LIMITED",
            "PROBE_AUTH_REFRESH_RETRYABLE"
    );

    private Ali1688Dp10OpenApiProbeCommand() {
    }

    public static boolean handles(String[] args) {
        return args != null && args.length > 0 && COMMAND.equals(args[0]);
    }

    public static int run(String[] args) {
        try {
            Map<String, String> options = parse(args);
            String manifestCommit = requireHex(
                    options.get("--manifest-commit"),
                    40,
                    "PROBE_COMMIT_INVALID"
            );
            String expectedJarSha = requireHex(
                    options.get("--expected-jar-sha256"),
                    64,
                    "PROBE_JAR_SHA_INVALID"
            );
            String nonce = requireNonce();
            Path envFile = Path.of(options.get("--env-file")).toAbsolutePath().normalize();
            Path candidateJar = Path.of(options.get("--candidate-jar"))
                    .toAbsolutePath().normalize();
            Path evidenceFile = Path.of(options.get("--evidence-file"))
                    .toAbsolutePath().normalize();
            if (!expectedJarSha.equals(
                    Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(candidateJar))) {
                throw new IllegalStateException("PROBE_JAR_SHA_MISMATCH");
            }
            Ali1688Dp10OpenApiProbeEnvironment environment =
                    Ali1688Dp10OpenApiProbeEnvironment.load(envFile);
            Ali1688HistoricalOrderOpenApiProperties properties =
                    environment.openApiProperties();
            requireProductionConfiguration(properties);
            Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection =
                    new Ali1688Dp10OpenApiProbeAuthorizationSource().select(environment);
            Ali1688Dp10OpenApiProbeAuthorizationUpdater authorizationUpdater =
                    Ali1688Dp10OpenApiProbeAuthorizationUpdater.create(
                            environment,
                            selection
                    );
            ObjectMapper mapper = new ObjectMapper();
            HttpAli1688HistoricalOrderProvider provider =
                    new HttpAli1688HistoricalOrderProvider(
                            properties,
                            new Ali1688OpenApiSigner(),
                            new Ali1688TokenCipher(properties),
                            mapper,
                            new RestTemplateBuilder(),
                            authorizationUpdater
                    );
            Clock clock = Clock.systemUTC();
            Ali1688Dp10OpenApiProbeRunner.Proof proof;
            try {
                proof = new Ali1688Dp10OpenApiProbeRunner(provider, clock).run(
                        selection.authorization(),
                        selection.providerOrderNo(),
                        properties.getPageSize()
                );
            } catch (Ali1688Dp10OpenApiProbeRunner.ProbeFailure failure) {
                if (!isIsolatedAuthWait(failure.code())) throw failure;
                Ali1688Dp10OpenApiProbeEvidenceSupport.writeAuthWaitIsolation(
                        evidenceFile,
                        nonce,
                        manifestCommit,
                        candidateJar,
                        expectedJarSha,
                        properties,
                        clock,
                        mapper
                );
                System.out.println("DP10_OPEN_API_AUTH_REFRESH=AUTH_WAIT");
                System.out.println(
                        "DP10_OPEN_API_EXECUTION_CONTRACT=AUTH_WAIT_ISOLATED"
                );
                return 0;
            }
            Ali1688Dp10OpenApiProbeEvidenceSupport.write(
                    evidenceFile,
                    nonce,
                    manifestCommit,
                    candidateJar,
                    expectedJarSha,
                    properties,
                    clock,
                    mapper
            );
            System.out.println("DP10_OPEN_API_AUTH_REFRESH="
                    + (proof.authorizationRefreshed() ? "REFRESHED" : "CURRENT"));
            System.out.println("DP10_OPEN_API_EXECUTION_CONTRACT=CONTRACT_PROVEN");
            return 0;
        } catch (Ali1688Dp10OpenApiProbeRunner.ProbeFailure failure) {
            return fail(failure.code());
        } catch (Exception failure) {
            return fail("PROBE_EXECUTION_FAILED");
        }
    }

    static Map<String, String> parse(String[] args) {
        if (!handles(args) || args.length != 1 + OPTIONS.size() * 2) {
            throw new IllegalArgumentException("PROBE_ARGUMENTS_INVALID");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if (!OPTIONS.contains(option) || parsed.containsKey(option)
                    || value == null || value.isBlank() || value.startsWith("--")) {
                throw new IllegalArgumentException("PROBE_ARGUMENTS_INVALID");
            }
            parsed.put(option, value);
        }
        if (!parsed.keySet().equals(OPTIONS)) {
            throw new IllegalArgumentException("PROBE_ARGUMENTS_INVALID");
        }
        return Map.copyOf(parsed);
    }

    static boolean isIsolatedAuthWait(String failureCode) {
        return ISOLATED_AUTH_WAIT_FAILURES.contains(failureCode);
    }

    private static void requireProductionConfiguration(
            Ali1688HistoricalOrderOpenApiProperties properties
    ) {
        if (properties == null || !properties.isRequiredForDp10()
                || !properties.hasProductionDp10Configuration()) {
            throw new IllegalStateException("PROBE_OPEN_API_CONFIG_INVALID");
        }
    }

    private static String requireNonce() {
        String nonce = System.getenv(NONCE_ENV);
        if (nonce == null || !nonce.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PROBE_NONCE_INVALID");
        }
        return nonce;
    }

    private static String requireHex(String value, int length, String code) {
        if (value == null || !value.matches("[0-9a-f]{" + length + "}")) {
            throw new IllegalStateException(code);
        }
        return value;
    }

    private static int fail(String code) {
        String sanitized = code != null && code.matches("[A-Z0-9_]+")
                ? code
                : "PROBE_EXECUTION_FAILED";
        System.err.println("DP10_OPEN_API_EXECUTION_CONTRACT=FAIL:" + sanitized);
        return 22;
    }
}

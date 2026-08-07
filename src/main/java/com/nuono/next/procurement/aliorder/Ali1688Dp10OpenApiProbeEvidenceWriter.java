package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/** Writes immutable candidate-bound DP-10 execution evidence. */
final class Ali1688Dp10OpenApiProbeEvidenceWriter {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Set<PosixFilePermission> FILE_MODE =
            PosixFilePermissions.fromString("rw-------");

    private Ali1688Dp10OpenApiProbeEvidenceWriter() {
    }

    static void writeExecutionProof(
            Path evidenceFile,
            String nonce,
            String manifestCommit,
            Path candidateJar,
            String expectedJarSha256,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper
    ) throws IOException {
        write(
                evidenceFile,
                nonce,
                manifestCommit,
                candidateJar,
                expectedJarSha256,
                properties,
                clock,
                mapper,
                Ali1688Dp10OpenApiProbeEvidenceSupport.EXECUTION_PROVEN,
                Ali1688Dp10OpenApiProbeEvidenceSupport.PROVEN
        );
    }

    static void writeAuthWaitIsolation(
            Path evidenceFile,
            String nonce,
            String manifestCommit,
            Path candidateJar,
            String expectedJarSha256,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper
    ) throws IOException {
        write(
                evidenceFile,
                nonce,
                manifestCommit,
                candidateJar,
                expectedJarSha256,
                properties,
                clock,
                mapper,
                Ali1688Dp10OpenApiProbeEvidenceSupport.AUTH_WAIT_ISOLATED,
                Ali1688Dp10OpenApiProbeEvidenceSupport.NOT_EXECUTED_AUTH_WAIT
        );
    }

    private static void write(
            Path evidenceFile,
            String nonce,
            String manifestCommit,
            Path candidateJar,
            String expectedJarSha256,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper,
            String releaseDisposition,
            String contractState
    ) throws IOException {
        requirePattern(nonce, SHA256, "PROBE_NONCE_INVALID");
        requirePattern(manifestCommit, COMMIT, "PROBE_COMMIT_INVALID");
        requirePattern(expectedJarSha256, SHA256, "PROBE_JAR_SHA_INVALID");
        if (!expectedJarSha256.equals(
                Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(candidateJar))) {
            throw new IllegalStateException("PROBE_JAR_SHA_MISMATCH");
        }
        if (!Ali1688Dp10OpenApiProbeEvidenceSupport.secureDirectory(evidenceFile.getParent())) {
            throw new IllegalStateException("PROBE_DIRECTORY_MODE_INVALID");
        }

        Instant verifiedAt = clock.instant();
        ObjectNode evidence = mapper.createObjectNode();
        evidence.put("schema", Ali1688Dp10OpenApiProbeEvidenceSupport.SCHEMA);
        evidence.put("type", Ali1688Dp10OpenApiProbeEvidenceSupport.TYPE);
        evidence.put("release_disposition", releaseDisposition);
        evidence.put("nonce_sha256", Ali1688Dp10OpenApiProbeEvidenceSupport.sha256Text(nonce));
        evidence.put("manifest_commit", manifestCommit);
        evidence.put("candidate_jar_sha256", expectedJarSha256);
        evidence.put("endpoint_fingerprint_sha256",
                Ali1688Dp10OpenApiProbeEvidenceSupport.endpointFingerprint(properties));
        evidence.put("app_key_fingerprint_sha256",
                Ali1688Dp10OpenApiProbeEvidenceSupport.appKeyFingerprint(properties));
        evidence.put("current_list_contract", contractState);
        evidence.put("history_list_contract", contractState);
        evidence.put("detail_contract", contractState);
        evidence.put("verified_at", verifiedAt.toString());
        evidence.put("expires_at", verifiedAt.plus(
                Ali1688Dp10OpenApiProbeEvidenceSupport.VALIDITY).toString());

        byte[] payload = (mapper.writeValueAsString(evidence) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.createFile(evidenceFile, PosixFilePermissions.asFileAttribute(FILE_MODE));
        Files.write(evidenceFile, payload, StandardOpenOption.WRITE);
        if (!Files.getPosixFilePermissions(evidenceFile).equals(FILE_MODE)) {
            throw new IllegalStateException("PROBE_EVIDENCE_MODE_INVALID");
        }
    }

    private static void requirePattern(String value, Pattern pattern, String code) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(code);
        }
    }
}

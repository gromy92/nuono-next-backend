package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Writes and verifies candidate-bound DP-10 execution or isolated auth-wait evidence. */
final class Ali1688Dp10OpenApiProbeEvidenceSupport {
    static final String SCHEMA = "nuono.dp10-openapi-execution-contract/v1";
    static final String TYPE = "DP10_OPEN_API_EXECUTION_CONTRACT";
    static final String PROVEN = "CONTRACT_PROVEN";
    static final String EXECUTION_PROVEN = "EXECUTION_PROVEN";
    static final String AUTH_WAIT_ISOLATED = "AUTH_WAIT_ISOLATED";
    static final String NOT_EXECUTED_AUTH_WAIT = "NOT_EXECUTED_AUTH_WAIT";
    static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Set<String> FIELDS = Set.of(
            "schema", "type", "release_disposition", "nonce_sha256",
            "manifest_commit",
            "candidate_jar_sha256", "endpoint_fingerprint_sha256",
            "app_key_fingerprint_sha256", "current_list_contract",
            "history_list_contract", "detail_contract", "verified_at", "expires_at"
    );
    private static final Set<PosixFilePermission> FILE_MODE =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIRECTORY_MODE =
            PosixFilePermissions.fromString("rwx------");

    private Ali1688Dp10OpenApiProbeEvidenceSupport() {
    }

    static boolean verifyFresh(
            Path evidenceFile,
            String expectedEvidenceSha256,
            String expectedCommit,
            Path candidateJar,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper
    ) {
        return verifyBoundEvidence(
                evidenceFile,
                expectedEvidenceSha256,
                expectedCommit,
                candidateJar,
                properties,
                clock,
                mapper,
                true
        );
    }

    static boolean verifyBound(
            Path evidenceFile,
            String expectedEvidenceSha256,
            String expectedCommit,
            Path candidateJar,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper
    ) {
        return verifyBoundEvidence(
                evidenceFile,
                expectedEvidenceSha256,
                expectedCommit,
                candidateJar,
                properties,
                clock,
                mapper,
                false
        );
    }

    private static boolean verifyBoundEvidence(
            Path evidenceFile,
            String expectedEvidenceSha256,
            String expectedCommit,
            Path candidateJar,
            Ali1688HistoricalOrderOpenApiProperties properties,
            Clock clock,
            ObjectMapper mapper,
            boolean requireFresh
    ) {
        try {
            requirePattern(expectedEvidenceSha256, SHA256, "PROBE_EVIDENCE_SHA_INVALID");
            requirePattern(expectedCommit, COMMIT, "PROBE_COMMIT_INVALID");
            if (!secureFile(evidenceFile)
                    || !expectedEvidenceSha256.equals(sha256File(evidenceFile))) return false;
            JsonNode evidence = mapper.readTree(Files.readAllBytes(evidenceFile));
            if (evidence == null || !evidence.isObject() || !exactFields(evidence)) return false;
            String jarSha = text(evidence, "candidate_jar_sha256");
            String releaseDisposition = text(evidence, "release_disposition");
            String currentContract = text(evidence, "current_list_contract");
            String historyContract = text(evidence, "history_list_contract");
            String detailContract = text(evidence, "detail_contract");
            boolean executionProven = EXECUTION_PROVEN.equals(releaseDisposition)
                    && PROVEN.equals(currentContract)
                    && PROVEN.equals(historyContract)
                    && PROVEN.equals(detailContract);
            boolean authWaitIsolated = AUTH_WAIT_ISOLATED.equals(releaseDisposition)
                    && NOT_EXECUTED_AUTH_WAIT.equals(currentContract)
                    && NOT_EXECUTED_AUTH_WAIT.equals(historyContract)
                    && NOT_EXECUTED_AUTH_WAIT.equals(detailContract);
            if (!SCHEMA.equals(text(evidence, "schema"))
                    || !TYPE.equals(text(evidence, "type"))
                    || !expectedCommit.equals(text(evidence, "manifest_commit"))
                    || (!executionProven && !authWaitIsolated)
                    || !matches(text(evidence, "nonce_sha256"), SHA256)
                    || !matches(jarSha, SHA256)
                    || !jarSha.equals(sha256File(candidateJar))
                    || !endpointFingerprint(properties).equals(
                            text(evidence, "endpoint_fingerprint_sha256"))
                    || !appKeyFingerprint(properties).equals(
                            text(evidence, "app_key_fingerprint_sha256"))) return false;
            Instant verifiedAt = Instant.parse(text(evidence, "verified_at"));
            Instant expiresAt = Instant.parse(text(evidence, "expires_at"));
            Instant now = clock.instant();
            return VALIDITY.equals(Duration.between(verifiedAt, expiresAt))
                    && !verifiedAt.isAfter(now.plusSeconds(30))
                    && (!requireFresh || expiresAt.isAfter(now));
        } catch (RuntimeException | IOException invalidEvidence) {
            return false;
        }
    }

    static String endpointFingerprint(Ali1688HistoricalOrderOpenApiProperties properties) {
        String canonical = String.join("\n",
                normalizeGateway(properties.getApiGatewayBaseUrl()),
                normalizeGateway(properties.getTokenUrlTemplate()),
                normalizeGateway(properties.getAuthorizeUrl()),
                normalizeGateway(properties.getRedirectUri()),
                trim(properties.getSite()),
                trim(properties.getApiVersion()),
                trim(properties.getBuyerOrderListNamespace()),
                trim(properties.getBuyerOrderListApiName()),
                trim(properties.getBuyerOrderDetailNamespace()),
                trim(properties.getBuyerOrderDetailApiName()),
                trim(properties.getPageNumberParameterName()),
                trim(properties.getPageSizeParameterName()),
                trim(properties.getModifiedFromParameterName()),
                trim(properties.getModifiedToParameterName()),
                trim(properties.getHistoryParameterName()),
                trim(properties.getModifiedFromFormat()),
                trim(properties.getProviderZoneId()),
                String.valueOf(properties.getTimeoutSeconds()),
                String.valueOf(properties.getPageSize()),
                String.valueOf(properties.getStateTtlSeconds()),
                "access_token", "_aop_timestamp", "_aop_signature", "orderId"
        );
        return sha256Text(canonical);
    }

    static String appKeyFingerprint(Ali1688HistoricalOrderOpenApiProperties properties) {
        return sha256Text(trim(properties.getAppKey()));
    }

    static String sha256File(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("PROBE_FILE_INVALID");
        }
        MessageDigest digest = sha256();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static boolean secureFile(Path path) throws IOException {
        return path != null
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.getPosixFilePermissions(path).equals(FILE_MODE)
                && ((Number) Files.getAttribute(
                        path,
                        "unix:nlink",
                        LinkOption.NOFOLLOW_LINKS
                )).longValue() == 1L
                && secureDirectory(path.getParent());
    }

    static boolean secureDirectory(Path path) throws IOException {
        return path != null
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.getPosixFilePermissions(path).equals(DIRECTORY_MODE);
    }

    private static boolean exactFields(JsonNode evidence) {
        Set<String> actual = new LinkedHashSet<>();
        Iterator<String> names = evidence.fieldNames();
        names.forEachRemaining(actual::add);
        return FIELDS.equals(actual);
    }

    private static String normalizeGateway(String value) {
        String appKeyTemplateToken = "{appKey}";
        String appKeyTemplateSentinel = "nuono-app-key-template";
        String normalizedValue = trim(value).replace(
                appKeyTemplateToken,
                appKeyTemplateSentinel
        );
        URI uri = URI.create(normalizedValue).normalize();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        int port = uri.getPort();
        if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
            port = -1;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        path = path.replace(appKeyTemplateSentinel, appKeyTemplateToken);
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
        return scheme + "://" + host + (port < 0 ? "" : ":" + port)
                + path + query + fragment;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.textValue();
    }

    private static boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }

    private static void requirePattern(String value, Pattern pattern, String code) {
        if (!matches(value, pattern)) throw new IllegalArgumentException(code);
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    static String sha256Text(String value) {
        return hex(sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            int unsigned = item & 0xff;
            value.append(digits[unsigned >>> 4]).append(digits[unsigned & 0x0f]);
        }
        return value.toString();
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception impossible) { throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible); }
    }
}

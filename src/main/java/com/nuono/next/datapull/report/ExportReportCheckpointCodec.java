package com.nuono.next.datapull.report;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class ExportReportCheckpointCodec {

    private static final String VERSION = "v3";
    private static final String NONE = "-";

    String encode(ExportReportCheckpoint checkpoint) {
        DownloadedReportArtifact artifact = checkpoint.getArtifact();
        ReportArtifactAuthority authority = checkpoint.getArtifactAuthority();
        return String.join(
                "|",
                VERSION,
                checkpoint.getPhase().name(),
                String.valueOf(checkpoint.getConsecutiveRetryAttempt()),
                checkpoint.isCreateOutcomeUnknown() ? "1" : "0",
                encodeText(checkpoint.getStableRequestKey()),
                checkpoint.getDownloadLocatorReference() == null
                        ? NONE
                        : encodeText(checkpoint.getDownloadLocatorReference()),
                authority == null ? NONE : encodeText(authority.getStableRequestKey()),
                authority == null ? NONE : encodeText(authority.getRemoteHandle()),
                authority == null ? NONE : String.valueOf(authority.getDeclaredRowCount()),
                artifact == null ? NONE : encodeText(artifact.getArtifactKey()),
                artifact == null ? NONE : artifact.getSha256(),
                artifact == null ? NONE : String.valueOf(artifact.getContentLength())
        );
    }

    ExportReportCheckpoint decode(String encoded) {
        String value = ReportContract.requireIdentity(encoded, "checkpoint");
        String[] parts = value.split("\\|", -1);
        if (parts.length != 12 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("unsupported report checkpoint");
        }
        ExportReportCheckpoint.Phase phase = ExportReportCheckpoint.Phase.valueOf(parts[1]);
        int retryAttempt = Integer.parseInt(parts[2]);
        boolean createOutcomeUnknown = parseBoolean(parts[3]);
        String requestKey = decodeText(parts[4]);
        String locatorReference = NONE.equals(parts[5]) ? null : decodeText(parts[5]);
        boolean hasAuthority = !NONE.equals(parts[6]);
        if (hasAuthority != !NONE.equals(parts[7]) || hasAuthority != !NONE.equals(parts[8])) {
            throw new IllegalArgumentException("partial report authority checkpoint");
        }
        ReportArtifactAuthority authority = hasAuthority
                ? ReportArtifactAuthority.restored(
                        decodeText(parts[6]),
                        decodeText(parts[7]),
                        Long.parseLong(parts[8])
                )
                : null;
        boolean hasArtifact = !NONE.equals(parts[9]);
        if (hasArtifact != !NONE.equals(parts[10]) || hasArtifact != !NONE.equals(parts[11])) {
            throw new IllegalArgumentException("partial artifact checkpoint");
        }
        DownloadedReportArtifact artifact = hasArtifact
                ? DownloadedReportArtifact.complete(
                        decodeText(parts[9]),
                        parts[10],
                        Long.parseLong(parts[11])
                ).bind(java.util.Objects.requireNonNull(authority, "artifact authority"))
                : null;
        return ExportReportCheckpoint.restored(
                phase,
                requestKey,
                retryAttempt,
                createOutcomeUnknown,
                locatorReference,
                phase == ExportReportCheckpoint.Phase.DOWNLOAD ? authority : null,
                artifact
        );
    }

    private boolean parseBoolean(String value) {
        if ("1".equals(value)) {
            return true;
        }
        if ("0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("invalid create outcome checkpoint flag");
    }

    private String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String decodeText(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}

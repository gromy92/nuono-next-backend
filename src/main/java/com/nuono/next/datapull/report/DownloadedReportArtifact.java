package com.nuono.next.datapull.report;

import java.util.Locale;
import java.util.regex.Pattern;

/** Durable, secret-free reference to one completely downloaded provider file. */
public final class DownloadedReportArtifact {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String artifactKey;
    private final String sha256;
    private final long contentLength;
    private final ReportArtifactAuthority authority;

    private DownloadedReportArtifact(
            String artifactKey,
            String sha256,
            long contentLength,
            ReportArtifactAuthority authority
    ) {
        this.artifactKey = ReportContract.requireIdentity(artifactKey, "artifactKey");
        String normalizedSha = ReportContract.requireIdentity(sha256, "sha256")
                .toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(normalizedSha).matches()) {
            throw new IllegalArgumentException("sha256 must be a lowercase 64-character digest");
        }
        if (contentLength <= 0L) {
            throw new IllegalArgumentException("non-empty artifacts require a positive contentLength");
        }
        this.sha256 = normalizedSha;
        this.contentLength = contentLength;
        this.authority = authority;
    }

    public static DownloadedReportArtifact complete(
            String artifactKey,
            String sha256,
            long contentLength
    ) {
        return new DownloadedReportArtifact(artifactKey, sha256, contentLength, null);
    }

    DownloadedReportArtifact bind(ReportArtifactAuthority reportAuthority) {
        if (authority != null) {
            throw new IllegalStateException("report artifact authority is already bound");
        }
        return new DownloadedReportArtifact(
                artifactKey,
                sha256,
                contentLength,
                java.util.Objects.requireNonNull(reportAuthority, "reportAuthority")
        );
    }

    public String getArtifactKey() {
        return artifactKey;
    }

    public String getSha256() {
        return sha256;
    }

    public long getContentLength() {
        return contentLength;
    }

    public ReportArtifactAuthority getAuthority() {
        return authority;
    }
}

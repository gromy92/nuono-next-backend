package com.nuono.next.noon;

import java.util.Objects;

/** Bounded result metadata from the binary HTTP body subscriber. */
final class NoonBinaryDownloadBody {
    private final long streamedBytes;
    private final byte[] errorPreview;
    private final boolean acceptedFinalResponse;

    NoonBinaryDownloadBody(
            long streamedBytes,
            byte[] errorPreview,
            boolean acceptedFinalResponse
    ) {
        if (streamedBytes < 0L) {
            throw new IllegalArgumentException("streamedBytes must not be negative");
        }
        this.streamedBytes = streamedBytes;
        this.errorPreview = Objects.requireNonNull(errorPreview, "errorPreview").clone();
        this.acceptedFinalResponse = acceptedFinalResponse;
    }

    long streamedBytes() {
        return streamedBytes;
    }

    byte[] errorPreview() {
        return errorPreview.clone();
    }

    boolean acceptedFinalResponse() {
        return acceptedFinalResponse;
    }
}

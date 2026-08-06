package com.nuono.next.noon;

/** Verified response framing supplied before the first durable binary chunk. */
public final class NoonBinaryDownloadMetadata {
    private final long responseStart;
    private final long responseLength;
    private final long totalLength;
    private final String entityValidator;

    public NoonBinaryDownloadMetadata(
            long responseStart,
            long responseLength,
            long totalLength,
            String entityValidator
    ) {
        if (responseStart < 0L || responseLength <= 0L || totalLength <= 0L
                || Math.addExact(responseStart, responseLength) != totalLength) {
            throw new IllegalArgumentException("binary download response range is invalid");
        }
        this.responseStart = responseStart;
        this.responseLength = responseLength;
        this.totalLength = totalLength;
        this.entityValidator = normalize(entityValidator);
    }

    public long responseStart() { return responseStart; }
    public long responseLength() { return responseLength; }
    public long totalLength() { return totalLength; }
    public String entityValidator() { return entityValidator; }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

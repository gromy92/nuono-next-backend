package com.nuono.next.noon;

/**
 * Synchronous bounded sink for a single binary Noon response.
 *
 * <p>The transport never retains a complete response. Each accepted chunk is consumed before the
 * next chunk is requested. Abort deliberately keeps already durable chunks so a later attempt can
 * reconcile them without relying on a process-local file.</p>
 */
public interface NoonBinaryDownloadSink {

    int preferredChunkBytes();

    long maximumBytes();

    /** Last fully durable byte boundary; the transport requests only the remaining suffix. */
    default long resumeByteOffset() {
        return 0L;
    }

    /** Strong ETag or Last-Modified value bound by an earlier suffix request, when available. */
    default String resumeEntityValidator() {
        return null;
    }

    /** Binds verified HTTP framing before the first new chunk becomes durable. */
    default void begin(NoonBinaryDownloadMetadata metadata) {
        // Non-resumable test sinks need no durable response binding.
    }

    void accept(byte[] bytes, int offset, int length);

    void complete();

    default void abort(Throwable failure) {
        // Durable implementations retain matching partial chunks for restart recovery.
    }
}

package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonBoundedDownloadExchangeTest {

    @Test
    void resumedRequestOwnsRangeAndIfRangeHeaders() {
        NoonBinaryDownloadSink sink = new StubSink(1_048_576L, "\"export-v1\"");

        HttpRequest request = NoonBoundedDownloadExchange.request(
                URI.create("https://example.test/export.csv"),
                "project",
                true,
                Map.of("Accept", "text/plain"),
                new NoonRequestHeaders(null, null, null, null),
                sink,
                Duration.ofSeconds(30)
        );

        assertEquals("bytes=1048576-", request.headers().firstValue("Range").orElseThrow());
        assertEquals("\"export-v1\"", request.headers().firstValue("If-Range").orElseThrow());
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElseThrow());
    }

    @Test
    void callerCannotOverrideTransportRangeHeaders() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonBoundedDownloadExchange.request(
                        URI.create("https://example.test/export.csv"),
                        null,
                        false,
                        Map.of("Range", "bytes=0-1"),
                        new NoonRequestHeaders(null, null, null, null),
                        new StubSink(0L, null),
                        Duration.ofSeconds(30)
                )
        );
    }

    private static final class StubSink implements NoonBinaryDownloadSink {
        private final long offset;
        private final String validator;

        private StubSink(long offset, String validator) {
            this.offset = offset;
            this.validator = validator;
        }

        @Override public int preferredChunkBytes() { return 1_048_576; }
        @Override public long maximumBytes() { return 536_870_912L; }
        @Override public long resumeByteOffset() { return offset; }
        @Override public String resumeEntityValidator() { return validator; }
        @Override public void accept(byte[] bytes, int byteOffset, int length) { }
        @Override public void complete() { }
    }
}

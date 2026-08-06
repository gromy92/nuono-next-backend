package com.nuono.next.noon;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

final class NoonBinaryDownloadTestSupport {
    private NoonBinaryDownloadTestSupport() { }

    static HttpResponse.ResponseInfo response(
            int status,
            Map<String, List<String>> headers
    ) {
        return new HttpResponse.ResponseInfo() {
            @Override public int statusCode() { return status; }
            @Override public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    static final class RecordingSubscription implements Flow.Subscription {
        boolean cancelled;
        @Override public void request(long value) { }
        @Override public void cancel() { cancelled = true; }
    }

    static final class RecordingSink implements NoonBinaryDownloadSink {
        private final int chunkBytes;
        private final long maximumBytes;
        private final List<byte[]> chunks = new ArrayList<>();
        boolean completed;
        boolean aborted;
        boolean begun;
        private final long resumeOffset;
        private final String validator;
        NoonBinaryDownloadMetadata metadata;

        RecordingSink(int chunkBytes, long maximumBytes) {
            this(chunkBytes, maximumBytes, 0L, null);
        }

        RecordingSink(
                int chunkBytes,
                long maximumBytes,
                long resumeOffset,
                String validator
        ) {
            this.chunkBytes = chunkBytes;
            this.maximumBytes = maximumBytes;
            this.resumeOffset = resumeOffset;
            this.validator = validator;
        }

        @Override public int preferredChunkBytes() { return chunkBytes; }
        @Override public long maximumBytes() { return maximumBytes; }
        @Override public long resumeByteOffset() { return resumeOffset; }
        @Override public String resumeEntityValidator() { return validator; }
        @Override public void begin(NoonBinaryDownloadMetadata value) {
            begun = true;
            metadata = value;
        }
        @Override public void complete() { completed = true; }
        @Override public void abort(Throwable failure) { aborted = true; }

        @Override
        public void accept(byte[] bytes, int offset, int length) {
            chunks.add(java.util.Arrays.copyOfRange(bytes, offset, offset + length));
        }

        List<Integer> chunkLengths() {
            return chunks.stream().map(value -> value.length).collect(Collectors.toList());
        }

        byte[] joined() {
            int length = chunks.stream().mapToInt(value -> value.length).sum();
            byte[] result = new byte[length];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
    }
}

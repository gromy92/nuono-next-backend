package com.nuono.next.noon;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Streams a verified final response while holding the last chunk until exact EOF. */
final class NoonBinaryStreamingSubscriber
        implements HttpResponse.BodySubscriber<NoonBinaryDownloadBody> {
    private static final int PREVIEW_BYTES = NoonBoundedDownloadBodyHandler.ERROR_PREVIEW_BYTES;
    private static final int SIGNATURE_BYTES = 512;
    private final NoonBinaryDownloadSink sink;
    private final NoonBinaryDownloadMetadata metadata;
    private final CompletableFuture<NoonBinaryDownloadBody> result = new CompletableFuture<>();
    private final ByteArrayOutputStream preview = new ByteArrayOutputStream(PREVIEW_BYTES);
    private Flow.Subscription subscription;
    private byte[] chunk;
    private int used;
    private long received;
    private boolean sinkBegun;
    private boolean previewMode;
    private boolean finished;

    NoonBinaryStreamingSubscriber(
            NoonBinaryDownloadSink sink,
            NoonBinaryDownloadMetadata metadata
    ) {
        this.sink = sink;
        this.metadata = metadata;
        this.chunk = new byte[sink.preferredChunkBytes()];
    }

    @Override
    public CompletionStage<NoonBinaryDownloadBody> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
        if (subscription != null) {
            value.cancel();
            return;
        }
        subscription = value;
        value.request(1L);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (finished) {
            return;
        }
        try {
            for (ByteBuffer buffer : buffers) {
                if (previewMode) {
                    copyPreview(buffer);
                } else {
                    copyBinary(buffer);
                }
                if (finished) {
                    return;
                }
            }
            subscription.request(1L);
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    private void copyBinary(ByteBuffer source) {
        while (source.hasRemaining()) {
            if (used == chunk.length) {
                flushBuffered();
                if (previewMode) {
                    copyPreview(source);
                    return;
                }
            }
            int copied = Math.min(source.remaining(), chunk.length - used);
            long nextReceived = Math.addExact(received, copied);
            if (nextReceived > metadata.responseLength()) {
                throw contract("NOON_BINARY_CONTENT_LENGTH_MISMATCH");
            }
            source.get(chunk, used, copied);
            used += copied;
            received = nextReceived;
            requireNotUnlabelledGzip();
        }
    }

    private void flushBuffered() {
        if (used == 0) {
            return;
        }
        if (!sinkBegun && looksLikeInterceptionBody(chunk, used)) {
            previewMode = true;
            appendPreview(chunk, 0, used);
            used = 0;
            return;
        }
        beginSink();
        sink.accept(chunk, 0, used);
        chunk = new byte[sink.preferredChunkBytes()];
        used = 0;
    }

    private void beginSink() {
        if (sinkBegun) {
            return;
        }
        if (metadata.entityValidator() == null) {
            throw contract("NOON_BINARY_ENTITY_VALIDATOR_REQUIRED");
        }
        String expectedValidator = normalize(sink.resumeEntityValidator());
        if (expectedValidator != null
                && !expectedValidator.equals(metadata.entityValidator())) {
            throw contract("NOON_BINARY_ENTITY_VALIDATOR_CHANGED");
        }
        sink.begin(metadata);
        sinkBegun = true;
    }

    private void requireNotUnlabelledGzip() {
        if (!sinkBegun && used >= 2
                && (chunk[0] & 0xff) == 0x1f
                && (chunk[1] & 0xff) == 0x8b) {
            throw contract("NOON_BINARY_GZIP_WITHOUT_HEADER_UNSUPPORTED");
        }
    }

    private boolean looksLikeInterceptionBody(byte[] bytes, int length) {
        int start = signatureStart(bytes, length);
        int previewLength = Math.min(length - start, SIGNATURE_BYTES);
        String value = new String(
                bytes, start, Math.max(0, previewLength), StandardCharsets.UTF_8
        ).toLowerCase(Locale.ROOT);
        // Governed artifacts are CSV or spreadsheet/binary containers, never JSON.
        return value.startsWith("<")
                || value.startsWith("{")
                || value.startsWith("[");
    }

    private int signatureStart(byte[] bytes, int length) {
        int start = length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
        while (start < length && (bytes[start] & 0xff) <= 0x20) {
            start++;
        }
        return start;
    }

    private void copyPreview(ByteBuffer source) {
        int copied = Math.min(source.remaining(), PREVIEW_BYTES - preview.size());
        if (copied > 0) {
            byte[] bytes = new byte[copied];
            source.get(bytes);
            preview.write(bytes, 0, bytes.length);
        }
        source.position(source.limit());
        if (preview.size() == PREVIEW_BYTES) {
            finishPreview();
        }
    }

    private void appendPreview(byte[] bytes, int offset, int length) {
        int copied = Math.min(length, PREVIEW_BYTES - preview.size());
        if (copied > 0) {
            preview.write(bytes, offset, copied);
        }
        if (preview.size() == PREVIEW_BYTES) {
            finishPreview();
        }
    }

    @Override
    public void onError(Throwable failure) {
        fail(failure);
    }

    @Override
    public void onComplete() {
        if (finished) {
            return;
        }
        try {
            if (previewMode || (!sinkBegun && looksLikeInterceptionBody(chunk, used))) {
                if (!previewMode) {
                    appendPreview(chunk, 0, used);
                }
                finishPreview();
                return;
            }
            if (received != metadata.responseLength()) {
                throw contract("NOON_BINARY_CONTENT_LENGTH_MISMATCH");
            }
            flushBuffered();
            if (previewMode) {
                finishPreview();
                return;
            }
            sink.complete();
            finished = true;
            result.complete(new NoonBinaryDownloadBody(received, new byte[0], true));
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    private void finishPreview() {
        if (finished) {
            return;
        }
        finished = true;
        if (subscription != null) {
            subscription.cancel();
        }
        result.complete(new NoonBinaryDownloadBody(0L, preview.toByteArray(), false));
    }

    private void fail(Throwable failure) {
        if (finished) {
            return;
        }
        finished = true;
        if (subscription != null) {
            subscription.cancel();
        }
        sink.abort(failure);
        result.completeExceptionally(failure);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NoonBinaryDownloadContractException contract(String code) {
        return new NoonBinaryDownloadContractException(code);
    }
}

package com.nuono.next.noon;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Captures only enough rejected-body bytes for auth/risk classification. */
final class NoonBinaryPreviewSubscriber
        implements HttpResponse.BodySubscriber<NoonBinaryDownloadBody> {
    private final CompletableFuture<NoonBinaryDownloadBody> result = new CompletableFuture<>();
    private final ByteArrayOutputStream preview;
    private final int maximumBytes;
    private Flow.Subscription subscription;
    private boolean finished;

    NoonBinaryPreviewSubscriber(int maximumBytes) {
        this.maximumBytes = maximumBytes;
        this.preview = new ByteArrayOutputStream(maximumBytes);
    }

    @Override public CompletionStage<NoonBinaryDownloadBody> getBody() { return result; }

    @Override
    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        value.request(1L);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (finished) {
            return;
        }
        for (ByteBuffer source : buffers) {
            int copied = Math.min(source.remaining(), maximumBytes - preview.size());
            if (copied > 0) {
                byte[] bytes = new byte[copied];
                source.get(bytes);
                preview.write(bytes, 0, bytes.length);
            }
            source.position(source.limit());
            if (preview.size() == maximumBytes) {
                finish();
                return;
            }
        }
        subscription.request(1L);
    }

    @Override
    public void onError(Throwable failure) {
        if (!finished) {
            finished = true;
            result.completeExceptionally(failure);
        }
    }

    @Override public void onComplete() { finish(); }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (subscription != null) {
            subscription.cancel();
        }
        result.complete(new NoonBinaryDownloadBody(0L, preview.toByteArray(), false));
    }
}

/** Rejects an invalid framing contract before requesting any body bytes. */
final class NoonBinaryRejectingSubscriber
        implements HttpResponse.BodySubscriber<NoonBinaryDownloadBody> {
    private final CompletableFuture<NoonBinaryDownloadBody> result;

    NoonBinaryRejectingSubscriber(RuntimeException failure) {
        result = CompletableFuture.failedFuture(failure);
    }

    @Override public CompletionStage<NoonBinaryDownloadBody> getBody() { return result; }
    @Override public void onSubscribe(Flow.Subscription subscription) { subscription.cancel(); }
    @Override public void onNext(List<ByteBuffer> item) { }
    @Override public void onError(Throwable throwable) { }
    @Override public void onComplete() { }
}

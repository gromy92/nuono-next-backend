package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.nuono.next.noon.NoonBinaryDownloadTestSupport.response;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import com.nuono.next.noon.NoonBinaryDownloadTestSupport.RecordingSink;
import com.nuono.next.noon.NoonBinaryDownloadTestSupport.RecordingSubscription;
import org.junit.jupiter.api.Test;

class NoonBoundedDownloadBodyHandlerTest {

    @Test
    void successfulBodyIsDeliveredInFixedChunksWithoutWholeBodyBuffering() {
        RecordingSink sink = new RecordingSink(4, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(
                200, Map.of(
                        "content-length", List.of("9"),
                        "etag", List.of("\"v1\"")
                )
        ));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4, 5, 6, 7, 8, 9})
        ));
        subscriber.onComplete();

        NoonBinaryDownloadBody result = subscriber.getBody().toCompletableFuture().join();
        assertEquals(9L, result.streamedBytes());
        assertTrue(result.acceptedFinalResponse());
        assertEquals(List.of(4, 4, 1), sink.chunkLengths());
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, sink.joined());
        assertTrue(sink.completed);
        assertFalse(subscription.cancelled);
    }

    @Test
    void bodyIsCancelledAsSoonAsTheConfiguredCeilingWouldBeCrossed() {
        RecordingSink sink = new RecordingSink(4, 5);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(
                        200, Map.of("content-length", List.of("6"))
                ));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6})));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals("NOON_BINARY_DOWNLOAD_LIMIT_EXCEEDED", failure.getCause().getMessage());
        assertTrue(failure.getCause() instanceof NoonBinaryDownloadContractException);
        assertTrue(subscription.cancelled);
        assertFalse(sink.aborted);
        assertFalse(sink.completed);
    }

    @Test
    void nonSuccessBodyKeepsOnlyABoundedPreviewAndNeverTouchesArtifactSink() {
        RecordingSink sink = new RecordingSink(4, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(429, Map.of()));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(
                new byte[NoonBoundedDownloadBodyHandler.ERROR_PREVIEW_BYTES + 10]
        )));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertEquals(NoonBoundedDownloadBodyHandler.ERROR_PREVIEW_BYTES,
                body.errorPreview().length);
        assertTrue(sink.chunkLengths().isEmpty());
        assertFalse(sink.completed);
        assertFalse(body.acceptedFinalResponse());
    }

    @Test
    void compressedSuccessIsRejectedInsteadOfBeingExpandedWithoutABound() {
        RecordingSink sink = new RecordingSink(4, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(
                        response(200, Map.of("content-encoding", List.of("gzip")))
                );
        subscriber.onSubscribe(new RecordingSubscription());

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals("NOON_BINARY_CONTENT_ENCODING_UNSUPPORTED",
                failure.getCause().getMessage());
        assertTrue(failure.getCause() instanceof NoonBinaryDownloadContractException);
    }

    @Test
    void acceptedTwoHundredHtmlIsPreviewedAndNeverBeginsTheArtifact() {
        byte[] html = "<html><body>login required</body></html>"
                .getBytes(StandardCharsets.UTF_8);
        RecordingSink sink = new RecordingSink(4, 200);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(200, Map.of(
                        "content-length", List.of(Integer.toString(html.length)),
                        "content-type", List.of("text/html")
                )));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(html)));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertFalse(body.acceptedFinalResponse());
        assertTrue(new String(body.errorPreview(), StandardCharsets.UTF_8).contains("login"));
        assertFalse(sink.begun);
        assertFalse(sink.completed);
    }

    @Test
    void disguisedBomPrefixedJsonRiskBodyIsPreviewedWithoutPersistence() {
        byte[] json = ("\ufeff  \n[{\"error\":\"access denied\"}]")
                .getBytes(StandardCharsets.UTF_8);
        RecordingSink sink = new RecordingSink(64, 200);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(200, Map.of(
                        "content-length", List.of(Integer.toString(json.length)),
                        "content-type", List.of("application/octet-stream"),
                        "etag", List.of("\"risk-page\"")
                )));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(json)));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertFalse(body.acceptedFinalResponse());
        assertTrue(new String(body.errorPreview(), StandardCharsets.UTF_8)
                .contains("access denied"));
        assertFalse(sink.begun);
        assertFalse(sink.completed);
    }

    @Test
    void disguisedXmlRiskBodyIsPreviewedWithoutPersistence() {
        byte[] xml = "  <Error><Code>AccessDenied</Code></Error>"
                .getBytes(StandardCharsets.UTF_8);
        RecordingSink sink = new RecordingSink(64, 200);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(200, Map.of(
                        "content-length", List.of(Integer.toString(xml.length)),
                        "content-type", List.of("application/octet-stream"),
                        "etag", List.of("\"risk-page\"")
                )));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(xml)));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertFalse(body.acceptedFinalResponse());
        assertTrue(new String(body.errorPreview(), StandardCharsets.UTF_8)
                .contains("AccessDenied"));
        assertFalse(sink.begun);
        assertFalse(sink.completed);
    }

    @Test
    void binaryResponseWithoutEntityValidatorFailsBeforeAnyChunkIsDurable() {
        RecordingSink sink = new RecordingSink(16, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(
                        200, Map.of("content-length", List.of("4"))
                ));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4})));
        subscriber.onComplete();

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals("NOON_BINARY_ENTITY_VALIDATOR_REQUIRED", failure.getCause().getMessage());
        assertTrue(sink.chunkLengths().isEmpty());
        assertFalse(sink.begun);
        assertFalse(sink.completed);
    }

    @Test
    void weakEtagFallsBackOnlyToAValidLastModifiedValidator() {
        String lastModified = "Tue, 04 Aug 2026 08:00:00 GMT";
        RecordingSink sink = new RecordingSink(16, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(200, Map.of(
                        "content-length", List.of("4"),
                        "etag", List.of("W/\"v1\""),
                        "last-modified", List.of(lastModified)
                )));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4})));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertTrue(body.acceptedFinalResponse());
        assertEquals(lastModified, sink.metadata.entityValidator());
        assertTrue(sink.completed);
    }

    @Test
    void resumeAcceptsOnlyTheExactFinalRangeAndBindsItsValidator() {
        RecordingSink sink = new RecordingSink(4, 20, 4L, "\"v1\"");
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(206, Map.of(
                        "content-length", List.of("5"),
                        "content-range", List.of("bytes 4-8/9"),
                        "etag", List.of("\"v1\"")
                )));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {5, 6, 7, 8, 9})));
        subscriber.onComplete();

        NoonBinaryDownloadBody body = subscriber.getBody().toCompletableFuture().join();
        assertTrue(body.acceptedFinalResponse());
        assertEquals(4L, sink.metadata.responseStart());
        assertEquals(9L, sink.metadata.totalLength());
        assertEquals("\"v1\"", sink.metadata.entityValidator());
        assertEquals(List.of(4, 1), sink.chunkLengths());
    }

    @Test
    void shortResponseFailsBeforeItsHeldFinalChunkCanBecomeDurable() {
        RecordingSink sink = new RecordingSink(16, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(
                        200, Map.of(
                                "content-length", List.of("9"),
                                "etag", List.of("\"v1\"")
                        )
                ));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8})));
        subscriber.onComplete();

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals("NOON_BINARY_CONTENT_LENGTH_MISMATCH", failure.getCause().getMessage());
        assertTrue(subscription.cancelled);
        assertTrue(sink.chunkLengths().isEmpty());
        assertFalse(sink.completed);
    }

    @Test
    void timeoutKeepsOnlyFullEarlierChunksSoTheNextAttemptCanRangeResume() {
        RecordingSink sink = new RecordingSink(4, 20);
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(
                        200, Map.of(
                                "content-length", List.of("9"),
                                "etag", List.of("\"v1\"")
                        )
                ));
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8})));
        subscriber.onError(new IllegalStateException("deadline"));

        assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals(List.of(4), sink.chunkLengths());
        assertTrue(sink.begun);
        assertTrue(sink.aborted);
        assertFalse(sink.completed);
    }

}

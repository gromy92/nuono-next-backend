package com.nuono.next.noon;

import static com.nuono.next.noon.NoonBinaryDownloadTestSupport.response;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noon.NoonBinaryDownloadTestSupport.RecordingSink;
import com.nuono.next.noon.NoonBinaryDownloadTestSupport.RecordingSubscription;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class NoonBinaryValidatorRejectionTest {

    @Test
    void resumeRejectsAChangedEntityValidatorBeforeAnyNewChunkIsDurable() {
        RecordingSink sink = new RecordingSink(4, 20, 4L, "\"v1\"");
        HttpResponse.BodySubscriber<NoonBinaryDownloadBody> subscriber =
                new NoonBoundedDownloadBodyHandler(sink).apply(response(206, Map.of(
                        "content-length", List.of("5"),
                        "content-range", List.of("bytes 4-8/9"),
                        "etag", List.of("\"v2\"")
                )));
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {5, 6, 7, 8, 9})));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertEquals("NOON_BINARY_ENTITY_VALIDATOR_CHANGED", failure.getCause().getMessage());
        assertTrue(sink.chunkLengths().isEmpty());
        assertFalse(sink.begun);
        assertFalse(sink.completed);
        assertTrue(subscription.cancelled);
    }
}

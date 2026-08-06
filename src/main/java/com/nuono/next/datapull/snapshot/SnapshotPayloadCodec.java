package com.nuono.next.datapull.snapshot;

/**
 * Explicit, schema-bound codec for durable snapshot staging payloads.
 *
 * <p>Implementations must use a versioned domain format and reject unknown versions or fields
 * that would change the item meaning. Java native serialization, reflective polymorphic type
 * loading, secrets, credentials, and provider session material are forbidden. Encoding should be
 * deterministic for operational inspection, while semantic replay equality remains defined by
 * {@link SnapshotItemDescriptor}.</p>
 */
public interface SnapshotPayloadCodec<T> {
    String encode(T item);

    T decode(String payload);
}

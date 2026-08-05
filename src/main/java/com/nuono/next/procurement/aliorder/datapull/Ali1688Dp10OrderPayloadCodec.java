package com.nuono.next.procurement.aliorder.datapull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Encodes staged business payload and rechecks its fingerprint on every read. */
final class Ali1688Dp10OrderPayloadCodec {
    private final ObjectMapper objectMapper;

    Ali1688Dp10OrderPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    String encode(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        if (order == null) return null;
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException failure) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_ENCODE_FAILED");
        }
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot decode(String payload, String fingerprint) {
        if (payload == null) return null;
        if (!Ali1688Dp10Digest.sha256(payload).equals(fingerprint)) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_INTEGRITY_FAILED");
        }
        try {
            return objectMapper.readValue(
                    payload,
                    Ali1688HistoricalOrderProvider.OrderSnapshot.class
            );
        } catch (JsonProcessingException failure) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_DECODE_FAILED");
        }
    }

    String fingerprint(String payload) {
        return payload == null ? Ali1688Dp10Digest.sha256("<null>") : Ali1688Dp10Digest.sha256(payload);
    }

    EncodedPayload encodeBounded(
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            int maximumBytes
    ) {
        if (order == null) return new EncodedPayload(null, fingerprint(null), false);
        CappedDigestOutputStream output = new CappedDigestOutputStream(maximumBytes);
        try {
            objectMapper.writeValue(output, order);
            return output.result();
        } catch (IOException | RuntimeException failure) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_PAYLOAD_ENCODE_FAILED");
        }
    }

    static final class EncodedPayload {
        private final String payload;
        private final String fingerprint;
        private final boolean tooLarge;

        private EncodedPayload(String payload, String fingerprint, boolean tooLarge) {
            this.payload = payload;
            this.fingerprint = fingerprint;
            this.tooLarge = tooLarge;
        }

        String getPayload() { return payload; }
        String getFingerprint() { return fingerprint; }
        boolean isTooLarge() { return tooLarge; }
    }

    /** Hashes every canonical byte while retaining at most the configured stage capacity. */
    private static final class CappedDigestOutputStream extends OutputStream {
        private final int maximumBytes;
        private final ByteArrayOutputStream retained;
        private final MessageDigest digest;
        private boolean tooLarge;

        private CappedDigestOutputStream(int maximumBytes) {
            if (maximumBytes <= 0) throw new IllegalArgumentException("maximumBytes");
            this.maximumBytes = maximumBytes;
            this.retained = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException unavailable) {
                throw new IllegalStateException("SHA-256 is required", unavailable);
            }
        }

        @Override
        public void write(int value) {
            byte item = (byte) value;
            digest.update(item);
            if (retained.size() < maximumBytes) retained.write(value);
            else tooLarge = true;
        }

        @Override
        public void write(byte[] values, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, values.length);
            digest.update(values, offset, length);
            int remaining = maximumBytes - retained.size();
            int accepted = Math.min(Math.max(remaining, 0), length);
            if (accepted > 0) retained.write(values, offset, accepted);
            if (accepted < length) tooLarge = true;
        }

        private EncodedPayload result() {
            return new EncodedPayload(
                    tooLarge ? null : retained.toString(StandardCharsets.UTF_8),
                    hex(digest.digest()),
                    tooLarge);
        }

        private String hex(byte[] bytes) {
            StringBuilder value = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        }
    }
}

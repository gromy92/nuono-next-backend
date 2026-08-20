package com.nuono.next.noonauth;

import java.time.Instant;
import java.util.Optional;

public interface NoonAuthCheckpointVault {
    void save(long recoveryId, int generation, Kind kind, String payload, Instant expiresAt);

    Optional<Checkpoint> load(long recoveryId, Instant now);

    void clear(long recoveryId);

    enum Kind { OTP_CHALLENGE, IDENTITY_GRANT }

    final class Checkpoint {
        private final int generation;
        private final Kind kind;
        private final String payload;
        private final Instant expiresAt;

        public Checkpoint(int generation, Kind kind, String payload, Instant expiresAt) {
            this.generation = generation;
            this.kind = kind;
            this.payload = payload;
            this.expiresAt = expiresAt;
        }

        public int getGeneration() { return generation; }
        public Kind getKind() { return kind; }
        public String getPayload() { return payload; }
        public Instant getExpiresAt() { return expiresAt; }
    }
}

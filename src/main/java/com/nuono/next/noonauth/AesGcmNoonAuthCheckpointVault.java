package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryCheckpointMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public final class AesGcmNoonAuthCheckpointVault implements NoonAuthCheckpointVault {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final NoonAuthRecoveryCheckpointMapper mapper;
    private final NoonAuthRecoveryProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AesGcmNoonAuthCheckpointVault(
            NoonAuthRecoveryCheckpointMapper mapper,
            NoonAuthRecoveryProperties properties
    ) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public void save(long recoveryId, int generation, Kind kind, String payload, Instant expiresAt) {
        requireConfigured();
        if (recoveryId <= 0 || generation <= 0 || kind == null || payload == null || expiresAt == null) {
            throw new IllegalArgumentException("invalid Noon auth checkpoint");
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        NoonAuthRecoveryCheckpointRecord record = new NoonAuthRecoveryCheckpointRecord();
        record.setRecoveryId(recoveryId);
        record.setGenerationNo(generation);
        record.setCheckpointKind(kind.name());
        record.setKeyVersion(properties.getCheckpointKeyVersion());
        record.setInitializationVector(iv);
        record.setCiphertext(crypt(Cipher.ENCRYPT_MODE, recoveryId, generation, kind, iv,
                payload.getBytes(StandardCharsets.UTF_8)));
        record.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        if (mapper.upsert(record) < 1) {
            throw new IllegalStateException("Noon auth checkpoint was not persisted");
        }
    }

    @Override
    public Optional<Checkpoint> load(long recoveryId, Instant now) {
        NoonAuthRecoveryCheckpointRecord row = mapper.select(recoveryId);
        if (row == null) {
            return Optional.empty();
        }
        Instant expiresAt = row.getExpiresAt().toInstant(ZoneOffset.UTC);
        if (!expiresAt.isAfter(now)) {
            mapper.delete(recoveryId);
            return Optional.empty();
        }
        requireConfigured();
        Kind kind = Kind.valueOf(row.getCheckpointKind());
        byte[] clear = crypt(Cipher.DECRYPT_MODE, recoveryId, row.getGenerationNo(), kind,
                row.getInitializationVector(), row.getCiphertext());
        return Optional.of(new Checkpoint(row.getGenerationNo(), kind,
                new String(clear, StandardCharsets.UTF_8), expiresAt));
    }

    @Override
    public void clear(long recoveryId) {
        mapper.delete(recoveryId);
    }

    private byte[] crypt(int mode, long recoveryId, int generation, Kind kind, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD((recoveryId + ":" + generation + ":" + kind.name())
                    .getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect Noon auth checkpoint", exception);
        }
    }

    private byte[] key() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(
                properties.getCheckpointCipherSecret().getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(properties.getCheckpointCipherSecret())) {
            throw new IllegalStateException("Noon auth checkpoint cipher secret is required");
        }
    }
}

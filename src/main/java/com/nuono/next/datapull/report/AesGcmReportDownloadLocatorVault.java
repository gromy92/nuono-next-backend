package com.nuono.next.datapull.report;

import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM vault; a missing or malformed key blocks runtime startup. */
public final class AesGcmReportDownloadLocatorVault implements ReportDownloadLocatorVault {
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int LOCATOR_MAX_UTF8_BYTES = 16 * 1024;
    private static final String KEY_PREFIX = "rpt-loc-v1-";

    private final DataPullReportLocatorMapper mapper;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public AesGcmReportDownloadLocatorVault(
            DataPullReportLocatorMapper mapper,
            String keyBase64
    ) {
        this(mapper, keyBase64, new SecureRandom(), Clock.systemUTC());
    }

    AesGcmReportDownloadLocatorVault(
            DataPullReportLocatorMapper mapper,
            String keyBase64,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        byte[] decoded = decodeKey(keyBase64);
        this.key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
    }

    @Override
    public String store(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String rawLocator
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        byte[] locator = requirePersistableLocator(rawLocator);
        String reference = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        String handleSha = ReportDigestSupport.sha256(safeHandle.getValue());
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        byte[] ciphertext = encrypt(
                locator,
                iv,
                aad(reference, safeIntent.getTaskId(), safeIntent.getStableRequestKey(), handleSha)
        );
        ReportDownloadLocatorRecord row = new ReportDownloadLocatorRecord();
        row.setLocatorReference(reference);
        row.setTaskId(safeIntent.getTaskId());
        row.setStableRequestKey(safeIntent.getStableRequestKey());
        row.setRemoteHandleSha256(handleSha);
        row.setInitializationVector(iv);
        row.setEncryptedLocator(ciphertext);
        row.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("REPORT_LOCATOR_PERSIST_FAILED");
        }
        return reference;
    }

    @Override
    public String resolve(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String locatorReference
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        String reference = ReportContract.requireIdentity(locatorReference, "locatorReference");
        ReportDownloadLocatorRecord row = mapper.selectByReference(reference);
        if (row == null) {
            throw new ReportLocatorNotFoundException();
        }
        String handleSha = ReportDigestSupport.sha256(safeHandle.getValue());
        if (safeIntent.getTaskId() != row.getTaskId()
                || !safeIntent.getStableRequestKey().equals(row.getStableRequestKey())
                || !handleSha.equals(row.getRemoteHandleSha256())) {
            throw new IllegalStateException("REPORT_LOCATOR_CONTEXT_MISMATCH");
        }
        byte[] plaintext = decrypt(
                row.getEncryptedLocator(),
                row.getInitializationVector(),
                aad(reference, safeIntent.getTaskId(), safeIntent.getStableRequestKey(), handleSha)
        );
        return ReportContract.requireIdentity(
                new String(plaintext, StandardCharsets.UTF_8),
                "decryptedLocator"
        );
    }

    private byte[] encrypt(byte[] plaintext, byte[] iv, byte[] aad) {
        return crypt(Cipher.ENCRYPT_MODE, plaintext, iv, aad);
    }

    private byte[] requirePersistableLocator(String value) {
        byte[] encoded = ReportContract.requireIdentity(value, "rawLocator")
                .getBytes(StandardCharsets.UTF_8);
        if (encoded.length > LOCATOR_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("rawLocator exceeds the supported URL size");
        }
        return encoded;
    }

    private byte[] decrypt(byte[] ciphertext, byte[] iv, byte[] aad) {
        if (ciphertext == null || iv == null || iv.length != IV_BYTES) {
            throw new IllegalStateException("REPORT_LOCATOR_CIPHERTEXT_INVALID");
        }
        return crypt(Cipher.DECRYPT_MODE, ciphertext, iv, aad);
    }

    private byte[] crypt(int mode, byte[] input, byte[] iv, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("REPORT_LOCATOR_CRYPTO_FAILED", failure);
        }
    }

    private byte[] aad(String reference, long taskId, String requestKey, String handleSha) {
        return (reference + "\n" + taskId + "\n" + requestKey + "\n" + handleSha)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(
                    ReportContract.requireIdentity(value, "report locator encryption key")
            );
            if (decoded.length != KEY_BYTES) {
                throw new IllegalArgumentException("report locator key must be 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("REPORT_LOCATOR_KEY_INVALID", failure);
        }
    }
}

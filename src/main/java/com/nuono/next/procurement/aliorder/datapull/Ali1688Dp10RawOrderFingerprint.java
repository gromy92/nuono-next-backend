package com.nuono.next.procurement.aliorder.datapull;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Canonical fingerprint of the provider DTO before any item-level validation mutates it. */
final class Ali1688Dp10RawOrderFingerprint {
    private static final ObjectWriter CANONICAL_WRITER = canonicalWriter();

    private Ali1688Dp10RawOrderFingerprint() {
    }

    static String fingerprint(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        MessageDigest digest = sha256();
        try (DigestOutputStream output = new DigestOutputStream(
                OutputStream.nullOutputStream(), digest)) {
            CANONICAL_WRITER.writeValue(output, order);
        } catch (IOException | RuntimeException failure) {
            throw new Ali1688Dp10PageContractException(
                    "DP10_RAW_FINGERPRINT_ENCODE_FAILED");
        }
        return hex(digest.digest());
    }

    private static ObjectWriter canonicalWriter() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .build()
                .writerFor(Ali1688HistoricalOrderProvider.OrderSnapshot.class);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required", unavailable);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }
}

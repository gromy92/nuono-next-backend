package com.nuono.next.officialwarehouse.datapull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Persistence-shape guard for one DP-07-A inventory row. */
final class Dp07InventoryColumnContract {

    static final int MAX_RAW_PAYLOAD_BYTES = 1_000_000;
    static final int MAX_STAGE_PAYLOAD_BYTES = 16_711_680;
    private static final DateTimeFormatter MYSQL_DATETIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private Dp07InventoryColumnContract() {}

    static String fit(String value, int maxCharacters, String name) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\0') >= 0 || hasUnpairedSurrogate(value)
                || value.codePointCount(0, value.length()) > maxCharacters) {
            throw defect("DP-07-A " + name + " does not fit the target column");
        }
        return value;
    }

    static String mysqlDateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(value, MYSQL_DATETIME);
            if (parsed.getYear() < 1000 || parsed.getYear() > 9999
                    || !MYSQL_DATETIME.format(parsed).equals(value)) {
                throw new DateTimeParseException("outside MySQL DATETIME", value, 0);
            }
            return value;
        } catch (DateTimeParseException invalid) {
            throw defect("DP-07-A inventorySnapshotAt is invalid");
        }
    }

    static String boundedJson(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_RAW_PAYLOAD_BYTES) {
            throw new ContainerContractException("DP-07-A raw row exceeds provider capacity");
        }
        return value;
    }

    static String stableIdentity(String... parts) {
        StringBuilder naturalIdentity = new StringBuilder();
        for (String value : parts) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            naturalIdentity.append(normalized.length()).append(':')
                    .append(normalized).append('|');
        }
        return "inventory:v1:" + sha256(naturalIdentity.toString());
    }

    static BusinessDefect defect(String message) {
        return new BusinessDefect(message);
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index += 1;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }

    static final class BusinessDefect extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private BusinessDefect(String message) {
            super(message);
        }
    }

    /** A technical capacity mismatch invalidates the provider container, not one business row. */
    static final class ContainerContractException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private ContainerContractException(String message) {
            super(message);
        }
    }
}

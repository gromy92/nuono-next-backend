package com.nuono.next.noonpull;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.util.StringUtils;

/** Strict secret-file, environment, and expiry rules for the ephemeral report probe source. */
final class NoonReportDownloadProbeSourceSupport {
    private static final Duration MINIMUM_VALIDITY = Duration.ofMinutes(15);

    private NoonReportDownloadProbeSourceSupport() {
    }

    static boolean freshNoonUrl(String value, Clock clock) {
        if (!StringUtils.hasText(value)) return false;
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (!"https".equals(uri.getScheme())
                || !"storage.googleapis.com".equals(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getFragment() != null
                || uri.getPath() == null
                || !uri.getPath().startsWith("/noonprd-mp-gcs--partner-impex/")) {
            return false;
        }
        Instant expires = expiry(query(uri.getRawQuery()));
        return expires != null && expires.isAfter(clock.instant().plus(MINIMUM_VALIDITY));
    }

    private static Instant expiry(Map<String, String> query) {
        String legacy = query.get("Expires");
        if (legacy != null && legacy.matches("[0-9]+")) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(legacy));
            } catch (RuntimeException invalid) {
                return null;
            }
        }
        String date = query.get("X-Goog-Date");
        String seconds = query.get("X-Goog-Expires");
        if (date == null || seconds == null || !seconds.matches("[0-9]+")) return null;
        try {
            Instant signedAt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC).parse(date, Instant::from);
            return signedAt.plusSeconds(Long.parseLong(seconds));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!StringUtils.hasText(rawQuery)) return values;
        for (String field : rawQuery.split("&")) {
            int separator = field.indexOf('=');
            if (separator <= 0 || values.put(field.substring(0, separator),
                    field.substring(separator + 1)) != null) return Map.of();
        }
        return values;
    }

    static Map<String, Object> loadEnvironment(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("probe environment file is unavailable");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("probe environment entry is malformed");
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            if (!key.matches("[A-Z][A-Z0-9_]*") || values.put(key, value) != null) {
                throw new IllegalArgumentException("probe environment key is invalid or duplicated");
            }
        }
        return new LinkedHashMap<>(values);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == value.charAt(value.length() - 1)
                && (value.charAt(0) == '\'' || value.charAt(0) == '"')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static void writeSecret(Path path, String value) throws IOException {
        if (Files.exists(path) || path.getParent() == null
                || !Files.isDirectory(path.getParent())) {
            throw new IllegalArgumentException("probe output path is not a new file");
        }
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path, value + "\n", StandardCharsets.UTF_8);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String safeMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoSuchBeanDefinitionException) {
                Class<?> missing = ((NoSuchBeanDefinitionException) current).getBeanType();
                if (missing != null) {
                    return "NoSuchBeanDefinitionException." + missing.getSimpleName();
                }
            }
            String message = current.getMessage();
            if (message != null && message.startsWith("Noon proxy provider unavailable: HTTP ")) {
                String status = message.substring("Noon proxy provider unavailable: HTTP ".length())
                        .replaceAll("[^0-9 ]", "")
                        .trim()
                        .replaceAll(" +", "_");
                return status.isEmpty() ? "PROXY_PROVIDER_UNAVAILABLE"
                        : "PROXY_PROVIDER_HTTP_" + status;
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        String name = failure == null ? "unknown" : failure.getClass().getSimpleName();
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}

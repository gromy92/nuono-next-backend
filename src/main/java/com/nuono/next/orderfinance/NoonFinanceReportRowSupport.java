package com.nuono.next.orderfinance;

import com.nuono.next.noonpull.NoonFinanceTransactionReportDescriptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/** Container-neutral parsing helpers shared by finance report row decisions. */
final class NoonFinanceReportRowSupport {
    private static final char SEPARATOR = '\u001f';

    private NoonFinanceReportRowSupport() {
    }

    static Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            result.put(normalizeHeader(headers[i]), i);
        }
        return result;
    }

    static boolean hasRequiredColumns(Map<String, Integer> headerIndex) {
        for (String requiredColumn : NoonFinanceTransactionReportDescriptor.requiredColumns()) {
            if (!headerIndex.containsKey(normalizeHeader(requiredColumn))) {
                return false;
            }
        }
        return true;
    }

    static String missingColumnsDiagnostic(Map<String, Integer> headerIndex) {
        String missing = NoonFinanceTransactionReportDescriptor.requiredColumns().stream()
                .filter(column -> !headerIndex.containsKey(normalizeHeader(column)))
                .collect(Collectors.joining(","));
        String actualHeaders = headerIndex.keySet().stream()
                .limit(60)
                .collect(Collectors.joining(","));
        return "missing=" + missing + "; actual_headers=" + actualHeaders;
    }

    static String value(String[] row, Map<String, Integer> headerIndex, String column) {
        Integer index = headerIndex.get(normalizeHeader(column));
        if (index == null || index < 0 || index >= row.length) {
            return "";
        }
        return row[index] == null ? "" : row[index].trim();
    }

    static String nullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static boolean isBlankRow(String[] row) {
        if (row == null || row.length == 0) {
            return true;
        }
        for (String value : row) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    static String rowHash(String[] row) {
        StringBuilder canonical = new StringBuilder();
        for (String value : row) {
            if (canonical.length() > 0) {
                canonical.append(SEPARATOR);
            }
            canonical.append(value == null ? "" : value.trim());
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

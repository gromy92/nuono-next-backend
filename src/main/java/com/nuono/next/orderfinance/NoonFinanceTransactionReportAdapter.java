package com.nuono.next.orderfinance;

import com.nuono.next.noonpull.NoonFinanceTransactionReportDescriptor;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonFinanceTransactionReportAdapter {
    private static final char SEPARATOR = '\u001f';

    private final NoonFinanceTransactionFactWriter factWriter;

    public NoonFinanceTransactionReportAdapter(NoonFinanceTransactionFactWriter factWriter) {
        this.factWriter = factWriter;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        List<List<String>> rows;
        try {
            rows = parseCsv(decode(file));
        } catch (RuntimeException exception) {
            return NoonReportProcessResult.mappingFailed(1, exception.getMessage());
        }
        if (rows.isEmpty() || rows.get(0).isEmpty()) {
            return NoonReportProcessResult.emptyReport();
        }
        Map<String, Integer> headerIndex = headerIndex(rows.get(0));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.missingColumns(missingColumnsDiagnostic(headerIndex));
        }

        int exceptions = 0;
        int imported = 0;
        for (int rowNumber = 1; rowNumber < rows.size(); rowNumber++) {
            List<String> row = rows.get(rowNumber);
            if (isBlankRow(row)) {
                continue;
            }
            try {
                NoonFinanceTransactionFact fact = toFact(file, row, headerIndex);
                if (fact == null) {
                    continue;
                }
                factWriter.upsert(fact);
                imported++;
            } catch (RuntimeException exception) {
                exceptions++;
            }
        }
        if (imported == 0 && exceptions == 0) {
            return NoonReportProcessResult.emptyReport();
        }
        if (imported == 0) {
            return NoonReportProcessResult.mappingFailed(exceptions);
        }
        if (exceptions > 0) {
            return NoonReportProcessResult.partialSuccess(imported, exceptions);
        }
        return NoonReportProcessResult.succeeded(imported, 0);
    }

    private NoonFinanceTransactionFact toFact(
            NoonReportDownloadedFile file,
            List<String> row,
            Map<String, Integer> headerIndex
    ) {
        NoonReportPullRequest request = file.getRequest();
        String contractCode = nullable(value(row, headerIndex, "Contract"));
        String contractTitle = nullable(value(row, headerIndex, "Contract Title"));
        String currency = requiredValue(row, headerIndex, "Currency").toUpperCase(Locale.ROOT);
        String detectedSiteCode = detectSiteCode(contractCode, contractTitle, currency);
        String requestedSiteCode = normalizeSiteCode(request.getSiteCode());
        if (StringUtils.hasText(detectedSiteCode)
                && StringUtils.hasText(requestedSiteCode)
                && !detectedSiteCode.equals(requestedSiteCode)) {
            return null;
        }
        String siteCode = StringUtils.hasText(requestedSiteCode) ? requestedSiteCode : request.getSiteCode();
        return new NoonFinanceTransactionFact(
                request.getOwnerUserId(),
                request.getStoreCode(),
                siteCode,
                file.getSourceBatchId(),
                file.getDigestSha256(),
                rowHash(row),
                contractCode,
                contractTitle,
                requiredValue(row, headerIndex, "Reference Nr"),
                requiredValue(row, headerIndex, "Order Nr"),
                value(row, headerIndex, "Item Nr"),
                optionalDate(value(row, headerIndex, "Order Date")),
                requiredDate(value(row, headerIndex, "Transaction Date"), "Transaction Date"),
                nullable(value(row, headerIndex, "Title")),
                nullable(value(row, headerIndex, "SKUs")),
                nullable(value(row, headerIndex, "Partner SKUs")),
                requiredValue(row, headerIndex, "Transaction Type").toLowerCase(Locale.ROOT),
                currency,
                decimal(value(row, headerIndex, "Net Proceeds")),
                decimal(value(row, headerIndex, "Referral Fee including VAT")),
                decimal(value(row, headerIndex, "Fullfilment & Logistics Fees including VAT")),
                decimal(value(row, headerIndex, "Shipping Credits including VAT")),
                decimal(value(row, headerIndex, "Other Order Fees including VAT")),
                decimal(value(row, headerIndex, "Order Subsidies including VAT")),
                decimal(value(row, headerIndex, "Non-Order Fees including VAT")),
                decimal(value(row, headerIndex, "Non-Order Subsidies including VAT")),
                decimal(value(row, headerIndex, "Others including VAT")),
                decimal(value(row, headerIndex, "Total")),
                request.getDateFrom(),
                request.getDateTo()
        );
    }

    private SiteAssignment resolveSiteAssignment(
            NoonReportPullRequest request,
            String detectedSiteCode
    ) {
        String siteCode = StringUtils.hasText(detectedSiteCode)
                ? detectedSiteCode
                : normalizeSiteCode(request.getSiteCode());
        if (!StringUtils.hasText(siteCode)) {
            siteCode = request.getSiteCode();
        }
        return new SiteAssignment(storeCodeForSite(request.getStoreCode(), siteCode), siteCode);
    }

    private String detectSiteCode(String contractCode, String contractTitle, String currency) {
        String normalizedCurrency = normalizeSiteCode(currency);
        if ("AED".equals(normalizedCurrency)) {
            return "AE";
        }
        if ("SAR".equals(normalizedCurrency)) {
            return "SA";
        }
        String contractText = (
                (contractCode == null ? "" : contractCode)
                        + " "
                        + (contractTitle == null ? "" : contractTitle)
        ).toUpperCase(Locale.ROOT);
        if (contractText.contains("NOON-SA") || contractText.contains("KSA") || contractText.endsWith("SA")) {
            return "SA";
        }
        if (contractText.contains("NOON AE") || contractText.contains("UAE") || contractText.endsWith("AE")) {
            return "AE";
        }
        return null;
    }

    private String storeCodeForSite(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            return storeCode;
        }
        String normalizedSiteCode = normalizeSiteCode(siteCode);
        if (!"AE".equals(normalizedSiteCode) && !"SA".equals(normalizedSiteCode)) {
            return storeCode;
        }
        String normalizedStoreCode = storeCode.toUpperCase(Locale.ROOT);
        if (normalizedStoreCode.endsWith("-NAE") || normalizedStoreCode.endsWith("-NSA")) {
            return storeCode.substring(0, storeCode.length() - 2) + normalizedSiteCode;
        }
        if (normalizedStoreCode.endsWith("-AE") || normalizedStoreCode.endsWith("-SA")) {
            return storeCode.substring(0, storeCode.length() - 2) + normalizedSiteCode;
        }
        if (normalizedStoreCode.endsWith("-UAE") || normalizedStoreCode.endsWith("-KSA")) {
            return storeCode.substring(0, storeCode.length() - 3)
                    + ("AE".equals(normalizedSiteCode) ? "UAE" : "KSA");
        }
        return storeCode;
    }

    private String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static final class SiteAssignment {
        private final String storeCode;
        private final String siteCode;

        private SiteAssignment(String storeCode, String siteCode) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    private String decode(NoonReportDownloadedFile file) {
        String csv = new String(file.getContent(), StandardCharsets.UTF_8);
        if (!csv.isEmpty() && csv.charAt(0) == '\ufeff') {
            return csv.substring(1);
        }
        return csv;
    }

    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean sawAnyCharacter = false;
        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            sawAnyCharacter = true;
            if (inQuotes) {
                if (current == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (current == '"') {
                inQuotes = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\r' || current == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(current);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV field.");
        }
        if (sawAnyCharacter && (field.length() > 0 || !row.isEmpty())) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            result.put(normalizeHeader(headers.get(i)), i);
        }
        return result;
    }

    private boolean hasRequiredColumns(Map<String, Integer> headerIndex) {
        for (String requiredColumn : NoonFinanceTransactionReportDescriptor.requiredColumns()) {
            if (!headerIndex.containsKey(normalizeHeader(requiredColumn))) {
                return false;
            }
        }
        return true;
    }

    private String missingColumnsDiagnostic(Map<String, Integer> headerIndex) {
        String missing = NoonFinanceTransactionReportDescriptor.requiredColumns().stream()
                .filter(column -> !headerIndex.containsKey(normalizeHeader(column)))
                .collect(Collectors.joining(","));
        String actualHeaders = headerIndex.keySet().stream()
                .limit(60)
                .collect(Collectors.joining(","));
        return "missing=" + missing + "; actual_headers=" + actualHeaders;
    }

    private String value(List<String> row, Map<String, Integer> headerIndex, String column) {
        Integer index = headerIndex.get(normalizeHeader(column));
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    private String requiredValue(List<String> row, Map<String, Integer> headerIndex, String column) {
        String value = value(row, headerIndex, column);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Blank column value: " + column);
        }
        return value.trim();
    }

    private LocalDate requiredDate(String value, String column) {
        LocalDate parsed = optionalDate(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Blank date value: " + column);
        }
        return parsed;
    }

    private LocalDate optionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.replace('\u00a0', ' ').replace('\u202f', ' ').trim();
        if (!StringUtils.hasText(trimmed) || "-".equals(trimmed) || "–".equals(trimmed) || "—".equals(trimmed)) {
            return null;
        }
        String token = trimmed.split("[ T]", 2)[0];
        if (token.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return parseDateWithPattern(token, "yyyy-M-d");
        }
        if (token.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            return parseDateWithPattern(token, "yyyy/M/d");
        }
        if (token.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
            String[] parts = token.split("/");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            if (first > 12 && second <= 12) {
                return parseDateWithPattern(token, "d/M/yyyy");
            }
            if (second > 12 && first <= 12) {
                return parseDateWithPattern(token, "M/d/yyyy");
            }
            throw new IllegalArgumentException("Ambiguous slash date value: " + value);
        }
        return LocalDate.parse(token);
    }

    private LocalDate parseDateWithPattern(String value, String pattern) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date value: " + value, exception);
        }
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        String normalized = value.replace('\u00a0', ' ')
                .replace('\u202f', ' ')
                .replace("–", "-")
                .replace("—", "-")
                .replace("−", "-")
                .trim();
        if (isZeroPlaceholder(normalized)) {
            return BigDecimal.ZERO;
        }
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        if (negative) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.trim()
                .replaceAll("^[\\p{Sc}\\s]+", "")
                .replaceAll("[\\p{Sc}\\s]+$", "")
                .trim();
        if (isZeroPlaceholder(normalized)) {
            return BigDecimal.ZERO;
        }
        if (negative && normalized.startsWith("-")) {
            throw new IllegalArgumentException("Invalid negative decimal value: " + value);
        }
        if (!isConservativeNumericToken(normalized)) {
            throw new IllegalArgumentException("Invalid decimal value: " + value);
        }
        BigDecimal parsed = new BigDecimal(normalized.replace(",", ""));
        if (negative) {
            return parsed.negate();
        }
        return parsed;
    }

    private boolean isZeroPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        return "-".equals(trimmed) || "--".equals(trimmed);
    }

    private boolean isConservativeNumericToken(String value) {
        if (!StringUtils.hasText(value) || value.contains(" ")) {
            return false;
        }
        return value.matches("[-+]?(?:(?:\\d{1,3}(?:,\\d{3})+)|(?:\\d+))(?:\\.\\d+)?");
    }

    private String nullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private String rowHash(List<String> row) {
        StringBuilder canonical = new StringBuilder();
        for (String value : row) {
            if (canonical.length() > 0) {
                canonical.append(SEPARATOR);
            }
            canonical.append(value == null ? "" : value.trim());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

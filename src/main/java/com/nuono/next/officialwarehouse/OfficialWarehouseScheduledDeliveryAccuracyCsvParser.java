package com.nuono.next.officialwarehouse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OfficialWarehouseScheduledDeliveryAccuracyCsvParser {

    public ParsedFile parse(byte[] content) {
        String csv = new String(content == null ? new byte[0] : content, StandardCharsets.UTF_8);
        List<List<String>> records = parseRecords(csv);
        if (records.isEmpty()) {
            return new ParsedFile(List.of(), List.of());
        }
        List<String> headers = normalizedHeaders(records.get(0));
        List<AccuracyRow> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            rows.add(toRow(index + 1, headers, records.get(index)));
        }
        return new ParsedFile(headers, rows);
    }

    private AccuracyRow toRow(int rowNo, List<String> headers, List<String> record) {
        Map<String, String> rawFields = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            rawFields.put(headers.get(index), normalizeCell(index < record.size() ? record.get(index) : null));
        }
        return new AccuracyRow(
                rowNo,
                rawFields,
                text(rawFields, "asn number"),
                text(rawFields, "warehouse"),
                text(rawFields, "country"),
                dateTimeWithoutUtc(rawFields, "asn creation date"),
                text(rawFields, "scheduled date"),
                text(rawFields, "delivery date"),
                integer(rawFields, "scheduled quantity"),
                integer(rawFields, "grn quantity"),
                integer(rawFields, "inbound quantity variance"),
                text(rawFields, "status"),
                text(rawFields, "inbound utilization efficiency %")
        );
    }

    private List<List<String>> parseRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < csv.length(); index++) {
            char value = csv.charAt(index);
            if (inQuotes) {
                if (value == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(value);
                }
                continue;
            }
            if (value == '"') {
                inQuotes = true;
            } else if (value == ',') {
                currentRecord.add(field.toString());
                field.setLength(0);
            } else if (value == '\r' || value == '\n') {
                currentRecord.add(field.toString());
                addIfNotBlank(records, currentRecord);
                currentRecord = new ArrayList<>();
                field.setLength(0);
                if (value == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(value);
            }
        }
        if (field.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(field.toString());
            addIfNotBlank(records, currentRecord);
        }
        return records;
    }

    private void addIfNotBlank(List<List<String>> records, List<String> record) {
        for (String field : record) {
            if (StringUtils.hasText(field)) {
                records.add(record);
                return;
            }
        }
    }

    private List<String> normalizedHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String header = normalizeCell(rawHeaders.get(index));
            if (index == 0 && header != null && header.startsWith("\ufeff")) {
                header = header.substring(1);
            }
            headers.add(header == null ? "" : header.toLowerCase(Locale.ROOT));
        }
        return headers;
    }

    private static String dateTimeWithoutUtc(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" UTC")) {
            return value.substring(0, value.length() - 4).trim();
        }
        return value;
    }

    private static String text(Map<String, String> fields, String key) {
        return nullIfDash(fields.get(key));
    }

    private static int integer(Map<String, String> fields, String key) {
        String value = nullIfDash(fields.get(key));
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalizeCell(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullIfDash(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return "-".equals(trimmed) ? null : trimmed;
    }

    public static class ParsedFile {
        public final List<String> headers;
        public final List<AccuracyRow> rows;

        public ParsedFile(List<String> headers, List<AccuracyRow> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    public static class AccuracyRow {
        public final int rowNo;
        public final Map<String, String> rawFields;
        public final String noonAsnNr;
        public final String warehouseCode;
        public final String countryCode;
        public final String asnCreationDate;
        public final String scheduledDate;
        public final String deliveryDate;
        public final int scheduledQty;
        public final int grnQty;
        public final int inboundQtyVariance;
        public final String status;
        public final String inboundUtilizationEfficiency;

        private AccuracyRow(
                int rowNo,
                Map<String, String> rawFields,
                String noonAsnNr,
                String warehouseCode,
                String countryCode,
                String asnCreationDate,
                String scheduledDate,
                String deliveryDate,
                int scheduledQty,
                int grnQty,
                int inboundQtyVariance,
                String status,
                String inboundUtilizationEfficiency
        ) {
            this.rowNo = rowNo;
            this.rawFields = rawFields;
            this.noonAsnNr = noonAsnNr;
            this.warehouseCode = warehouseCode;
            this.countryCode = countryCode;
            this.asnCreationDate = asnCreationDate;
            this.scheduledDate = scheduledDate;
            this.deliveryDate = deliveryDate;
            this.scheduledQty = scheduledQty;
            this.grnQty = grnQty;
            this.inboundQtyVariance = inboundQtyVariance;
            this.status = status;
            this.inboundUtilizationEfficiency = inboundUtilizationEfficiency;
        }

        public String businessKey() {
            return StringUtils.hasText(noonAsnNr) ? noonAsnNr : "row:" + rowNo;
        }
    }
}

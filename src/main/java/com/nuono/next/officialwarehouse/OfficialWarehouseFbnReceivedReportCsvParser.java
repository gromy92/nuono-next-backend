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
public class OfficialWarehouseFbnReceivedReportCsvParser {

    public ParsedFile parse(byte[] content) {
        String csv = new String(content == null ? new byte[0] : content, StandardCharsets.UTF_8);
        List<List<String>> records = parseRecords(csv);
        if (records.isEmpty()) {
            return new ParsedFile(List.of(), List.of());
        }
        List<String> headers = normalizedHeaders(records.get(0));
        List<ReceivedRow> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            rows.add(toRow(index + 1, headers, record));
        }
        return new ParsedFile(headers, rows);
    }

    private ReceivedRow toRow(int rowNo, List<String> headers, List<String> record) {
        Map<String, String> rawFields = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            rawFields.put(header, normalizeCell(index < record.size() ? record.get(index) : null));
        }
        return new ReceivedRow(
                rowNo,
                rawFields,
                text(rawFields, "partner_sku"),
                text(rawFields, "sku"),
                text(rawFields, "po_nr"),
                text(rawFields, "pbarcode_canonical"),
                text(rawFields, "storage_type_code"),
                text(rawFields, "volume"),
                text(rawFields, "brand"),
                text(rawFields, "product_title"),
                text(rawFields, "asn"),
                text(rawFields, "partner_warehouse"),
                text(rawFields, "noon_warehouse"),
                text(rawFields, "country_code"),
                integer(rawFields, "qty_expected"),
                integer(rawFields, "received_qty"),
                integer(rawFields, "qc_failed_qty"),
                integer(rawFields, "unidentified_qty"),
                text(rawFields, "qc_failed_reason"),
                text(rawFields, "asn_created_at"),
                text(rawFields, "asn_schedule_date"),
                text(rawFields, "asn_completed_at")
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

    private static String text(Map<String, String> fields, String key) {
        return nullIfDash(fields.get(key));
    }

    private static Integer integer(Map<String, String> fields, String key) {
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
        if ("-".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    public static class ParsedFile {
        public final List<String> headers;
        public final List<ReceivedRow> rows;

        public ParsedFile(List<String> headers, List<ReceivedRow> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    public static class ReceivedRow {
        public final int rowNo;
        public final Map<String, String> rawFields;
        public final String partnerSku;
        public final String noonSku;
        public final String poNr;
        public final String pbarcodeCanonical;
        public final String storageTypeCode;
        public final String volume;
        public final String brand;
        public final String productTitle;
        public final String noonAsnNr;
        public final String partnerWarehouse;
        public final String noonWarehouse;
        public final String countryCode;
        public final int qtyExpected;
        public final int receivedQty;
        public final int qcFailedQty;
        public final int unidentifiedQty;
        public final String qcFailedReason;
        public final String asnCreatedAt;
        public final String asnScheduleDate;
        public final String asnCompletedAt;

        private ReceivedRow(
                int rowNo,
                Map<String, String> rawFields,
                String partnerSku,
                String noonSku,
                String poNr,
                String pbarcodeCanonical,
                String storageTypeCode,
                String volume,
                String brand,
                String productTitle,
                String noonAsnNr,
                String partnerWarehouse,
                String noonWarehouse,
                String countryCode,
                int qtyExpected,
                int receivedQty,
                int qcFailedQty,
                int unidentifiedQty,
                String qcFailedReason,
                String asnCreatedAt,
                String asnScheduleDate,
                String asnCompletedAt
        ) {
            this.rowNo = rowNo;
            this.rawFields = rawFields;
            this.partnerSku = partnerSku;
            this.noonSku = noonSku;
            this.poNr = poNr;
            this.pbarcodeCanonical = pbarcodeCanonical;
            this.storageTypeCode = storageTypeCode;
            this.volume = volume;
            this.brand = brand;
            this.productTitle = productTitle;
            this.noonAsnNr = noonAsnNr;
            this.partnerWarehouse = partnerWarehouse;
            this.noonWarehouse = noonWarehouse;
            this.countryCode = countryCode;
            this.qtyExpected = qtyExpected;
            this.receivedQty = receivedQty;
            this.qcFailedQty = qcFailedQty;
            this.unidentifiedQty = unidentifiedQty;
            this.qcFailedReason = qcFailedReason;
            this.asnCreatedAt = asnCreatedAt;
            this.asnScheduleDate = asnScheduleDate;
            this.asnCompletedAt = asnCompletedAt;
        }

        public String businessKey() {
            return value(noonAsnNr) + "|" + value(noonSku) + "|" + value(partnerSku) + "|" + value(pbarcodeCanonical);
        }

        private String value(String value) {
            return value == null ? "" : value;
        }
    }
}

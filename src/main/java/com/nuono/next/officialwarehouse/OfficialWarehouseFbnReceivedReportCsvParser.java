package com.nuono.next.officialwarehouse;

import com.nuono.next.noonpull.NoonReportCsvRecords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OfficialWarehouseFbnReceivedReportCsvParser {
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "partner_sku", "sku", "asn", "qty_expected", "received_qty",
            "qc_failed_qty", "unidentified_qty", "asn_schedule_date"
    );
    public ParsedFile parse(byte[] content) {
        List<String[]> records = NoonReportCsvRecords.parse(content);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("FBN received report is missing its header.");
        }
        List<String> headers = OfficialWarehouseFbnReceivedReportValueParser.normalizedHeaders(
                Arrays.asList(records.get(0))
        );
        if (!headers.containsAll(REQUIRED_HEADERS)) {
            throw new IllegalArgumentException("FBN received report is missing required columns.");
        }
        List<ReceivedRow> rows = new ArrayList<>();
        int sourceDataRowCount = 0;
        for (int index = 1; index < records.size(); index++) {
            String[] record = records.get(index);
            if (isBlank(record)) {
                continue;
            }
            sourceDataRowCount++;
            if (record.length != headers.size()) {
                throw new IllegalArgumentException("FBN received report row width does not match header.");
            }
            ReceivedRow row = toRow(index + 1, headers, Arrays.asList(record));
            if (row != null) {
                rows.add(row);
            }
        }
        return new ParsedFile(headers, rows, sourceDataRowCount);
    }

    public void requireStageHeader(String[] header) {
        List<String> headers = OfficialWarehouseFbnReceivedReportValueParser.normalizedHeaders(
                Arrays.asList(header == null ? new String[0] : header)
        );
        if (!headers.containsAll(REQUIRED_HEADERS)) {
            throw new IllegalArgumentException("FBN received report is missing required columns.");
        }
    }

    boolean isBlank(String[] record) {
        if (record == null) {
            return true;
        }
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ReceivedRow toRow(int rowNo, List<String> headers, List<String> record) {
        return toRow(rowNo, rawFields(headers, record));
    }

    Map<String, String> rawFields(List<String> headers, List<String> record) {
        Map<String, String> rawFields = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            rawFields.put(
                    header,
                    OfficialWarehouseFbnReceivedReportValueParser.normalizeCell(
                            index < record.size() ? record.get(index) : null
                    )
            );
        }
        return rawFields;
    }

    ReceivedRow toRow(int rowNo, Map<String, String> rawFields) {
        String partnerSku = text(rawFields, "partner_sku");
        String noonSku = text(rawFields, "sku");
        String pbarcodeCanonical = text(rawFields, "pbarcode_canonical");
        String noonAsnNr = text(rawFields, "asn");
        Integer qtyExpected = integer(rawFields, "qty_expected");
        Integer receivedQty = integer(rawFields, "received_qty");
        Integer qcFailedQty = integer(rawFields, "qc_failed_qty");
        Integer unidentifiedQty = integer(rawFields, "unidentified_qty");
        String asnCreatedAt = dateTime(rawFields, "asn_created_at");
        String asnScheduleDate = date(rawFields, "asn_schedule_date");
        String asnCompletedAt = dateTime(rawFields, "asn_completed_at");

        if (isDeterministicBusinessDefect(
                noonAsnNr,
                partnerSku,
                noonSku,
                pbarcodeCanonical,
                qtyExpected,
                receivedQty,
                qcFailedQty,
                unidentifiedQty
        )) {
            return null;
        }
        return new ReceivedRow(
                rowNo,
                rawFields,
                partnerSku,
                noonSku,
                text(rawFields, "po_nr"),
                pbarcodeCanonical,
                text(rawFields, "storage_type_code"),
                text(rawFields, "volume"),
                text(rawFields, "brand"),
                text(rawFields, "product_title"),
                noonAsnNr,
                text(rawFields, "partner_warehouse"),
                text(rawFields, "noon_warehouse"),
                text(rawFields, "country_code"),
                qtyExpected,
                receivedQty,
                qcFailedQty,
                unidentifiedQty,
                text(rawFields, "qc_failed_reason"),
                asnCreatedAt,
                asnScheduleDate,
                asnCompletedAt
        );
    }

    private static boolean isDeterministicBusinessDefect(
            String noonAsnNr,
            String partnerSku,
            String noonSku,
            String pbarcodeCanonical,
            Integer qtyExpected,
            Integer receivedQty,
            Integer qcFailedQty,
            Integer unidentifiedQty
    ) {
        if (qtyExpected == null || receivedQty == null || qcFailedQty == null || unidentifiedQty == null) {
            return true;
        }
        if (qtyExpected < 0 || receivedQty < 0 || qcFailedQty < 0 || unidentifiedQty < 0) {
            return true;
        }
        if (!hasStableIdentity(noonAsnNr)) {
            return true;
        }
        return !hasStableIdentity(partnerSku)
                && !hasStableIdentity(noonSku)
                && !hasStableIdentity(pbarcodeCanonical);
    }

    private static boolean hasStableIdentity(String value) {
        return !OfficialWarehouseFbnReceivedReportValueParser.normalizeIdentity(value).isEmpty();
    }

    private static String text(Map<String, String> fields, String key) {
        return OfficialWarehouseFbnReceivedReportValueParser.text(fields, key);
    }

    private static Integer integer(Map<String, String> fields, String key) {
        return OfficialWarehouseFbnReceivedReportValueParser.integer(fields, key);
    }

    private static String date(Map<String, String> fields, String key) {
        return OfficialWarehouseFbnReceivedReportValueParser.date(fields, key);
    }

    private static String dateTime(Map<String, String> fields, String key) {
        return OfficialWarehouseFbnReceivedReportValueParser.dateTime(fields, key);
    }

    public static class ParsedFile {
        public final List<String> headers;
        public final List<ReceivedRow> rows;
        public final int sourceDataRowCount;

        public ParsedFile(List<String> headers, List<ReceivedRow> rows, int sourceDataRowCount) {
            this.headers = headers;
            this.rows = rows;
            this.sourceDataRowCount = sourceDataRowCount;
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
            return "FBN_RECEIVED_V1"
                    + keyPart("asn", noonAsnNr)
                    + keyPart("noonSku", noonSku)
                    + keyPart("partnerSku", partnerSku)
                    + keyPart("pbarcode", pbarcodeCanonical);
        }

        public String getBusinessKey() {
            return businessKey();
        }

        private String keyPart(String name, String value) {
            String normalized = OfficialWarehouseFbnReceivedReportValueParser.normalizeIdentity(
                    value
            );
            return "|" + name + "=" + normalized.length() + ":" + normalized;
        }
    }
}

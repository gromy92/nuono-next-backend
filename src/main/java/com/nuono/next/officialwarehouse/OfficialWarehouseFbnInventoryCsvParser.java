package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Strict parser for the complete CSV export form of the FBN inventory response. */
final class OfficialWarehouseFbnInventoryCsvParser {
    private final ObjectMapper objectMapper;

    OfficialWarehouseFbnInventoryCsvParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    List<InventoryItem> parse(String csv) {
        List<List<String>> records = parseRecords(csv);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("FBN inventory CSV response is empty");
        }
        List<String> headers = normalizedHeaders(records.get(0));
        requireHeaders(headers);
        List<InventoryItem> items = new ArrayList<>();
        String snapshotAt = null;
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex += 1) {
            List<String> record = records.get(rowIndex);
            if (record.size() != headers.size()) {
                throw new IllegalArgumentException("FBN inventory CSV row width is inconsistent");
            }
            ObjectNode row = objectMapper.createObjectNode();
            for (int column = 0; column < headers.size(); column += 1) {
                String header = headers.get(column);
                String value = trimToNull(record.get(column));
                if (value != null) {
                    row.put(header, value);
                }
            }
            InventoryItem item = InventoryItem.from(row);
            if (!StringUtils.hasText(item.inventorySnapshotAt)) {
                throw new IllegalArgumentException(
                        "FBN inventory CSV snapshot timestamp is missing"
                );
            }
            if (snapshotAt != null && !snapshotAt.equals(item.inventorySnapshotAt)) {
                throw new IllegalArgumentException(
                        "FBN inventory CSV snapshot timestamp drift"
                );
            }
            snapshotAt = item.inventorySnapshotAt;
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException(
                    "FBN inventory CSV empty snapshot extent is unproven"
            );
        }
        return items;
    }

    private void requireHeaders(List<String> headers) {
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            if (!StringUtils.hasText(header) || !unique.add(header)) {
                throw new IllegalArgumentException("FBN inventory CSV header is invalid");
            }
        }
        if (!headers.contains("warehouse_code")
                || !headers.contains("qty")
                || !headers.contains("inventory_type")
                || !headers.contains("inventory_snapshot_at")
                || (!headers.contains("partner_sku")
                && !headers.contains("sku")
                && !headers.contains("pbarcode")
                && !headers.contains("barcode"))) {
            throw new IllegalArgumentException("FBN inventory CSV columns are incomplete");
        }
    }

    private List<List<String>> parseRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        String safeCsv = csv == null ? "" : csv;
        for (int index = 0; index < safeCsv.length(); index += 1) {
            char value = safeCsv.charAt(index);
            if (inQuotes) {
                if (value == '"') {
                    if (index + 1 < safeCsv.length() && safeCsv.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 1;
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
                if (value == '\r'
                        && index + 1 < safeCsv.length()
                        && safeCsv.charAt(index + 1) == '\n') {
                    index += 1;
                }
            } else {
                field.append(value);
            }
        }
        if (field.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(field.toString());
            addIfNotBlank(records, currentRecord);
        }
        if (inQuotes) {
            throw new IllegalArgumentException("FBN inventory CSV has an unclosed quote");
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
        for (int index = 0; index < rawHeaders.size(); index += 1) {
            String header = trimToNull(rawHeaders.get(index));
            if (index == 0 && header != null && header.startsWith("\ufeff")) {
                header = header.substring(1);
            }
            headers.add(header == null ? "" : header.toLowerCase(Locale.ROOT));
        }
        return headers;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class OfficialWarehouseRoutingLineParser {
    private OfficialWarehouseRoutingLineParser() {
    }

    static List<AsnLineInsertRecord> parse(JsonNode detail) {
        if (detail == null) {
            return List.of();
        }
        JsonNode lines = detail.path("lines");
        if (!lines.isArray()) {
            lines = detail.path("partnerAsnLineList");
        }
        if (!lines.isArray()) {
            return List.of();
        }
        List<AsnLineInsertRecord> result = new ArrayList<>();
        for (JsonNode line : lines) {
            String noonSku = text(line, "sku", "noon_sku", "noonSku");
            Integer quantity = positiveInteger(
                    line, "qty", "quantity", "total_qty", "totalQty",
                    "expected_qty", "expectedQty", "qty_expected", "qtyExpected"
            );
            if (!StringUtils.hasText(noonSku) || quantity == null) {
                continue;
            }
            AsnLineInsertRecord record = new AsnLineInsertRecord();
            record.noonSku = noonSku;
            record.quantity = quantity;
            record.storageTypeCode = firstNonBlank(
                    text(line, "storage_type_code", "storageTypeCode"), "standard"
            );
            result.add(record);
        }
        return result;
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static Integer positiveInteger(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            try {
                int parsed = value == null ? 0 : Integer.parseInt(value);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed alternatives and continue with the next field.
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

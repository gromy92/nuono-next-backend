package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Strict JSON-or-complete-CSV response parser for the FBN inventory provider. */
final class OfficialWarehouseFbnInventoryResponseParser {
    private static final List<String> KNOWN_ROW_SCALAR_FIELDS = List.of(
            "warehouse_code", "warehouseCode", "warehouse",
            "qty", "inventory_type", "inventoryType", "reason_code", "reasonCode",
            "barcode", "pbarcode", "pbarcode_canonical", "sku", "noon_sku", "noonSku",
            "partner_sku", "partnerSku", "psku", "country_code", "countryCode",
            "classification_code", "classificationCode", "title", "product_title",
            "productTitle", "brand", "inventory_snapshot_at", "inventorySnapshotAt",
            "snapshot_at"
    );

    private final ObjectMapper objectMapper;
    private final OfficialWarehouseFbnInventoryCsvParser csvParser;

    OfficialWarehouseFbnInventoryResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.csvParser = new OfficialWarehouseFbnInventoryCsvParser(objectMapper);
    }

    InventoryPage parse(byte[] responseBytes, int page) {
        String responseText = strictUtf8(responseBytes);
        JsonNode jsonResponse = tryReadJson(responseText);
        if (jsonResponse != null) {
            PaginationEvidence pagination = paginationEvidence(jsonResponse, page);
            return new InventoryPage(
                    page,
                    pagination.hasNextPage,
                    pagination.totalPages,
                    false,
                    null, null, null,
                    readItems(requireRowArray(jsonResponse)), jsonResponse, responseBytes
            );
        }
        if (page != 1) {
            throw new IllegalArgumentException("FBN inventory CSV export is valid only as page 1");
        }
        List<InventoryItem> csvItems = csvParser.parse(responseText);
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("source_format", "csv");
        raw.put("row_count", csvItems.size());
        raw.put("complete_export", true);
        // Format closure and row timestamps are not provider-native export authority.
        return new InventoryPage(
                1, false, 1, true, null, null, (long) csvItems.size(),
                csvItems, raw, responseBytes
        );
    }

    private String strictUtf8(byte[] responseBytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(responseBytes == null ? new byte[0] : responseBytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
            throw new IllegalArgumentException(
                    "FBN inventory response is not valid UTF-8",
                    invalidUtf8
            );
        }
    }

    private JsonNode tryReadJson(String responseText) {
        String trimmed = trimToNull(responseText);
        if (trimmed == null) {
            return objectMapper.createObjectNode();
        }
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (IOException exception) {
            throw new IllegalArgumentException("FBN inventory JSON response is malformed", exception);
        }
    }

    private List<InventoryItem> readItems(JsonNode rows) {
        List<InventoryItem> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("FBN inventory row must be an object");
            }
            requireKnownRowFieldsAreScalar(row);
            result.add(InventoryItem.from(row));
        }
        return result;
    }

    private void requireKnownRowFieldsAreScalar(JsonNode row) {
        for (String field : KNOWN_ROW_SCALAR_FIELDS) {
            JsonNode value = row.get(field);
            if (value != null && !value.isNull() && value.isContainerNode()) {
                throw new IllegalArgumentException("FBN inventory row field shape is invalid");
            }
        }
    }

    private JsonNode requireRowArray(JsonNode response) {
        JsonNode array = firstArray(
                response,
                response == null ? null : response.path("data"),
                response == null ? null : response.path("data").path("rows"),
                response == null ? null : response.path("data").path("items"),
                response == null ? null : response.path("data").path("hits"),
                response == null ? null : response.path("rows"),
                response == null ? null : response.path("items"),
                response == null ? null : response.path("hits")
        );
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("FBN inventory JSON rows container is missing");
        }
        return array;
    }

    private PaginationEvidence paginationEvidence(JsonNode response, int currentPage) {
        Boolean explicit = firstBoolean(
                response == null ? null : response.path("has_next"),
                response == null ? null : response.path("hasNext"),
                response == null ? null : response.path("data").path("has_next"),
                response == null ? null : response.path("data").path("hasNext"),
                response == null ? null : response.path("pagination").path("has_next"),
                response == null ? null : response.path("pagination").path("hasNext"),
                response == null ? null : response.path("data").path("pagination").path("has_next"),
                response == null ? null : response.path("data").path("pagination").path("hasNext")
        );
        Integer totalPages = firstInteger(
                response == null ? null : response.path("total_pages"),
                response == null ? null : response.path("totalPages"),
                response == null ? null : response.path("pagination").path("total_pages"),
                response == null ? null : response.path("pagination").path("totalPages"),
                response == null ? null : response.path("data").path("total_pages"),
                response == null ? null : response.path("data").path("totalPages"),
                response == null ? null : response.path("data").path("pagination").path("total_pages"),
                response == null ? null : response.path("data").path("pagination").path("totalPages")
        );
        if (totalPages != null && totalPages < 1) {
            throw new IllegalArgumentException("FBN inventory total pages is invalid");
        }
        if (totalPages != null && currentPage > totalPages) {
            throw new IllegalArgumentException("FBN inventory page exceeds total pages");
        }
        Boolean derived = totalPages == null ? null : currentPage < totalPages;
        if (explicit != null && derived != null && !explicit.equals(derived)) {
            throw new IllegalArgumentException("FBN inventory pagination metadata conflicts");
        }
        return new PaginationEvidence(explicit == null ? derived : explicit, totalPages);
    }

    private JsonNode firstArray(JsonNode... nodes) {
        if (nodes != null) {
            for (JsonNode node : nodes) {
                if (node != null && node.isArray()) {
                    return node;
                }
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode... nodes) {
        if (nodes != null) {
            for (JsonNode node : nodes) {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    continue;
                }
                if (node.isBoolean()) {
                    return node.asBoolean();
                }
                String text = trimToNull(node.asText(null));
                if ("true".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(text)) {
                    return false;
                }
                throw new IllegalArgumentException("FBN inventory has-next metadata is invalid");
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode... nodes) {
        if (nodes != null) {
            for (JsonNode node : nodes) {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    continue;
                }
                if ((node.isInt() || node.isLong()) && node.canConvertToInt()) {
                    return node.asInt();
                }
                if (node.isIntegralNumber()) {
                    throw invalidTotalPages(null);
                }
                try {
                    String text = trimToNull(node.asText(null));
                    if (text != null) {
                        return Integer.parseInt(text);
                    }
                    throw new IllegalArgumentException(
                            "FBN inventory total-pages metadata is blank"
                    );
                } catch (NumberFormatException invalidInteger) {
                    throw invalidTotalPages(invalidInteger);
                }
            }
        }
        return null;
    }

    private IllegalArgumentException invalidTotalPages(Exception cause) {
        return new IllegalArgumentException(
                "FBN inventory total-pages metadata is invalid",
                cause
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class PaginationEvidence {
        private final Boolean hasNextPage;
        private final Integer totalPages;

        private PaginationEvidence(Boolean hasNextPage, Integer totalPages) {
            this.hasNextPage = hasNextPage;
            this.totalPages = totalPages;
        }
    }
}

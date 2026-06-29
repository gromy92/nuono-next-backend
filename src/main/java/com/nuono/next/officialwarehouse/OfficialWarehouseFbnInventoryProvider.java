package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
public class OfficialWarehouseFbnInventoryProvider {

    static final String FBN_INVENTORY_URL =
            "https://fbn.noon.partners/_svc/sc-fbn/api/v5/seller-lab/fbn-inventory";

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;

    public OfficialWarehouseFbnInventoryProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
    }

    public InventoryPage fetchPage(PullRequest request, int page) {
        if (request == null || request.ownerUserId == null || !StringUtils.hasText(request.storeCode)) {
            throw new IllegalArgumentException("缺少官方仓库存同步店铺范围。");
        }
        int safePage = Math.max(1, page);
        NoonPullStoreBinding binding = bindingResolver.resolve(NoonInterfacePullRequest.builder()
                .ownerUserId(request.ownerUserId)
                .storeCode(request.storeCode)
                .siteCode(request.siteCode)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("official-warehouse-fbn-inventory")
                .targetIdentity("official-warehouse-fbn-inventory:" + request.storeCode)
                .build());
        byte[] responseBytes = sessionFactory.login(binding).postBytes(
                FBN_INVENTORY_URL,
                requestBody(safePage),
                false,
                fbnHeaders(binding)
        );
        ParsedInventoryResponse response = readResponse(responseBytes, safePage);
        return new InventoryPage(safePage, response.hasNextPage, response.items, response.rawResponse);
    }

    private ObjectNode requestBody(int page) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("inventory_tab_name", "export");
        body.set("filters", objectMapper.createObjectNode());
        ObjectNode pagination = body.putObject("pagination");
        pagination.put("page", page);
        return body;
    }

    private Map<String, String> fbnHeaders(NoonPullStoreBinding binding) {
        String site = siteCode(binding).toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/csv,application/json,*/*");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Platform", "web");
        headers.put("X-Project", binding.getProjectCode());
        return headers;
    }

    private ParsedInventoryResponse readResponse(byte[] responseBytes, int page) {
        String responseText = new String(responseBytes == null ? new byte[0] : responseBytes, StandardCharsets.UTF_8);
        JsonNode jsonResponse = tryReadJson(responseText);
        if (jsonResponse != null) {
            return new ParsedInventoryResponse(readItems(jsonResponse), hasNextPage(jsonResponse, page), jsonResponse);
        }
        List<InventoryItem> csvItems = readCsvItems(responseText);
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("source_format", "csv");
        raw.put("row_count", csvItems.size());
        return new ParsedInventoryResponse(csvItems, false, raw);
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
            return null;
        }
    }

    private List<InventoryItem> readItems(JsonNode response) {
        List<InventoryItem> result = new ArrayList<>();
        for (JsonNode row : readRowNodes(response)) {
            result.add(InventoryItem.from(row));
        }
        return result;
    }

    private List<InventoryItem> readCsvItems(String csv) {
        List<List<String>> records = parseRecords(csv);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> headers = normalizedHeaders(records.get(0));
        List<InventoryItem> items = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex += 1) {
            List<String> record = records.get(rowIndex);
            ObjectNode row = objectMapper.createObjectNode();
            for (int column = 0; column < headers.size(); column += 1) {
                String header = headers.get(column);
                if (!StringUtils.hasText(header)) {
                    continue;
                }
                String value = column < record.size() ? trimToNull(record.get(column)) : null;
                if (value != null) {
                    row.put(header, value);
                }
            }
            items.add(InventoryItem.from(row));
        }
        return items;
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
                if (value == '\r' && index + 1 < safeCsv.length() && safeCsv.charAt(index + 1) == '\n') {
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

    private List<JsonNode> readRowNodes(JsonNode response) {
        List<JsonNode> rows = new ArrayList<>();
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
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                rows.add(item);
            }
        }
        return rows;
    }

    private boolean hasNextPage(JsonNode response, int currentPage) {
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
        if (explicit != null) {
            return explicit;
        }
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
        return totalPages != null && currentPage < totalPages;
    }

    private JsonNode firstArray(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
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
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isInt() || node.isLong()) {
                return node.asInt();
            }
            try {
                String text = trimToNull(node.asText(null));
                if (text != null) {
                    return Integer.parseInt(text);
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric pagination metadata from Noon.
            }
        }
        return null;
    }

    private String siteCode(NoonPullStoreBinding binding) {
        return firstNonBlank(binding.getSiteCode(), "SA").toUpperCase(Locale.ROOT);
    }

    private static String stockBucket(String inventoryType, String reasonCode) {
        String type = normalize(inventoryType);
        String reason = normalize(reasonCode);
        if ("SALEABLE".equals(type)) {
            return "SELLABLE";
        }
        if ("GRADED_RETURNS_CIR".equals(type) && "CUSTOMER_RETURN".equals(reason)) {
            return "RETURNED";
        }
        if ("INBOUND_PS".equals(type)) {
            return "RECEIVING_EXCEPTION";
        }
        if ("DAMAGED".equals(type)) {
            return "DAMAGED";
        }
        if ("EXPIRED".equals(type)) {
            return "QUALITY_HOLD";
        }
        if ("LOST".equals(type)) {
            return "LOST";
        }
        return "PENDING_CONFIRMATION";
    }

    private static String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = trimToNull(value.asText(null));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Integer integer(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        try {
            String text = trimToNull(value.asText(null));
            return text == null ? null : Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String normalizeDateTime(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String withoutUtc = trimmed.toUpperCase(Locale.ROOT).endsWith(" UTC")
                ? trimmed.substring(0, trimmed.length() - 4).trim()
                : trimmed;
        return withoutUtc.replaceFirst("^(\\d{4}-\\d{2}-\\d{2}),\\s*", "$1 ");
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class PullRequest {
        public final Long ownerUserId;
        public final String storeCode;
        public final String siteCode;

        public PullRequest(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    public static class InventoryPage {
        public final int page;
        public final boolean hasNextPage;
        public final List<InventoryItem> items;
        public final JsonNode rawResponse;

        public InventoryPage(int page, boolean hasNextPage, List<InventoryItem> items, JsonNode rawResponse) {
            this.page = page;
            this.hasNextPage = hasNextPage;
            this.items = items;
            this.rawResponse = rawResponse;
        }
    }

    private static class ParsedInventoryResponse {
        private final List<InventoryItem> items;
        private final boolean hasNextPage;
        private final JsonNode rawResponse;

        private ParsedInventoryResponse(List<InventoryItem> items, boolean hasNextPage, JsonNode rawResponse) {
            this.items = items;
            this.hasNextPage = hasNextPage;
            this.rawResponse = rawResponse;
        }
    }

    public static class InventoryItem {
        public final String warehouseCode;
        public final Integer quantity;
        public final String inventoryType;
        public final String reasonCode;
        public final String stockBucket;
        public final String barcode;
        public final String pbarcode;
        public final String noonSku;
        public final String partnerSku;
        public final String countryCode;
        public final String classificationCode;
        public final String title;
        public final String brand;
        public final String inventorySnapshotAt;
        public final JsonNode rawPayload;

        private InventoryItem(
                String warehouseCode,
                Integer quantity,
                String inventoryType,
                String reasonCode,
                String stockBucket,
                String barcode,
                String pbarcode,
                String noonSku,
                String partnerSku,
                String countryCode,
                String classificationCode,
                String title,
                String brand,
                String inventorySnapshotAt,
                JsonNode rawPayload
        ) {
            this.warehouseCode = warehouseCode;
            this.quantity = quantity;
            this.inventoryType = inventoryType;
            this.reasonCode = reasonCode;
            this.stockBucket = stockBucket;
            this.barcode = barcode;
            this.pbarcode = pbarcode;
            this.noonSku = noonSku;
            this.partnerSku = partnerSku;
            this.countryCode = countryCode;
            this.classificationCode = classificationCode;
            this.title = title;
            this.brand = brand;
            this.inventorySnapshotAt = inventorySnapshotAt;
            this.rawPayload = rawPayload;
        }

        static InventoryItem from(JsonNode row) {
            String inventoryType = text(row, "inventory_type", "inventoryType");
            String reasonCode = text(row, "reason_code", "reasonCode");
            return new InventoryItem(
                    text(row, "warehouse_code", "warehouseCode", "warehouse"),
                    integer(row, "qty"),
                    inventoryType,
                    reasonCode,
                    stockBucket(inventoryType, reasonCode),
                    text(row, "barcode"),
                    text(row, "pbarcode", "pbarcode_canonical"),
                    text(row, "sku", "noon_sku", "noonSku"),
                    text(row, "partner_sku", "partnerSku", "psku"),
                    text(row, "country_code", "countryCode"),
                    text(row, "classification_code", "classificationCode"),
                    text(row, "title", "product_title", "productTitle"),
                    text(row, "brand"),
                    normalizeDateTime(text(row, "inventory_snapshot_at", "inventorySnapshotAt", "snapshot_at")),
                    row
            );
        }
    }
}

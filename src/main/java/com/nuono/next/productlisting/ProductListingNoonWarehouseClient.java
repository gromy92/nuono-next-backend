package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductListingNoonWarehouseClient {
    private final ObjectMapper objectMapper;

    ProductListingNoonWarehouseClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    List<ProductListingWarehouseView> listWarehouses(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        JsonNode warehouses = warehouseArray(readWarehouseRoot(session, endpoints, headers));
        List<ProductListingWarehouseView> result = new ArrayList<>();
        if (warehouses == null || !warehouses.isArray()) {
            return result;
        }
        for (JsonNode warehouse : warehouses) {
            if (!isActiveWarehouse(warehouse)) {
                continue;
            }
            ProductListingWarehouseView view = toView(warehouse, binding);
            if (StringUtils.hasText(view.getWarehouseCode())
                    && StringUtils.hasText(view.getIdPartnerWarehouse())) {
                result.add(view);
            }
        }
        return result;
    }

    ResolvedWarehouseStock resolveWarehouseStock(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            NoonPullStoreBinding binding,
            ProductListingDraftCommand draft,
            Map<String, String> headers
    ) {
        if (!Boolean.TRUE.equals(draft.getFbp())
                || draft.getQuantity() == null) {
            return null;
        }
        String warehouseId = firstNonBlank(draft.getWarehouseId(), "");
        String warehouseCode = firstNonBlank(draft.getWarehouseCode(), "");
        String warehouseLookupCode = StringUtils.hasText(warehouseCode) ? warehouseCode : warehouseId;
        if (!StringUtils.hasText(warehouseLookupCode)) {
            return null;
        }
        if (warehouseId.matches("\\d+")) {
            return new ResolvedWarehouseStock(Long.parseLong(warehouseId), warehouseCode, null);
        }

        List<ProductListingWarehouseView> warehouses = listWarehouses(session, endpoints, binding, headers);
        List<String> availableCodes = new ArrayList<>();
        for (ProductListingWarehouseView warehouse : warehouses) {
            if (StringUtils.hasText(warehouse.getWarehouseCode()) && availableCodes.size() < 8) {
                availableCodes.add(warehouse.getWarehouseCode());
            }
            if (!warehouseLookupCode.equalsIgnoreCase(warehouse.getWarehouseCode())) {
                continue;
            }
            String resolvedId = firstNonBlank(warehouse.getIdPartnerWarehouse(), "");
            if (!StringUtils.hasText(resolvedId) || !resolvedId.matches("\\d+")) {
                throw new IllegalStateException("Noon warehouse " + warehouseLookupCode + " is missing numeric idPartnerWarehouse.");
            }
            return new ResolvedWarehouseStock(
                    Long.parseLong(resolvedId),
                    firstNonBlank(warehouse.getWarehouseCode(), warehouseLookupCode),
                    warehouse.getIdProcessingTime()
            );
        }
        String suffix = availableCodes.isEmpty() ? "" : " Available warehouse codes: " + String.join(", ", availableCodes) + ".";
        throw new IllegalStateException("Noon warehouse code " + warehouseLookupCode + " was not found." + suffix);
    }

    private JsonNode readWarehouseRoot(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers
    ) {
        Map<String, String> readHeaders = new LinkedHashMap<>();
        if (headers != null) {
            readHeaders.putAll(headers);
        }
        readHeaders.put("Accept", "application/json");
        byte[] body = session.getBytes(endpoints.getWarehouseListUrl(), false, readHeaders);
        if (body == null || body.length == 0) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("解析 Noon 仓库列表失败：" + exception.getMessage(), exception);
        }
    }

    private JsonNode warehouseArray(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("warehouses", "data", "items", "warehouseRespList")) {
            JsonNode candidate = root.get(field);
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isActiveWarehouse(JsonNode warehouse) {
        JsonNode activeNode = firstNode(warehouse, "isWarehouseActive", "active");
        if (activeNode != null && !activeNode.asBoolean(false)) {
            return false;
        }
        String onboardingStatus = textValue(warehouse, "onboardingStatus", "onboarding_status");
        return !StringUtils.hasText(onboardingStatus) || "active".equalsIgnoreCase(onboardingStatus.trim());
    }

    private ProductListingWarehouseView toView(JsonNode warehouse, NoonPullStoreBinding binding) {
        ProductListingWarehouseView view = new ProductListingWarehouseView();
        view.setWarehouseCode(firstNonBlank(textValue(warehouse, "warehouseCode", "warehouse_code")));
        view.setWarehouseName(firstNonBlank(textValue(warehouse, "warehouseName", "warehouse_name", "name")));
        view.setIdPartnerWarehouse(firstNonBlank(
                textValue(warehouse, "idPartnerWarehouse", "id_partner_warehouse"),
                textValue(warehouse, "idWarehouse", "id_warehouse")
        ));
        view.setCountryCode(firstNonBlank(
                textValue(warehouse, "countryCode", "country_code"),
                ProductListingNoonHeaders.upper(binding.getSiteCode())
        ));
        view.setIdProcessingTime(parseInteger(textValue(
                warehouse,
                "defaultIdProcessingTime",
                "idProcessingTime",
                "warehouseProcessingTimeId"
        )));
        return view;
    }

    private JsonNode firstNode(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String... fields) {
        JsonNode value = firstNode(node, fields);
        return value == null ? "" : value.asText("");
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("\\d+")) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    static class ResolvedWarehouseStock {
        private final long idWarehouse;
        private final String warehouseCode;
        private final Integer idProcessingTime;

        ResolvedWarehouseStock(long idWarehouse, String warehouseCode, Integer idProcessingTime) {
            this.idWarehouse = idWarehouse;
            this.warehouseCode = warehouseCode;
            this.idProcessingTime = idProcessingTime;
        }

        long getIdWarehouse() {
            return idWarehouse;
        }

        String getWarehouseCode() {
            return warehouseCode;
        }

        Integer getIdProcessingTime() {
            return idProcessingTime;
        }
    }
}

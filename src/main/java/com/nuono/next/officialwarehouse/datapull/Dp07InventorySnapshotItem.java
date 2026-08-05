package com.nuono.next.officialwarehouse.datapull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.util.StringUtils;

/** Validated one-row fact staged by DP-07-A. */
public final class Dp07InventorySnapshotItem {

    private final String warehouseCode;
    private final int quantity;
    private final String inventoryType;
    private final String reasonCode;
    private final String stockBucket;
    private final String barcode;
    private final String pbarcode;
    private final String noonSku;
    private final String partnerSku;
    private final String countryCode;
    private final String classificationCode;
    private final String title;
    private final String brand;
    private final String inventorySnapshotAt;
    private final String rawPayloadJson;
    private final String stableIdentity;

    private Dp07InventorySnapshotItem(
            String warehouseCode,
            int quantity,
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
            String rawPayloadJson
    ) {
        this.warehouseCode = Dp07InventoryColumnContract.fit(
                requireProviderIdentity(warehouseCode, "warehouseCode"), 100, "warehouseCode"
        );
        if (quantity < 0) {
            throw Dp07InventoryColumnContract.defect(
                    "DP-07-A quantity must not be negative"
            );
        }
        this.quantity = quantity;
        this.inventoryType = Dp07InventoryColumnContract.fit(
                requireProviderIdentity(inventoryType, "inventoryType"), 100, "inventoryType"
        );
        this.reasonCode = Dp07InventoryColumnContract.fit(
                optionalProviderText(reasonCode), 100, "reasonCode"
        );
        this.stockBucket = Dp07InventoryColumnContract.fit(
                requireProviderIdentity(stockBucket, "stockBucket"), 60, "stockBucket"
        );
        this.barcode = Dp07InventoryColumnContract.fit(
                optionalProviderText(barcode), 100, "barcode"
        );
        this.pbarcode = Dp07InventoryColumnContract.fit(
                optionalProviderText(pbarcode), 100, "pbarcode"
        );
        this.noonSku = Dp07InventoryColumnContract.fit(
                optionalProviderText(noonSku), 100, "noonSku"
        );
        this.partnerSku = Dp07InventoryColumnContract.fit(
                optionalProviderText(partnerSku), 100, "partnerSku"
        );
        if (!hasText(this.barcode) && !hasText(this.pbarcode)
                && !hasText(this.noonSku) && !hasText(this.partnerSku)) {
            throw Dp07InventoryColumnContract.defect(
                    "DP-07-A item has no product identity"
            );
        }
        this.countryCode = Dp07InventoryColumnContract.fit(
                optionalProviderText(countryCode), 20, "countryCode"
        );
        this.classificationCode = Dp07InventoryColumnContract.fit(
                optionalProviderText(classificationCode), 100, "classificationCode"
        );
        this.title = Dp07InventoryColumnContract.fit(
                optionalProviderText(title), 1000, "title"
        );
        this.brand = Dp07InventoryColumnContract.fit(
                optionalProviderText(brand), 255, "brand"
        );
        this.inventorySnapshotAt = Dp07InventoryColumnContract.mysqlDateTime(
                optionalProviderText(inventorySnapshotAt)
        );
        this.rawPayloadJson = Dp07InventoryColumnContract.boundedJson(
                requireJson(rawPayloadJson)
        );
        this.stableIdentity = buildStableIdentity();
    }

    /** Only deterministic row business defects are skipped; required structure errors propagate. */
    public static Optional<Dp07InventorySnapshotItem> fromProvider(
            InventoryItem item,
            ObjectMapper objectMapper
    ) {
        try {
            InventoryItem value = item;
            if (value == null) {
                throw new ProviderRowContractException("DP-07-A inventory row is required");
            }
            if (value.quantity == null) {
                throw new ProviderRowContractException("DP-07-A quantity is required");
            }
            return Optional.of(new Dp07InventorySnapshotItem(
                    value.warehouseCode,
                    value.quantity,
                    value.inventoryType,
                    value.reasonCode,
                    value.stockBucket,
                    value.barcode,
                    value.pbarcode,
                    value.noonSku,
                    value.partnerSku,
                    value.countryCode,
                    value.classificationCode,
                    value.title,
                    value.brand,
                    value.inventorySnapshotAt,
                    canonicalJson(objectMapper, value.rawPayload)
            ));
        } catch (Dp07InventoryColumnContract.BusinessDefect deterministicBusinessDefect) {
            return Optional.empty();
        }
    }

    static Dp07InventorySnapshotItem restore(
            String warehouseCode,
            int quantity,
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
            String rawPayloadJson
    ) {
        return new Dp07InventorySnapshotItem(
                warehouseCode,
                quantity,
                inventoryType,
                reasonCode,
                stockBucket,
                barcode,
                pbarcode,
                noonSku,
                partnerSku,
                countryCode,
                classificationCode,
                title,
                brand,
                inventorySnapshotAt,
                rawPayloadJson
        );
    }

    public String getStableIdentity() { return stableIdentity; }
    public String getWarehouseCode() { return warehouseCode; }
    public int getQuantity() { return quantity; }
    public String getInventoryType() { return inventoryType; }
    public String getReasonCode() { return reasonCode; }
    public String getStockBucket() { return stockBucket; }
    public String getBarcode() { return barcode; }
    public String getPbarcode() { return pbarcode; }
    public String getNoonSku() { return noonSku; }
    public String getPartnerSku() { return partnerSku; }
    public String getCountryCode() { return countryCode; }
    public String getClassificationCode() { return classificationCode; }
    public String getTitle() { return title; }
    public String getBrand() { return brand; }
    public String getInventorySnapshotAt() { return inventorySnapshotAt; }
    public String getRawPayloadJson() { return rawPayloadJson; }

    private String buildStableIdentity() {
        return Dp07InventoryColumnContract.stableIdentity(
                warehouseCode, inventoryType, reasonCode, partnerSku, noonSku,
                pbarcode, barcode, countryCode, classificationCode
        );
    }

    static String canonicalJson(ObjectMapper objectMapper, JsonNode rawPayload) {
        try {
            ObjectMapper mapper = Objects.requireNonNull(objectMapper, "objectMapper");
            JsonNode raw = Objects.requireNonNull(rawPayload, "rawPayload");
            if (!raw.isObject()) {
                throw new IllegalArgumentException("DP-07-A raw row must be an object");
            }
            return mapper.writeValueAsString(canonicalNode(mapper, raw));
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("DP-07-A raw row cannot be encoded", failure);
        }
    }

    static String canonicalizeRawPayload(ObjectMapper objectMapper, String rawPayloadJson) {
        try {
            ObjectMapper mapper = Objects.requireNonNull(objectMapper, "objectMapper");
            JsonNode raw = mapper.readTree(requireJson(rawPayloadJson));
            if (raw == null || !raw.isObject()) {
                throw new IllegalArgumentException("DP-07-A raw row must be an object");
            }
            return mapper.writeValueAsString(canonicalNode(mapper, raw));
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-07-A raw row JSON is invalid", failure);
        }
    }

    private static JsonNode canonicalNode(ObjectMapper mapper, JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                result.set(name, canonicalNode(mapper, node.get(name)));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (JsonNode item : node) {
                result.add(canonicalNode(mapper, item));
            }
            return result;
        }
        return node.deepCopy();
    }

    private static String requireJson(String value) {
        String text = optionalText(value);
        if (text == null || text.indexOf('\0') >= 0
                || !text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("DP-07-A raw row JSON is invalid");
        }
        return text;
    }

    private static String requireProviderIdentity(String value, String name) {
        String result = optionalProviderText(value);
        if (!hasText(result) || result.indexOf('\0') >= 0) {
            throw new ProviderRowContractException("DP-07-A " + name + " is required");
        }
        return result;
    }

    private static String optionalProviderText(String value) {
        String result = optionalText(value);
        if (result != null && result.indexOf('\0') >= 0) {
            throw Dp07InventoryColumnContract.defect("DP-07-A item text is invalid");
        }
        return result;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return StringUtils.hasText(result) ? result : null;
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    static final class ProviderRowContractException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        ProviderRowContractException(String message) {
            super(message);
        }

        ProviderRowContractException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}

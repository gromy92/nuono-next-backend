package com.nuono.next.officialwarehouse.datapull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.snapshot.SnapshotItemDescriptor;
import com.nuono.next.datapull.snapshot.SnapshotPayloadCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Strict fixed-field staging codec for DP-07-A inventory rows. */
public final class Dp07InventorySnapshotCodec
        implements SnapshotPayloadCodec<Dp07InventorySnapshotItem>,
        SnapshotItemDescriptor<Dp07InventorySnapshotItem> {

    private static final int VERSION = 1;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "warehouseCode", "quantity", "inventoryType", "reasonCode",
            "stockBucket", "barcode", "pbarcode", "noonSku", "partnerSku", "countryCode",
            "classificationCode", "title", "brand", "inventorySnapshotAt", "rawPayloadJson"
    );
    private final ObjectMapper objectMapper;

    public Dp07InventorySnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    @Override
    public String encode(Dp07InventorySnapshotItem item) {
        try {
            Dp07InventorySnapshotItem value = java.util.Objects.requireNonNull(item, "item");
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", VERSION);
            put(root, "warehouseCode", value.getWarehouseCode());
            root.put("quantity", value.getQuantity());
            put(root, "inventoryType", value.getInventoryType());
            put(root, "reasonCode", value.getReasonCode());
            put(root, "stockBucket", value.getStockBucket());
            put(root, "barcode", value.getBarcode());
            put(root, "pbarcode", value.getPbarcode());
            put(root, "noonSku", value.getNoonSku());
            put(root, "partnerSku", value.getPartnerSku());
            put(root, "countryCode", value.getCountryCode());
            put(root, "classificationCode", value.getClassificationCode());
            put(root, "title", value.getTitle());
            put(root, "brand", value.getBrand());
            put(root, "inventorySnapshotAt", value.getInventorySnapshotAt());
            put(root, "rawPayloadJson", value.getRawPayloadJson());
            String payload = objectMapper.writeValueAsString(root);
            if (payload.getBytes(StandardCharsets.UTF_8).length
                    > Dp07InventoryColumnContract.MAX_STAGE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("DP-07-A staging payload is too large");
            }
            return payload;
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-07-A item cannot be encoded", failure);
        }
    }

    @Override
    public Dp07InventorySnapshotItem decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            requireRoot(root);
            String rawPayloadJson = text(root, "rawPayloadJson");
            if (!Dp07InventorySnapshotItem.canonicalizeRawPayload(objectMapper, rawPayloadJson)
                    .equals(rawPayloadJson)) {
                throw new IllegalArgumentException("DP-07-A raw row JSON is not canonical");
            }
            Dp07InventorySnapshotItem item = Dp07InventorySnapshotItem.restore(
                    text(root, "warehouseCode"),
                    root.path("quantity").asInt(-1),
                    text(root, "inventoryType"),
                    text(root, "reasonCode"),
                    text(root, "stockBucket"),
                    text(root, "barcode"),
                    text(root, "pbarcode"),
                    text(root, "noonSku"),
                    text(root, "partnerSku"),
                    text(root, "countryCode"),
                    text(root, "classificationCode"),
                    text(root, "title"),
                    text(root, "brand"),
                    text(root, "inventorySnapshotAt"),
                    rawPayloadJson
            );
            if (!encode(item).equals(payload)) {
                throw new IllegalArgumentException("DP-07-A staging payload is not canonical");
            }
            return item;
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-07-A staging payload is invalid", failure);
        }
    }

    @Override
    public String stableIdentity(Dp07InventorySnapshotItem item) {
        return java.util.Objects.requireNonNull(item, "item").getStableIdentity();
    }

    @Override
    public String stableContentFingerprint(Dp07InventorySnapshotItem item) {
        return sha256(encode(item));
    }

    private void requireRoot(JsonNode root) {
        if (root == null || !root.isObject()
                || root.path("schemaVersion").asInt(-1) != VERSION
                || !root.path("quantity").canConvertToInt()) {
            throw new IllegalArgumentException("unsupported DP-07-A staging payload");
        }
        Set<String> fields = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        names.forEachRemaining(fields::add);
        if (!ROOT_FIELDS.equals(fields)) {
            throw new IllegalArgumentException("unexpected DP-07-A staging fields");
        }
    }

    private void put(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }
}

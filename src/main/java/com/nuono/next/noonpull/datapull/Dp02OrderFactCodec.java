package com.nuono.next.noonpull.datapull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.snapshot.SnapshotItemDescriptor;
import com.nuono.next.datapull.snapshot.SnapshotPayloadCodec;
import com.nuono.next.noonpull.NoonOrderFactColumnContract;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Strict fixed-field staging codec for one persistable DP02 order fact. */
public final class Dp02OrderFactCodec
        implements SnapshotPayloadCodec<NoonOrderLineFact>,
        SnapshotItemDescriptor<NoonOrderLineFact> {
    private static final int VERSION = 1;
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "ownerUserId", "storeCode", "siteCode", "idPartner",
            "sourceCountry", "countryCode", "destinationCountry", "bayanNr",
            "orderLineIdentity", "orderIdentity", "partnerSku", "sku", "status",
            "offerPrice", "gmvLcy", "currencyCode", "brandCode", "family",
            "fulfillmentModel", "orderTimestamp", "shipmentTimestamp",
            "deliveredTimestamp", "reportDateFrom", "reportDateTo", "sourceBatchId"
    );
    private final ObjectMapper objectMapper;

    public Dp02OrderFactCodec(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(
                objectMapper, "objectMapper"
        ).copy();
    }

    @Override
    public String encode(NoonOrderLineFact fact) {
        try {
            NoonOrderLineFact value = NoonOrderFactColumnContract.requirePersistable(
                    java.util.Objects.requireNonNull(fact, "fact")
            );
            ObjectNode node = objectMapper.createObjectNode();
            node.put("schemaVersion", VERSION);
            node.put("ownerUserId", value.getOwnerUserId());
            put(node, "storeCode", value.getStoreCode());
            put(node, "siteCode", value.getSiteCode());
            put(node, "idPartner", value.getIdPartner());
            put(node, "sourceCountry", value.getSourceCountry());
            put(node, "countryCode", value.getCountryCode());
            put(node, "destinationCountry", value.getDestinationCountry());
            put(node, "bayanNr", value.getBayanNr());
            put(node, "orderLineIdentity", value.getOrderLineIdentity());
            put(node, "orderIdentity", value.getOrderIdentity());
            put(node, "partnerSku", value.getPartnerSku());
            put(node, "sku", value.getSku());
            put(node, "status", value.getStatus());
            put(node, "offerPrice", value.getOfferPrice().toPlainString());
            put(node, "gmvLcy", value.getGmvLcy().toPlainString());
            put(node, "currencyCode", value.getCurrencyCode());
            put(node, "brandCode", value.getBrandCode());
            put(node, "family", value.getFamily());
            put(node, "fulfillmentModel", value.getFulfillmentModel());
            put(node, "orderTimestamp", text(value.getOrderTimestamp()));
            put(node, "shipmentTimestamp", text(value.getShipmentTimestamp()));
            put(node, "deliveredTimestamp", text(value.getDeliveredTimestamp()));
            put(node, "reportDateFrom", text(value.getReportDateFrom()));
            put(node, "reportDateTo", text(value.getReportDateTo()));
            put(node, "sourceBatchId", value.getSourceBatchId());
            return objectMapper.writeValueAsString(node);
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP02 order fact cannot be encoded", failure);
        }
    }

    @Override
    public NoonOrderLineFact decode(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            requireFields(node);
            if (node.path("schemaVersion").asInt(-1) != VERSION) {
                throw new IllegalArgumentException("unsupported DP02 staging payload");
            }
            NoonOrderLineFact fact = NoonOrderFactColumnContract.requirePersistable(
                    new NoonOrderLineFact(
                            requiredLong(node, "ownerUserId"),
                            requiredText(node, "storeCode"),
                            requiredText(node, "siteCode"),
                            requiredText(node, "idPartner"),
                            requiredText(node, "sourceCountry"),
                            requiredText(node, "countryCode"),
                            requiredText(node, "destinationCountry"),
                            nullableText(node, "bayanNr"),
                            requiredText(node, "orderLineIdentity"),
                            requiredText(node, "orderIdentity"),
                            requiredText(node, "partnerSku"),
                            requiredText(node, "sku"),
                            requiredText(node, "status"),
                            new BigDecimal(requiredText(node, "offerPrice")),
                            new BigDecimal(requiredText(node, "gmvLcy")),
                            requiredText(node, "currencyCode"),
                            requiredText(node, "brandCode"),
                            requiredText(node, "family"),
                            requiredText(node, "fulfillmentModel"),
                            LocalDateTime.parse(requiredText(node, "orderTimestamp")),
                            nullableDateTime(node, "shipmentTimestamp"),
                            nullableDateTime(node, "deliveredTimestamp"),
                            LocalDate.parse(requiredText(node, "reportDateFrom")),
                            LocalDate.parse(requiredText(node, "reportDateTo")),
                            requiredText(node, "sourceBatchId")
                    )
            );
            if (!encode(fact).equals(payload)) {
                throw new IllegalArgumentException("DP02 staging payload is not canonical");
            }
            return fact;
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP02 staging payload is invalid", failure);
        }
    }

    @Override
    public String stableIdentity(NoonOrderLineFact fact) {
        return java.util.Objects.requireNonNull(fact, "fact").naturalKey();
    }

    @Override
    public String stableContentFingerprint(NoonOrderLineFact fact) {
        return sha256(encode(fact));
    }

    private void requireFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("DP02 staging fact must be an object");
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(actual::add);
        if (!FIELDS.equals(actual)) {
            throw new IllegalArgumentException("DP02 staging fact fields are invalid");
        }
    }

    private Long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException("DP02 " + field + " is invalid");
        }
        return value.asLong();
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("DP02 " + field + " is required");
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDateTime nullableDateTime(JsonNode node, String field) {
        String value = nullableText(node, field);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private void put(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
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

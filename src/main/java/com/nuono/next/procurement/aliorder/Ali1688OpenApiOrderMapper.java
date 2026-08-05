package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** Maps 1688 list/detail JSON to the provider-neutral order payload. */
final class Ali1688OpenApiOrderMapper {
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_OFFSET = DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ");
    private static final DateTimeFormatter COMPACT_MILLIS_OFFSET = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSZ");
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiJson json;

    Ali1688OpenApiOrderMapper(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiJson json
    ) {
        this.properties = properties;
        this.json = json;
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot merge(
            Ali1688HistoricalOrderProvider.OrderSnapshot base,
            Ali1688HistoricalOrderProvider.OrderSnapshot detail
    ) {
        if (detail == null) return base;
        if (!StringUtils.hasText(detail.getProviderOrderNo())) detail.setProviderOrderNo(base.getProviderOrderNo());
        if (!StringUtils.hasText(detail.getOrderTime())) detail.setOrderTime(base.getOrderTime());
        if (!StringUtils.hasText(detail.getPaidAt())) detail.setPaidAt(base.getPaidAt());
        if (!StringUtils.hasText(detail.getBuyerCompanyName())) detail.setBuyerCompanyName(base.getBuyerCompanyName());
        if (!StringUtils.hasText(detail.getBuyerMemberName())) detail.setBuyerMemberName(base.getBuyerMemberName());
        if (!StringUtils.hasText(detail.getSupplierName())) detail.setSupplierName(base.getSupplierName());
        if (!StringUtils.hasText(detail.getSellerMemberName())) detail.setSellerMemberName(base.getSellerMemberName());
        if (detail.getProviderModifiedAt() == null) detail.setProviderModifiedAt(base.getProviderModifiedAt());
        return detail;
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot map(JsonNode orderNode) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        if (orderNode == null || orderNode.isNull()) return order;
        JsonNode base = json.firstObject(orderNode, "baseInfo");
        JsonNode source = base == null ? orderNode : base;
        JsonNode buyer = json.firstObject(source, "buyerContact");
        JsonNode seller = json.firstObject(source, "sellerContact");
        JsonNode logistics = json.firstObject(orderNode, "nativeLogistics");
        JsonNode receiver = json.firstObject(source, "receiverInfo");
        order.setProviderOrderNo(first(source, orderNode, "idOfStr", "id", "orderId", "orderIdStr", "providerOrderNo"));
        order.setOrderTime(normalize(first(source, orderNode, "createTime", "gmtCreate", "orderTime")));
        order.setPaidAt(normalize(first(source, orderNode, "payTime", "gmtPayment", "paidAt")));
        order.setBuyerCompanyName(defaultText(json.text(source, "buyerCompanyName", "buyerCompany"), json.text(buyer, "companyName", "name")));
        order.setBuyerMemberName(first(source, orderNode, "buyerLoginId", "buyerMemberName", "buyerMemberId"));
        order.setSupplierName(defaultText(json.text(source, "sellerCompanyName", "supplierName", "sellerName"), json.text(seller, "companyName", "name")));
        order.setSellerMemberName(first(source, orderNode, "sellerLoginId", "sellerMemberName", "sellerMemberId"));
        order.setGoodsTotalText(first(source, orderNode, "sumProductPayment", "goodsTotalText", "goodsTotal"));
        order.setFreightText(first(source, orderNode, "shippingFee", "freightText", "freight", "shipFee"));
        order.setPaidAmountText(first(source, orderNode, "sumPayment", "paidAmountText", "paidAmount", "totalAmount"));
        order.setAmountText(defaultText(json.text(orderNode, "amountText", "sumPayment", "totalAmount"), order.getPaidAmountText()));
        order.setCurrency(defaultText(first(source, orderNode, "currency"), "CNY"));
        order.setOrderStatus(first(source, orderNode, "status", "orderStatus"));
        order.setLogisticsStatus(first(source, orderNode, "logisticsStatus", "shippingStatus"));
        order.setShipperName(json.text(orderNode, "shipperName"));
        order.setOriginalUrl(json.text(orderNode, "originalUrl", "orderUrl"));
        order.setReceiverName(defaultText(json.text(orderNode, "receiverName"), defaultText(json.text(logistics, "contactPerson"), json.text(receiver, "toFullName"))));
        order.setReceiverPostalCode(defaultText(json.text(logistics, "zip"), json.text(receiver, "toPost")));
        order.setReceiverPhone(defaultText(json.text(orderNode, "receiverPhone", "receiverMobile", "receiverTelephone"), json.text(buyer, "phone", "mobile")));
        order.setReceiverMobile(defaultText(json.text(orderNode, "receiverMobile"), json.text(buyer, "mobile")));
        order.setReceiverAddress(defaultText(json.text(orderNode, "receiverAddress"), json.text(logistics, "address")));
        order.setBuyerRemark(first(source, orderNode, "buyerRemark", "buyerMemo"));
        order.setSupplierContact(defaultText(json.text(orderNode, "supplierContact"), json.text(seller, "name", "phone", "mobile")));
        order.setInitiatorLoginName(defaultText(json.text(orderNode, "initiatorLoginName", "buyerLoginId"), order.getBuyerMemberName()));
        String[] modifiedFields = configuredFields(properties.getModifiedAtResponseFieldNames());
        order.setProviderModifiedAt(parseInstant(defaultText(
                json.text(source, modifiedFields),
                json.text(orderNode, modifiedFields)
        )));
        order.setRawSnapshotJson(json.write(orderNode));
        for (JsonNode item : json.arrayValues(
                orderNode,
                "productItems", "orderItems", "items", "cargoList", "entries"
        )) {
            order.getItems().add(mapItem(item));
        }
        return order;
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot mapItem(JsonNode node) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setProviderSubOrderId(json.text(
                node,
                "subOrderId", "subOrderID", "subOrderIdStr",
                "subItemId", "subItemID", "subItemIdStr"
        ));
        item.setProviderItemId(json.text(
                node,
                "itemId", "itemID", "itemIdStr", "orderEntryId", "orderEntryID", "id"
        ));
        item.setOfferId(json.text(node, "offerId", "offerID", "productID"));
        item.setSkuId(json.text(node, "skuId", "skuID", "specId"));
        item.setTitle(json.text(node, "name", "title", "productName"));
        item.setSkuText(defaultText(json.text(node, "skuInfo", "skuText", "spec"), skuText(node.get("skuInfos"))));
        item.setModelText(json.text(node, "modelText"));
        item.setProductCode(json.text(node, "productCode", "cargoNumber"));
        item.setSingleProductCode(json.text(node, "productCargoNumber", "singleProductCode", "cargoNumber"));
        item.setQuantity(json.integer(node, "quantity", "amount", "num"));
        item.setUnit(json.text(node, "unit", "unitName"));
        item.setUnitPriceText(json.text(node, "price", "unitPriceText", "unitPrice"));
        item.setAmountText(json.text(node, "itemAmount", "amountText", "productPayment"));
        item.setImageUrl(json.text(node, "imageUrl", "mainImageUrl", "productImgUrl"));
        item.setLogisticsCompany(json.text(node, "logisticsCompany", "logisticsCompanyName", "expressName"));
        item.setTrackingNo(json.text(node, "trackingNo", "logisticsBillNo", "expressNo"));
        JsonNode logistics = json.firstObject(node, "logistics", "logisticsInfo");
        if (logistics != null) {
            item.setLogisticsCompany(defaultText(item.getLogisticsCompany(), json.text(logistics, "logisticsCompany", "logisticsCompanyName")));
            item.setTrackingNo(defaultText(item.getTrackingNo(), json.text(logistics, "trackingNo", "logisticsBillNo")));
        }
        item.setRawSnapshotJson(json.write(node));
        return item;
    }

    private String skuText(JsonNode node) {
        if (node == null || !node.isArray()) return null;
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String name = json.text(item, "name");
            String value = json.text(item, "value");
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) values.add(name + "：" + value);
            else if (StringUtils.hasText(value)) values.add(value);
        }
        return values.isEmpty() ? null : String.join(" / ", values);
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        try { return Instant.parse(text); } catch (RuntimeException ignored) { }
        try { return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(); }
        catch (RuntimeException ignored) { }
        try {
            if (text.matches("\\d{17}[+-]\\d{4}")) return OffsetDateTime.parse(text, COMPACT_MILLIS_OFFSET).toInstant();
            if (text.matches("\\d{14}[+-]\\d{4}")) return OffsetDateTime.parse(text, COMPACT_OFFSET).toInstant();
            if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(text, MYSQL_DATETIME)
                        .atZone(ZoneId.of(properties.getProviderZoneId().trim())).toInstant();
            }
        } catch (RuntimeException ignored) { return null; }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return value;
        String text = value.trim();
        if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) return text;
        try {
            if (text.matches("\\d{17}[+-]\\d{4}")) return OffsetDateTime.parse(text, COMPACT_MILLIS_OFFSET).toLocalDateTime().format(MYSQL_DATETIME);
            if (text.matches("\\d{14}[+-]\\d{4}")) return OffsetDateTime.parse(text, COMPACT_OFFSET).toLocalDateTime().format(MYSQL_DATETIME);
        } catch (RuntimeException ignored) { return text; }
        return text;
    }

    private String first(JsonNode first, JsonNode second, String... names) {
        return defaultText(json.text(first, names), json.text(second, names));
    }

    private String[] configuredFields(String fields) {
        if (!StringUtils.hasText(fields)) return new String[0];
        return java.util.Arrays.stream(fields.split(","))
                .map(String::trim).filter(name -> name.matches("[A-Za-z][A-Za-z0-9_]*"))
                .distinct().toArray(String[]::new);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}

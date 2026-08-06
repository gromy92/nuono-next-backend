package com.nuono.next.procurement.aliorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Pure schema-bound proof that one DP-10 order can reach all owned fact rows.
 * Widths mirror the header/item/logistics definitions in schema migrations 071 and 127.
 */
public final class Ali1688HistoricalOrderFactPreflight {
    private static final Instant MYSQL_DATETIME_MIN =
            Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MYSQL_DATETIME_MAX_MILLIS =
            Instant.parse("9999-12-31T23:59:59.999Z");
    private static final BigDecimal DECIMAL_18_4_OVERFLOW_ROUNDING_BOUNDARY =
            new BigDecimal("99999999999999.99995");
    private static final DateTimeFormatter MYSQL_DATETIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    public Decision inspectStage(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        if (order == null || !StringUtils.hasText(order.getProviderOrderNo())) {
            return Decision.rejected("DP10_ORDER_IDENTITY_MISSING");
        }
        String identity = order.getProviderOrderNo();
        if (!identity.equals(identity.trim())
                || !wellFormedUnicode(identity)
                || characterCount(identity) > 120) {
            return Decision.rejected("DP10_ORDER_IDENTITY_INVALID");
        }
        Instant modifiedAt = order.getProviderModifiedAt();
        if (modifiedAt == null) {
            return Decision.rejected("DP10_ORDER_MODIFIED_AT_MISSING");
        }
        if (modifiedAt.isBefore(MYSQL_DATETIME_MIN)
                || modifiedAt.isAfter(MYSQL_DATETIME_MAX_MILLIS)) {
            return Decision.rejected("DP10_ORDER_MODIFIED_AT_INVALID");
        }
        return Decision.accepted();
    }

    public Decision inspectFact(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        Decision stage = inspectStage(order);
        if (!stage.isAccepted()) return stage;

        String failure = orderTextFailure(order);
        if (failure != null) return Decision.rejected(failure);
        if (!validOrderTime(order.getOrderTime())) {
            return Decision.rejected("DP10_FACT_ORDER_TIME_INVALID");
        }
        BigDecimal amount = mappedAmount(order.getAmountText());
        if (amount != null && !fitsDecimal18Scale4(amount)) {
            return Decision.rejected("DP10_FACT_AMOUNT_OUT_OF_RANGE");
        }

        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return Decision.rejected("DP10_FACT_ITEMS_MISSING");
        }
        for (Ali1688HistoricalOrderProvider.OrderItemSnapshot item : items) {
            failure = itemFailure(item);
            if (failure != null) return Decision.rejected(failure);
        }
        return Decision.accepted();
    }

    static BigDecimal mappedAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String orderTextFailure(Ali1688HistoricalOrderProvider.OrderSnapshot order) {
        String failure = textFailure(order.getPaidAt(), 40, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getBuyerCompanyName(), 300, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getBuyerMemberName(), 160, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getSupplierName(), 300, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getSellerMemberName(), 160, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getGoodsTotalText(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getFreightText(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getAdjustmentText(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getPaidAmountText(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getAmountText(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getCurrency(), 20, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getOrderStatus(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getLogisticsStatus(), 80, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getShipperName(), 160, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getOriginalUrl(), 800, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverName(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverPostalCode(), 40, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverTelephone(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverMobile(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverPhone(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getReceiverAddress(), 1000, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getBuyerRemark(), 1000, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getSupplierContact(), 500, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getInitiatorLoginName(), 160, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getSourceBatchNo(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(order.getDownstreamOrderNo(), 120, "DP10_FACT_ORDER_TEXT_TOO_LONG");
        if (failure == null) failure = unicodeFailure(order.getRawSnapshotJson());
        if (failure == null) failure = unicodeFailure(order.getOrderTime());
        return failure;
    }

    private String itemFailure(Ali1688HistoricalOrderProvider.OrderItemSnapshot item) {
        if (item == null || !hasStableItemIdentity(item)) {
            return "DP10_FACT_ITEM_IDENTITY_INVALID";
        }
        String failure = textFailure(
                item.getProviderSubOrderId(), 300, "DP10_FACT_ITEM_IDENTITY_INVALID");
        if (failure == null) failure = textFailure(
                item.getProviderItemId(), 300, "DP10_FACT_ITEM_IDENTITY_INVALID");
        if (failure == null) failure = textFailure(item.getOfferId(), 80, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getSkuId(), 120, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getTitle(), 500, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getSkuText(), 300, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getModelText(), 300, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getProductCode(), 160, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getSingleProductCode(), 160, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getUnit(), 60, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getUnitPriceText(), 80, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getAmountText(), 80, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = textFailure(item.getImageUrl(), 800, "DP10_FACT_ITEM_TEXT_TOO_LONG");
        if (failure == null) failure = unicodeFailure(item.getRawSnapshotJson());
        if (failure == null && (StringUtils.hasText(item.getLogisticsCompany())
                || StringUtils.hasText(item.getTrackingNo()))) {
            failure = textFailure(
                    item.getLogisticsCompany(),
                    200,
                    "DP10_FACT_LOGISTICS_TEXT_TOO_LONG"
            );
            if (failure == null) {
                failure = textFailure(
                        item.getTrackingNo(),
                        160,
                        "DP10_FACT_LOGISTICS_TEXT_TOO_LONG"
                );
            }
        }
        return failure;
    }

    public static boolean hasStableItemIdentity(
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item
    ) {
        return item != null && (StringUtils.hasText(item.getProviderSubOrderId())
                || StringUtils.hasText(item.getProviderItemId())
                || StringUtils.hasText(item.getOfferId())
                || StringUtils.hasText(item.getSkuId())
                || StringUtils.hasText(item.getProductCode())
                || StringUtils.hasText(item.getSingleProductCode()));
    }

    private boolean validOrderTime(String value) {
        if (value == null) return true;
        if (value.isBlank() || !value.equals(value.trim()) || !wellFormedUnicode(value)) {
            return false;
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(value, MYSQL_DATETIME);
            return parsed.getYear() >= 1000 && parsed.getYear() <= 9999;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private boolean fitsDecimal18Scale4(BigDecimal value) {
        return value.abs().compareTo(DECIMAL_18_4_OVERFLOW_ROUNDING_BOUNDARY) < 0;
    }

    private String textFailure(String value, int maxCharacters, String lengthCode) {
        String unicode = unicodeFailure(value);
        if (unicode != null) return unicode;
        return value != null && characterCount(value) > maxCharacters ? lengthCode : null;
    }

    private String unicodeFailure(String value) {
        return value != null && !wellFormedUnicode(value)
                ? "DP10_FACT_TEXT_ENCODING_INVALID" : null;
    }

    private int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private boolean wellFormedUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index))) return false;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    public static final class Decision {
        private static final Decision ACCEPTED = new Decision(null);
        private final String sanitizedCode;

        private Decision(String sanitizedCode) {
            this.sanitizedCode = sanitizedCode;
        }

        static Decision accepted() { return ACCEPTED; }
        static Decision rejected(String code) { return new Decision(code); }
        public boolean isAccepted() { return sanitizedCode == null; }
        public String getSanitizedCode() { return sanitizedCode; }
    }
}

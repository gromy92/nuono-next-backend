package com.nuono.next.logisticsquote;

import java.util.Map;
import java.util.StringJoiner;

public class LogisticsQuoteFactNaturalKey {

    public String resolve(LogisticsQuotePublishedItem item) {
        if (item == null) {
            return null;
        }
        String provided = text(item.getNaturalKey());
        if (!isBlank(provided)) {
            return provided;
        }
        LogisticsQuoteFactType factType = LogisticsQuoteFactType.fromItemType(item.getItemType());
        Map<String, Object> payload = item.getPayload();
        if (LogisticsQuoteFactType.SERVICE_LINE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("country"),
                    payload.get("fulfillmentMode"),
                    payload.get("transportMode"),
                    payload.get("serviceScope"),
                    payload.get("destinationNode")
            );
        }
        if (LogisticsQuoteFactType.CARGO_CATEGORY == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("serviceLineKey"),
                    firstNonBlank(payload.get("categoryCode"), payload.get("categoryName"))
            );
        }
        if (LogisticsQuoteFactType.PRICE_RULE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("serviceLineKey"),
                    payload.get("cargoCategoryKey"),
                    payload.get("billingUnit"),
                    payload.get("pricingModel")
            );
        }
        if (LogisticsQuoteFactType.SURCHARGE_RULE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("serviceLineKey"),
                    payload.get("surchargeName")
            );
        }
        if (LogisticsQuoteFactType.BILLING_RULE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("serviceLineKey"),
                    payload.get("cargoCategoryKey"),
                    payload.get("ruleName")
            );
        }
        if (LogisticsQuoteFactType.RESTRICTION_RULE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("serviceLineKey"),
                    payload.get("restrictionType"),
                    payload.get("itemText")
            );
        }
        if (LogisticsQuoteFactType.WAREHOUSE_FEE_RULE == factType) {
            return join(
                    payload.get("forwarderCode"),
                    payload.get("country"),
                    payload.get("warehouseNode"),
                    payload.get("serviceName"),
                    payload.get("serviceType")
            );
        }
        return join(payload.get("forwarderCode"), payload.get("serviceLineKey"), payload.get("ruleName"), payload.get("serviceName"));
    }

    private Object firstNonBlank(Object first, Object second) {
        return isBlank(text(first)) ? second : first;
    }

    private String join(Object... values) {
        StringJoiner joiner = new StringJoiner("|");
        for (Object value : values) {
            joiner.add(text(value));
        }
        return joiner.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

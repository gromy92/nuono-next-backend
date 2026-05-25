package com.nuono.next.noonpull;

import java.util.Arrays;
import java.util.List;

public final class NoonOrderReportDescriptor {
    public static final String EXPORT_CATEGORY_CODE = "noon_noonoms_ordersexport";
    public static final String SOURCE_SYSTEM = "noon_order_report";

    private NoonOrderReportDescriptor() {
    }

    public static List<String> requiredColumns() {
        return Arrays.asList(
                "id_partner",
                "src_country",
                "country_code",
                "dest_country",
                "item_nr",
                "partner_sku",
                "sku",
                "status",
                "offer_price",
                "gmv_lcy",
                "currency_code",
                "brand_code",
                "family",
                "fulfillment_model",
                "order_timestamp",
                "shipment_timestamp",
                "delivered_timestamp"
        );
    }
}

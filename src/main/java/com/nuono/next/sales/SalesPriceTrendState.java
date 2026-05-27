package com.nuono.next.sales;

public class SalesPriceTrendState {

    public static final String READY = "ready";
    public static final String NO_ORDER_PRICE_FACTS = "no_order_price_facts";
    public static final String MIXED_CURRENCY = "mixed_currency";
    public static final String INVALID_ORDER_PRICE_FACTS = "invalid_order_price_facts";

    private final String state;
    private final String label;
    private final String message;

    public SalesPriceTrendState(String state, String label, String message) {
        this.state = state;
        this.label = label;
        this.message = message;
    }

    public static SalesPriceTrendState ready() {
        return new SalesPriceTrendState(READY, "订单价格已接入", "当前范围已使用真实订单行生成价格趋势。");
    }

    public static SalesPriceTrendState noOrderPriceFacts() {
        return new SalesPriceTrendState(
                NO_ORDER_PRICE_FACTS,
                "暂无订单价格",
                "当前范围没有可用于价格趋势的真实订单行。"
        );
    }

    public static SalesPriceTrendState mixedCurrency() {
        return new SalesPriceTrendState(
                MIXED_CURRENCY,
                "价格币种不一致",
                "当前范围订单价格存在多个币种，暂不合并展示价格趋势。"
        );
    }

    public static SalesPriceTrendState invalidOrderPriceFacts() {
        return new SalesPriceTrendState(
                INVALID_ORDER_PRICE_FACTS,
                "订单价格不可用",
                "当前范围订单行存在但缺少有效价格、时间、状态或币种。"
        );
    }

    public String getState() {
        return state;
    }

    public String getLabel() {
        return label;
    }

    public String getMessage() {
        return message;
    }
}

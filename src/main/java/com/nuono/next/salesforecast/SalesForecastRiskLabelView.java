package com.nuono.next.salesforecast;

public class SalesForecastRiskLabelView {

    private final String code;
    private final String label;
    private final String severity;
    private final String explanation;

    public SalesForecastRiskLabelView(String code, String label, String severity, String explanation) {
        this.code = code;
        this.label = label;
        this.severity = severity;
        this.explanation = explanation;
    }

    public static SalesForecastRiskLabelView fromCode(String code) {
        if ("possible_stockout_distortion".equals(code)) {
            return new SalesForecastRiskLabelView(code, "断货失真", "warning", "当前库存为 0 且近 30 天仍有销量，近期销量可能被断货压低。");
        }
        if ("replenishment_risk".equals(code)) {
            return new SalesForecastRiskLabelView(code, "补货风险", "danger", "库存覆盖天数偏低，需要优先复核补货。");
        }
        if ("overstock_risk".equals(code)) {
            return new SalesForecastRiskLabelView(code, "积压风险", "warning", "库存覆盖较深且近期销量偏低，需要复核库存消化节奏。");
        }
        if ("stale_sales_data".equals(code)) {
            return new SalesForecastRiskLabelView(code, "销量数据过期", "warning", "最新销量事实早于预期新鲜度。");
        }
        if ("missing_stock_data".equals(code)) {
            return new SalesForecastRiskLabelView(code, "缺库存", "warning", "当前商品未匹配到库存投影。");
        }
        if ("no_sales_training_data".equals(code)) {
            return new SalesForecastRiskLabelView(code, "无训练数据", "warning", "当前在架商品没有自身销量训练样本，预测只能依赖同类目兜底或按 0 处理，需人工复核。");
        }
        if ("insufficient_history_window".equals(code)) {
            return new SalesForecastRiskLabelView(code, "历史不足", "warning", "可用自身销量样本少于 30 天，预测需人工复核。");
        }
        if ("low_history_volume".equals(code)) {
            return new SalesForecastRiskLabelView(code, "低销量样本", "warning", "近 30 天销量不超过 10 件，少量波动会明显影响预测结果。");
        }
        if ("partial_history_window".equals(code)) {
            return new SalesForecastRiskLabelView(code, "样本窗口不完整", "info", "可用自身销量样本少于 60 天，60 天平滑窗口尚未完整。");
        }
        if ("low_confidence".equals(code)) {
            return new SalesForecastRiskLabelView(code, "低置信度", "warning", "样本不足或存在数据质量问题，预测需人工复核。");
        }
        return new SalesForecastRiskLabelView(code, code, "info", code);
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getSeverity() {
        return severity;
    }

    public String getExplanation() {
        return explanation;
    }
}

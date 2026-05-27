package com.nuono.next.operationsconfig;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OperationConfigDefaultVersionCatalog {
    public static final String DEFAULT_CALENDAR_VERSION_NO = "DEFAULT_CALENDAR_CONFIG";
    public static final String DEFAULT_LIFECYCLE_VERSION_NO = "DEFAULT_LIFECYCLE_CONFIG";
    private static final LocalDateTime DEFAULT_UPDATED_AT = LocalDateTime.of(2026, 5, 25, 0, 0);

    public List<OperationConfigVersionRowView> listRows() {
        return listRows(false);
    }

    public OperationConfigVersionDetailView getDetail(String versionNo) {
        return getDetail(versionNo, false);
    }

    public List<OperationConfigVersionRowView> listRows(boolean editableBySystemAdmin) {
        return List.of(calendarRow(editableBySystemAdmin), lifecycleRow(editableBySystemAdmin));
    }

    public OperationConfigVersionDetailView getDetail(String versionNo, boolean editableBySystemAdmin) {
        String normalized = versionNo == null ? "" : versionNo.trim().toUpperCase(Locale.ROOT);
        if (DEFAULT_CALENDAR_VERSION_NO.equals(normalized)) {
            OperationConfigVersionRowView row = calendarRow(editableBySystemAdmin);
            return toDetail(row, calendarItems());
        }
        if (DEFAULT_LIFECYCLE_VERSION_NO.equals(normalized)) {
            OperationConfigVersionRowView row = lifecycleRow(editableBySystemAdmin);
            return toDetail(row, lifecycleItems());
        }
        throw new IllegalArgumentException("operation config version not found");
    }

    private OperationConfigVersionRowView calendarRow(boolean editableBySystemAdmin) {
        return new OperationConfigVersionRowView(
                DEFAULT_CALENDAR_VERSION_NO,
                "默认日历配置",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                OperationConfigVersionType.BUSINESS_CALENDAR.getLabel(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                "13 条默认配置",
                13,
                "全局默认",
                null,
                DEFAULT_UPDATED_AT,
                systemDefaultActions(editableBySystemAdmin)
        );
    }

    private OperationConfigVersionRowView lifecycleRow(boolean editableBySystemAdmin) {
        return new OperationConfigVersionRowView(
                DEFAULT_LIFECYCLE_VERSION_NO,
                "默认生命周期配置",
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                OperationConfigVersionType.PRODUCT_LIFECYCLE.getLabel(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                "14 条 DEFAULT_V1 配置",
                14,
                "全局默认",
                null,
                DEFAULT_UPDATED_AT,
                systemDefaultActions(editableBySystemAdmin)
        );
    }

    private OperationConfigVersionDetailView toDetail(
            OperationConfigVersionRowView row,
            List<OperationConfigDefaultVersionItemView> items
    ) {
        return new OperationConfigVersionDetailView(
                row.getVersionNo(),
                row.getDisplayName(),
                row.getConfigType(),
                row.getConfigTypeLabel(),
                row.getStatus(),
                row.getStatusLabel(),
                row.getSourceLabel(),
                row.getSummary(),
                row.getItemCount(),
                row.getScopeSummary(),
                row.getUpdatedBy(),
                row.getUpdatedAt(),
                row.getActions(),
                items
        );
    }

    private List<OperationConfigDefaultVersionItemView> calendarItems() {
        return List.of(
                defaultItem("业务日历", "斋月 (Ramadan)", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "开斋节 (Eid al-Fitr)", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "古尔邦节 (Eid al-Adha)", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "白色星期五", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "黄色星期五", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "双十一 (11.11)", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "开学季模式", "提前一年", "日期范围", null, null, null),
                defaultItem("业务日历", "夏季模式", "提前一年", "日期范围", null, null, null),
                defaultItem("历史数据推算", "节日爆发系数", "每周1", null, null, "类目/系数", null),
                defaultItem("历史数据推算", "月度薪酬爆发系数", "每月5日", null, null, "类目/系数/日期", null),
                defaultItem("历史数据推算", "流行产品衰退系数", "每3天", null, null, "关键词/系数", null),
                defaultItem("上架选择", "流行产品关键词", "上架选择", "字符串或选择", null, null, null),
                defaultItem("上架选择", "季节产品", "上架选择", "选择 夏季/雨季", null, null, null)
        );
    }

    private List<OperationConfigDefaultVersionItemView> lifecycleItems() {
        OperationLifecycleRuleThresholds thresholds = OperationLifecycleRuleThresholds.defaultV1();
        return List.of(
                defaultItem("新品期", "新品期最长周期", "随时", "数值", formatInteger(thresholds.getNewMaxAgeDays()), null, null),
                defaultItem("新品期", "新品期最小周期", "随时", "数值", formatInteger(thresholds.getNewMinAgeDays()), null, null),
                defaultItem("新品期", "高客单价阈值", "随时", "数值", formatDecimal(thresholds.getHighPriceThreshold()), null, "高客单价可能生命周期不明显"),
                defaultItem("成长期", "成长期最小销量环比增长率", "随时", "数值", formatDecimal(thresholds.getGrowthMinSalesGrowthRate()), null, null),
                defaultItem("成长期", "成长期最小浏览环比增长率", "随时", "数值", formatDecimal(thresholds.getGrowthMinPvGrowthRate()), null, null),
                defaultItem("成长期", "成长期最小月销量", "随时", "数值", formatDecimal(thresholds.getGrowthMinMonthlySales()), null, null),
                defaultItem("成长期", "成长期最小月动销天数", "随时", "数值", formatInteger(thresholds.getGrowthMinActiveSalesDays()), null, "这个计算月有销量的天数"),
                defaultItem("成长期", "成长期最大波动率", "随时", "数值", formatDecimal(thresholds.getGrowthMaxVolatility()), null, null),
                defaultItem("稳定期", "稳定期最小浏览环比增长率", "随时", "数值", formatDecimal(thresholds.getStableMinPvGrowthRate()), null, null),
                defaultItem("稳定期", "稳定期波动率范围", "随时", "数组", "[" + formatDecimal(thresholds.getStableVolatilityMin()) + ", " + formatDecimal(thresholds.getStableVolatilityMax()) + "]", null, null),
                defaultItem("衰退期", "衰退期最大波动率", "随时", "数值", formatDecimal(thresholds.getDeclineMaxVolatility()), null, null),
                defaultItem("衰退期", "衰退最小销量环比增长率", "随时", "数值", formatDecimal(thresholds.getDeclineMaxSalesGrowthRate()), null, null),
                defaultItem("长尾期", "长尾期最大波动率", "随时", "数值", formatDecimal(thresholds.getLongTailMaxVolatility()), null, null),
                defaultItem("长尾期", "长尾期最大月销红量", "随时", "数值", formatDecimal(thresholds.getLongTailMaxMonthlySales()), null, null)
        );
    }

    private OperationConfigDefaultVersionItemView defaultItem(
            String groupName,
            String itemName,
            String cadence,
            String valueType,
            String defaultValue,
            String resultShape,
            String note
    ) {
        return new OperationConfigDefaultVersionItemView(
                groupName,
                itemName,
                cadence,
                valueType,
                defaultValue,
                resultShape,
                note
        );
    }

    private String formatInteger(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private List<OperationConfigVersionActionView> systemDefaultActions(boolean editableBySystemAdmin) {
        return List.of(
                new OperationConfigVersionActionView(
                        "EDIT",
                        "编辑",
                        editableBySystemAdmin,
                        editableBySystemAdmin ? null : "系统默认版本仅系统管理员可编辑"
                ),
                new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                new OperationConfigVersionActionView("DELETE", "删除", false, "系统默认版本不可删除"),
                new OperationConfigVersionActionView("PUBLISH", "发布", false, "系统默认版本不可发布"),
                new OperationConfigVersionActionView("DISABLE", "停用", false, "系统默认版本不可停用")
        );
    }
}

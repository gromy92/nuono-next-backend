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
    public static final String DEFAULT_REPLENISHMENT_PLAN_VERSION_NO = "DEFAULT_REPLENISHMENT_PLAN_BASIC_V1";
    private static final LocalDateTime DEFAULT_UPDATED_AT = LocalDateTime.of(2026, 5, 25, 0, 0);

    public List<OperationConfigVersionRowView> listRows() {
        return listRows(false);
    }

    public OperationConfigVersionDetailView getDetail(String versionNo) {
        return getDetail(versionNo, false);
    }

    public List<OperationConfigVersionRowView> listRows(boolean editableBySystemAdmin) {
        return List.of(
                calendarRow(editableBySystemAdmin),
                lifecycleRow(editableBySystemAdmin),
                replenishmentPlanRow(editableBySystemAdmin)
        );
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
        if (DEFAULT_REPLENISHMENT_PLAN_VERSION_NO.equals(normalized)) {
            OperationConfigVersionRowView row = replenishmentPlanRow(editableBySystemAdmin);
            return toDetail(row, replenishmentPlanItems());
        }
        throw new IllegalArgumentException("operation config version not found");
    }

    private OperationConfigVersionRowView calendarRow(boolean editableBySystemAdmin) {
        int itemCount = calendarItems().size();
        return new OperationConfigVersionRowView(
                DEFAULT_CALENDAR_VERSION_NO,
                "默认日历配置",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                OperationConfigVersionType.BUSINESS_CALENDAR.getLabel(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                itemCount + " 条日历配置",
                itemCount,
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

    private OperationConfigVersionRowView replenishmentPlanRow(boolean editableBySystemAdmin) {
        int itemCount = replenishmentPlanItems().size();
        return new OperationConfigVersionRowView(
                DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                "默认补货计划参数",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                OperationConfigVersionType.REPLENISHMENT_PLAN.getLabel(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                "空运12/15，海运70/30，库存来源 FBN",
                itemCount,
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
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "0.85", "all_products", "全品兜底：Ramadan 销量抑制"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.10", "all_products", "全品兜底：开斋节保守提升"),
                calendarRule("古尔邦节 (Eid al-Adha)", "2026-05-25 ~ 2026-05-31", "1.08", "all_products", "近一年销量暂未覆盖可比窗口，先用保守值"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "1.10", "all_products", "Noon 口径促销兜底，白色星期五不重复发布"),
                calendarRule("双十一 (11.11)", "2026-11-10 ~ 2026-11-12", "1.15", "all_products", "双十一全品兜底"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.03", "all_products", "开学季全品兜底"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.90", "all_products", "夏季全品淡季兜底"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "1.20", "category:stationery-envelopes_mailers_shipping_supplies", "Ramadan 高置信类目：信封邮寄用品提升"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "1.15", "category:kitchen_dining-dinnerware_serveware", "Ramadan 高置信类目：餐厨服务用品提升"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "1.15", "category:toys-pretend_play", "Ramadan 高置信类目：角色扮演玩具提升"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "1.10", "category:colour_cosmetics-make_up_tools_accessories", "Ramadan 高置信类目：美妆工具不套用全品压低"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "1.05", "category:home_decor-lighting", "Ramadan 高置信类目：家居照明稳中上行"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "0.70", "category:electronic_accessories-headphones", "Ramadan 高置信类目：耳机明显弱化"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "0.75", "category:stationery-writing_correction_supplies", "Ramadan 高置信类目：书写纠正文具下降"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "0.75", "category:sports_outdoor-sports_protective_gear", "Ramadan 高置信类目：运动护具下降"),
                calendarRule("斋月 (Ramadan)", "2026-02-18 ~ 2026-03-18", "0.75", "category:stationery-stationery", "Ramadan 高置信类目：普通文具下降"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.35", "category:hair_personal_care-hair_care", "开斋节高置信类目：头发护理强提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.35", "category:colour_cosmetics-nails", "开斋节高置信类目：美甲强提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.35", "category:colour_cosmetics-make_up_tools_accessories", "开斋节高置信类目：美妆工具强提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.35", "category:baby_product-feeding_training_accessories", "开斋节高置信类目：婴儿喂养训练提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.35", "category:stationery-gift_wrapping_supplies", "开斋节高置信类目：礼品包装提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.30", "category:toys-arts_crafts", "开斋节高置信类目：手工玩具提升"),
                calendarRule("开斋节 (Eid al-Fitr)", "2026-03-19 ~ 2026-03-22", "1.25", "category:electronic_accessories-phone_accessories", "开斋节高置信类目：手机配件提升"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "1.25", "category:automotive-interior_accessories", "黄色星期五高置信类目：汽车内饰促销敏感"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "1.20", "category:baby_product-hygiene_product", "黄色星期五高置信类目：婴儿卫生用品提升"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "0.90", "category:stationery-gift_wrapping_supplies", "黄色星期五高置信类目：礼品包装不跟随提升"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "0.95", "category:health_nutrition-medical_supplies_equipment", "黄色星期五高置信类目：医疗用品偏弱"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "0.95", "category:home_improvement-electrical_solar", "黄色星期五高置信类目：电气太阳能偏弱"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "1.00", "category:electronic_accessories-phone_accessories", "黄色星期五高置信类目：手机配件基本不提升"),
                calendarRule("黄色星期五", "2026-11-20 ~ 2026-11-30", "1.00", "category:colour_cosmetics-make_up_tools_accessories", "黄色星期五高置信类目：美妆工具基本不提升"),
                calendarRule("双十一 (11.11)", "2026-11-10 ~ 2026-11-12", "1.00", "category:baby_product-feeding_training_accessories", "双十一高置信类目：婴儿喂养训练不明显提升"),
                calendarRule("双十一 (11.11)", "2026-11-10 ~ 2026-11-12", "1.05", "category:colour_cosmetics-make_up_tools_accessories", "双十一高置信类目：美妆工具低于全品默认"),
                calendarRule("双十一 (11.11)", "2026-11-10 ~ 2026-11-12", "1.05", "category:colour_cosmetics-nails", "双十一高置信类目：美甲低于全品默认"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.35", "category:stationery-stationery", "开学季高置信类目：普通文具核心提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.30", "category:bags_luggage-other_bags", "开学季高置信类目：包袋提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.25", "category:stationery-desk_accessories_workspace_organizers", "开学季高置信类目：桌面收纳提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.20", "category:electronic_accessories-accessories", "开学季高置信类目：电子配件提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.20", "category:stationery-office_electronics", "开学季高置信类目：办公电子提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "1.15", "category:stationery-writing_correction_supplies", "开学季高置信类目：书写纠正文具提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "0.85", "category:colour_cosmetics-nails", "开学季高置信类目：美甲不套用开学季提升"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "0.90", "category:baby_product-feeding_training_accessories", "开学季高置信类目：婴儿喂养下降"),
                calendarRule("开学季模式", "2026-08-17 ~ 2026-09-07", "0.90", "category:electronic_accessories-phone_accessories", "开学季高置信类目：手机配件下降"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.25", "category:electronic_accessories-accessories", "夏季高置信类目：电子配件上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.25", "category:video_games-accessories", "夏季高置信类目：游戏配件上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.15", "category:home_appliances-small_appliances", "夏季高置信类目：小家电上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.10", "category:toys-arts_crafts", "夏季高置信类目：手工玩具上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.10", "category:sports_outdoor-accessories", "夏季高置信类目：户外运动配件上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.10", "category:baby_product-baby_safety_equipment", "夏季高置信类目：婴儿安全设备上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "1.10", "category:baby_product-baby_transport", "夏季高置信类目：婴儿出行上行"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.75", "category:stationery-office_electronics", "夏季高置信类目：办公电子弱化"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.75", "category:toys-learning_education", "夏季高置信类目：教育玩具弱化"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.75", "category:home_improvement-laundry_care", "夏季高置信类目：洗护家清弱化"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.80", "category:toys-novelty_toys", "夏季高置信类目：新奇玩具弱化"),
                calendarRule("夏季模式", "2026-06-01 ~ 2026-08-31", "0.80", "category:stationery-desk_accessories_workspace_organizers", "夏季高置信类目：桌面收纳弱化")
        );
    }

    private OperationConfigDefaultVersionItemView calendarRule(
            String itemName,
            String dateRange,
            String factor,
            String targetScope,
            String note
    ) {
        return defaultItem(
                "业务日历",
                itemName,
                null,
                "日期范围/系数",
                dateRange + " / " + factor,
                targetScope,
                note
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

    private List<OperationConfigDefaultVersionItemView> replenishmentPlanItems() {
        return List.of(
                defaultItem("运输时效", "空运运输天数", "随时", "数值", "12", null, "空运 ETA 提前量"),
                defaultItem("覆盖窗口", "空运覆盖天数", "随时", "数值", "15", null, "空运只覆盖短期应急缺口"),
                defaultItem("运输时效", "海运运输天数", "随时", "数值", "70", null, "海运 ETA 提前量"),
                defaultItem("覆盖窗口", "海运覆盖天数", "随时", "数值", "30", null, "海运到货后常规补货窗口"),
                defaultItem("库存口径", "库存来源", "随时", "数组", "FBN", null, "基础版只纳入 FBN"),
                defaultItem("在途口径", "在途必须有 ETA", "随时", "布尔", "true", null, "无 ETA 在途不参与可用库存覆盖"),
                defaultItem("空运策略", "空运只应急", "随时", "布尔", "true", null, "海运到货前断货才建议空运"),
                defaultItem("建议数量", "建议数量取整", "随时", "枚举", "ceil", null, "建议数量向上取整"),
                defaultItem("预测窗口", "预测窗口天数", "随时", "数值", "100", null, "基础版至少覆盖海运 70+30 天"),
                defaultItem("预测新鲜度", "预测陈旧提醒天数", "随时", "数值", "2", null, "超过阈值提示预测事实偏旧"),
                defaultItem("预测新鲜度", "预测陈旧阻断天数", "随时", "数值", "7", null, "超过阈值停止生成补货建议")
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

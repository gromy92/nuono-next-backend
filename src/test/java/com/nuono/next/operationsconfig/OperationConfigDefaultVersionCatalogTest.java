package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationConfigDefaultVersionCatalogTest {

    @Test
    void listsCalendarLifecycleAndReplenishmentSystemDefaultsAsTypedRows() {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();

        List<OperationConfigVersionRowView> rows = catalog.listRows();

        assertEquals(3, rows.size());
        OperationConfigVersionRowView calendar = rows.get(0);
        assertEquals("DEFAULT_CALENDAR_CONFIG", calendar.getVersionNo());
        assertEquals("默认日历配置", calendar.getDisplayName());
        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), calendar.getConfigType());
        assertEquals("日历版本", calendar.getConfigTypeLabel());
        assertEquals("SYSTEM_DEFAULT", calendar.getStatus());
        assertEquals(54, calendar.getItemCount());
        assertEquals("54 条日历配置", calendar.getSummary());
        assertTrue(calendar.getActions().stream().anyMatch(action ->
                "DETAIL".equals(action.getAction()) && action.isEnabled()
        ));
        assertTrue(calendar.getActions().stream().anyMatch(action ->
                "COPY".equals(action.getAction()) && action.isEnabled()
        ));
        assertTrue(calendar.getActions().stream().anyMatch(action ->
                "DELETE".equals(action.getAction()) && !action.isEnabled()
        ));

        OperationConfigVersionRowView lifecycle = rows.get(1);
        assertEquals("DEFAULT_LIFECYCLE_CONFIG", lifecycle.getVersionNo());
        assertEquals("默认生命周期配置", lifecycle.getDisplayName());
        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), lifecycle.getConfigType());
        assertEquals("生命周期版本", lifecycle.getConfigTypeLabel());
        assertEquals("SYSTEM_DEFAULT", lifecycle.getStatus());
        assertEquals(14, lifecycle.getItemCount());

        OperationConfigVersionRowView replenishment = rows.get(2);
        assertEquals("DEFAULT_REPLENISHMENT_PLAN_BASIC_V1", replenishment.getVersionNo());
        assertEquals("默认补货计划参数", replenishment.getDisplayName());
        assertEquals(OperationConfigVersionType.REPLENISHMENT_PLAN.name(), replenishment.getConfigType());
        assertEquals("补货计划参数", replenishment.getConfigTypeLabel());
        assertEquals("SYSTEM_DEFAULT", replenishment.getStatus());
        assertEquals("系统默认", replenishment.getStatusLabel());
        assertEquals("系统默认", replenishment.getSourceLabel());
        assertTrue(replenishment.getSummary().contains("空运12/15"));
        assertTrue(replenishment.getSummary().contains("海运70/30"));
        assertEquals("全局默认", replenishment.getScopeSummary());
        assertTrue(replenishment.getActions().stream().anyMatch(action ->
                "DETAIL".equals(action.getAction()) && action.isEnabled()
        ));
        assertTrue(replenishment.getActions().stream().anyMatch(action ->
                "COPY".equals(action.getAction()) && action.isEnabled()
        ));
        assertTrue(replenishment.getActions().stream().anyMatch(action ->
                "DELETE".equals(action.getAction()) && !action.isEnabled()
        ));
    }

    @Test
    void returnsDefaultCalendarAndLifecycleDetailsFromUploadedBaseline() {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();

        OperationConfigVersionDetailView calendar = catalog.getDetail("DEFAULT_CALENDAR_CONFIG");
        OperationConfigVersionDetailView lifecycle = catalog.getDetail("DEFAULT_LIFECYCLE_CONFIG");

        assertEquals("DEFAULT_CALENDAR_CONFIG", calendar.getVersionNo());
        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), calendar.getConfigType());
        assertEquals(54, calendar.getItems().size());
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "斋月 (Ramadan)".equals(item.getItemName())
                        && "2026-02-18 ~ 2026-03-18 / 0.85".equals(item.getDefaultValue())
                        && "all_products".equals(item.getResultShape())
        ));
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "开学季模式".equals(item.getItemName())
                        && "2026-08-17 ~ 2026-09-07 / 1.35".equals(item.getDefaultValue())
                        && "category:stationery-stationery".equals(item.getResultShape())
        ));
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "夏季模式".equals(item.getItemName())
                        && "2026-06-01 ~ 2026-08-31 / 1.25".equals(item.getDefaultValue())
                        && "category:electronic_accessories-accessories".equals(item.getResultShape())
        ));
        assertFalse(calendar.getItems().stream().anyMatch(item -> "白色星期五".equals(item.getItemName())));
        assertFalse(calendar.getItems().stream().anyMatch(item -> "月度薪酬爆发系数".equals(item.getItemName())));

        assertEquals("DEFAULT_LIFECYCLE_CONFIG", lifecycle.getVersionNo());
        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), lifecycle.getConfigType());
        assertEquals(14, lifecycle.getItems().size());
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "稳定期波动率范围".equals(item.getItemName()) && "[0.3, 0.5]".equals(item.getDefaultValue())
        ));
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "长尾期最大月销红量".equals(item.getItemName())
        ));
    }

    @Test
    void returnsDefaultReplenishmentPlanDetails() {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();

        OperationConfigVersionDetailView detail = catalog.getDetail("DEFAULT_REPLENISHMENT_PLAN_BASIC_V1");

        assertEquals("DEFAULT_REPLENISHMENT_PLAN_BASIC_V1", detail.getVersionNo());
        assertEquals(OperationConfigVersionType.REPLENISHMENT_PLAN.name(), detail.getConfigType());
        assertEquals("补货计划参数", detail.getConfigTypeLabel());
        assertEquals("默认补货计划参数", detail.getDisplayName());
        assertTrue(detail.getSummary().contains("空运12/15"));
        assertTrue(detail.getSummary().contains("海运70/30"));
        assertEquals("全局默认", detail.getScopeSummary());
        assertEquals("12", defaultValue(detail, "空运运输天数"));
        assertEquals("15", defaultValue(detail, "空运覆盖天数"));
        assertEquals("70", defaultValue(detail, "海运运输天数"));
        assertEquals("30", defaultValue(detail, "海运覆盖天数"));
        assertEquals("FBN,SUPERMALL", defaultValue(detail, "库存来源"));
        assertEquals("true", defaultValue(detail, "在途必须有 ETA"));
        assertEquals("true", defaultValue(detail, "空运只应急"));
        assertEquals("ceil", defaultValue(detail, "建议数量取整"));
        assertEquals("100", defaultValue(detail, "预测窗口天数"));
    }

    private static String defaultValue(OperationConfigVersionDetailView detail, String itemName) {
        return detail.getItems().stream()
                .filter(item -> itemName.equals(item.getItemName()))
                .findFirst()
                .orElseThrow()
                .getDefaultValue();
    }
}

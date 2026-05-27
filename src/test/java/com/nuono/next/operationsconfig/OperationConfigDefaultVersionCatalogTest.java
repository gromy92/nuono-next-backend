package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationConfigDefaultVersionCatalogTest {

    @Test
    void listsCalendarAndLifecycleSystemDefaultsAsTypedRows() {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();

        List<OperationConfigVersionRowView> rows = catalog.listRows();

        assertEquals(2, rows.size());
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
}

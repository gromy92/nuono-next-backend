package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationConfigVersionLibraryServiceTest {

    @Test
    void copyDefaultCalendarVersionCreatesSameTypeDraftWithContentSnapshot() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), draft.getConfigType());
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(draft.getVersionNo().startsWith("CALENDAR_CONFIG_"));
        assertEquals("默认日历配置 副本", draft.getDisplayName());
        assertEquals(3, service.listVersions(adminContext()).size());

        OperationConfigVersionDetailView copiedDetail = service.getDetail(adminContext(), draft.getVersionNo());
        assertEquals(54, copiedDetail.getItems().size());
        assertTrue(copiedDetail.getItems().stream().anyMatch(item -> "斋月 (Ramadan)".equals(item.getItemName())));
        assertTrue(copiedDetail.getItems().stream().anyMatch(item ->
                "category:stationery-stationery".equals(item.getResultShape())
        ));
        assertTrue(copiedDetail.getActions().stream().anyMatch(action ->
                "EDIT".equals(action.getAction()) && action.isEnabled()
        ));
        assertTrue(copiedDetail.getAuditTrail().stream().anyMatch(audit -> "COPY".equals(audit.getOperation())));
    }

    @Test
    void copyDefaultLifecycleVersionCreatesSameTypeDraftWithContentSnapshot() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_LIFECYCLE_CONFIG");

        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), draft.getConfigType());
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(draft.getVersionNo().startsWith("LIFECYCLE_CONFIG_"));
        assertEquals("默认生命周期配置 副本", draft.getDisplayName());

        OperationConfigVersionDetailView copiedDetail = service.getDetail(adminContext(), draft.getVersionNo());
        assertEquals(14, copiedDetail.getItems().size());
        assertTrue(copiedDetail.getItems().stream().anyMatch(item ->
                "稳定期波动率范围".equals(item.getItemName()) && "[0.3, 0.5]".equals(item.getDefaultValue())
        ));
    }

    @Test
    void copyCustomVersionCreatesNewDraftWithSameTypeAndSnapshot() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(new OperationConfigTypedVersion(
                88010L,
                "CALENDAR_CONFIG_88010",
                "旺季日历配置",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "PUBLISHED",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条自定义配置",
                1,
                "3 个店铺",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"自定义节日系数\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":\"自定义来源\"}]",
                1L,
                2L,
                now,
                now
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "CALENDAR_CONFIG_88010");

        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), draft.getConfigType());
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(draft.getVersionNo().startsWith("CALENDAR_CONFIG_"));
        assertTrue(!"CALENDAR_CONFIG_88010".equals(draft.getVersionNo()));
        assertEquals("旺季日历配置 副本", draft.getDisplayName());

        OperationConfigVersionDetailView copiedDetail = service.getDetail(adminContext(), draft.getVersionNo());
        assertEquals(1, copiedDetail.getItems().size());
        assertEquals("自定义节日系数", copiedDetail.getItems().get(0).getItemName());
        assertEquals("1.20", copiedDetail.getItems().get(0).getDefaultValue());
    }

    @Test
    void calendarDraftUpdatePersistsContentNameAndSummary() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        OperationConfigVersionDetailView updated = service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "2027 斋月日历配置",
                        "斋月类目重点配置",
                        List.of(
                                new OperationConfigVersionUpdateRequest.Item(
                                        "业务日历",
                                        "斋月 2027",
                                        "提前一年",
                                        "日期范围",
                                        "2027-02-08 ~ 2027-03-09",
                                        null,
                                        "验收修改"
                                ),
                                new OperationConfigVersionUpdateRequest.Item(
                                        "历史数据推算",
                                        "自定义活动因子",
                                        "每周1",
                                        "数值",
                                        "1.15",
                                        "category:home-bedding",
                                        null
                                )
                        )
                )
        );

        assertEquals("2027 斋月日历配置", updated.getDisplayName());
        assertEquals("斋月类目重点配置", updated.getSummary());
        assertEquals(2, updated.getItems().size());
        assertEquals("斋月 2027", updated.getItems().get(0).getItemName());
        assertEquals("2027-02-08 ~ 2027-03-09", updated.getItems().get(0).getDefaultValue());

        OperationConfigVersionRowView row = service.listVersions(adminContext()).stream()
                .filter(item -> draft.getVersionNo().equals(item.getVersionNo()))
                .findFirst()
                .orElseThrow();
        assertEquals("2027 斋月日历配置", row.getDisplayName());
        assertEquals("斋月类目重点配置", row.getSummary());
        assertEquals(2, row.getItemCount());
    }

    @Test
    void calendarDraftUpdatePersistsTargetScopeAndRejectsMissingScopeValue() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        OperationConfigVersionDetailView updated = service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(
                                new OperationConfigVersionUpdateRequest.Item(
                                        "业务日历",
                                        "开学季模式",
                                        null,
                                        "日期范围",
                                        "2026-08-01 ~ 2026-08-31 / 1.03",
                                        "category:home-bedding",
                                        null
                                ),
                                new OperationConfigVersionUpdateRequest.Item(
                                        "业务日历",
                                        "黄色星期五",
                                        null,
                                        "日期范围",
                                        "2026-11-20 ~ 2026-11-30 / 1.10",
                                        "all_products",
                                        null
                                )
                        )
                )
        );

        assertEquals("category:home-bedding", updated.getItems().get(0).getResultShape());
        assertEquals("all_products", updated.getItems().get(1).getResultShape());

        assertThrows(IllegalArgumentException.class, () -> service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "业务日历",
                                "缺少类目范围值",
                                null,
                                "日期范围",
                                "2026-08-01 ~ 2026-08-31 / 1.03",
                                "category",
                                null
                        ))
                )
        ));
    }

    @Test
    void calendarUpdateAcceptsSiteScopedFamilyAndSiteFallbackFactors() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        OperationConfigVersionDetailView updated = service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "站点类目因子",
                        "高置信站点类目因子 + 站点全品兜底",
                        List.of(
                                new OperationConfigVersionUpdateRequest.Item(
                                        "业务日历",
                                        "黄色星期五 / SA / paper",
                                        null,
                                        "日期范围/系数",
                                        "2026-11-20 ~ 2026-11-30 / 1.2500",
                                        "site:SA|family:paper",
                                        "高置信类目因子"
                                ),
                                new OperationConfigVersionUpdateRequest.Item(
                                        "业务日历",
                                        "黄色星期五 / SA / 全品兜底",
                                        null,
                                        "日期范围/系数",
                                        "2026-11-20 ~ 2026-11-30 / 1.1000",
                                        "site:SA",
                                        "低置信站点全品兜底"
                                )
                        )
                )
        );

        assertEquals(2, updated.getItems().size());
        assertEquals("site:SA|family:paper", updated.getItems().get(0).getResultShape());
        assertEquals("site:SA", updated.getItems().get(1).getResultShape());
    }

    @Test
    void calendarUpdateRejectsLifecycleContentForCalendarDraft() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        assertThrows(IllegalArgumentException.class, () -> service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "稳定期",
                                "稳定期波动率范围",
                                "随时",
                                "数组",
                                "[0.3, 0.5]",
                                null,
                                null
                        ))
                )
        ));
    }

    @Test
    void calendarUpdateRejectsNonDraftCalendarVersion() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(new OperationConfigTypedVersion(
                88010L,
                "CALENDAR_CONFIG_88010",
                "已发布日历配置",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "PUBLISHED",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "3 个店铺",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"自定义节日系数\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                now,
                now
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        assertThrows(IllegalStateException.class, () -> service.updateVersion(
                adminContext(),
                "CALENDAR_CONFIG_88010",
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "业务日历",
                                "修改已发布版本",
                                "随时",
                                "日期范围",
                                "1.20",
                                null,
                                null
                        ))
                )
        ));
    }

    @Test
    void lifecycleDraftUpdatePersistsContentAndSummary() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_LIFECYCLE_CONFIG");

        OperationConfigVersionDetailView updated = service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "稳定期",
                                "稳定期波动率范围",
                                "随时",
                                "数组",
                                "[0.25, 0.45]",
                                null,
                                "验收修改"
                        ))
                )
        );

        assertEquals("1 条生命周期配置", updated.getSummary());
        assertEquals(1, updated.getItems().size());
        assertEquals("稳定期波动率范围", updated.getItems().get(0).getItemName());
        assertEquals("[0.25, 0.45]", updated.getItems().get(0).getDefaultValue());

        OperationConfigVersionRowView row = service.listVersions(adminContext()).stream()
                .filter(item -> draft.getVersionNo().equals(item.getVersionNo()))
                .findFirst()
                .orElseThrow();
        assertEquals("1 条生命周期配置", row.getSummary());
        assertEquals(1, row.getItemCount());
    }

    @Test
    void lifecycleUpdateRejectsCalendarContentForLifecycleDraft() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_LIFECYCLE_CONFIG");

        assertThrows(IllegalArgumentException.class, () -> service.updateVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "业务日历",
                                "斋月 2027",
                                "提前一年",
                                "日期范围",
                                "2027-02-08 ~ 2027-03-09",
                                null,
                                null
                        ))
                )
        ));
    }

    @Test
    void lifecycleUpdateRejectsNonDraftLifecycleVersion() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(new OperationConfigTypedVersion(
                88110L,
                "LIFECYCLE_CONFIG_88110",
                "已发布生命周期配置",
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                "PUBLISHED",
                "DEFAULT_LIFECYCLE_CONFIG",
                "运营主管",
                "1 条生命周期配置",
                1,
                "3 个店铺",
                "[{\"groupName\":\"稳定期\",\"itemName\":\"稳定期波动率范围\",\"cadence\":\"随时\",\"valueType\":\"数组\",\"defaultValue\":\"[0.3, 0.5]\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                now,
                now
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        assertThrows(IllegalStateException.class, () -> service.updateVersion(
                adminContext(),
                "LIFECYCLE_CONFIG_88110",
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "稳定期",
                                "稳定期波动率范围",
                                "随时",
                                "数组",
                                "[0.25, 0.45]",
                                null,
                                null
                        ))
                )
        ));
    }

    @Test
    void adminCanEditSystemDefaultCalendarVersionAndListDoesNotDuplicateDefaultRow() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        OperationConfigVersionDetailView updated = service.updateVersion(
                adminContext(),
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO,
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "业务日历",
                                "管理员维护默认日历",
                                "提前一年",
                                "日期范围",
                                "2027-01-01 ~ 2027-01-07",
                                null,
                                "验收修改"
                        ))
                )
        );

        assertEquals("SYSTEM_DEFAULT", updated.getStatus());
        assertEquals("系统默认", updated.getSourceLabel());
        assertEquals("1 条日历配置", updated.getSummary());
        assertEquals("管理员维护默认日历", updated.getItems().get(0).getItemName());
        assertTrue(updated.getAuditTrail().stream().anyMatch(audit -> "EDIT".equals(audit.getOperation())));
        assertTrue(updated.getActions().stream().anyMatch(action ->
                "EDIT".equals(action.getAction()) && action.isEnabled()
        ));

        OperationConfigTypedVersion persisted = repository.findByVersionNo(
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO
        ).orElseThrow();
        assertEquals("SYSTEM_DEFAULT", persisted.getStatus());
        assertEquals("全局默认", persisted.getScopeSummary());

        List<OperationConfigVersionRowView> rows = service.listVersions(adminContext());
        assertEquals(1, rows.stream()
                .filter(row -> OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO.equals(row.getVersionNo()))
                .count());
        OperationConfigVersionRowView defaultRow = rows.stream()
                .filter(row -> OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO.equals(row.getVersionNo()))
                .findFirst()
                .orElseThrow();
        assertTrue(actionEnabled(defaultRow, "EDIT"));

        OperationConfigVersionDetailView detail = service.getDetail(
                adminContext(),
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO
        );
        assertEquals("管理员维护默认日历", detail.getItems().get(0).getItemName());
    }

    @Test
    void nonAdminCannotEditSystemDefaultVersionAndKeepsReadOnlyAction() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        assertThrows(BusinessAccessDeniedException.class, () -> service.updateVersion(
                operatorContext(),
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO,
                new OperationConfigVersionUpdateRequest(
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        List.of(new OperationConfigVersionUpdateRequest.Item(
                                "业务日历",
                                "运营尝试维护默认日历",
                                "提前一年",
                                "日期范围",
                                "2027-01-01 ~ 2027-01-07",
                                null,
                                null
                        ))
                )
        ));
        assertTrue(repository.findByVersionNo(OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO).isEmpty());

        OperationConfigVersionRowView defaultRow = service.listVersions(operatorContext()).stream()
                .filter(row -> OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO.equals(row.getVersionNo()))
                .findFirst()
                .orElseThrow();
        assertFalse(actionEnabled(defaultRow, "EDIT"));
        assertTrue(actionEnabled(defaultRow, "DETAIL"));
    }

    @Test
    void rowActionsAreGovernedByVersionStatus() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(versionWithStatus("CALENDAR_CONFIG_DRAFT", "DRAFT", now));
        repository.insert(versionWithStatus("CALENDAR_CONFIG_PUBLISHED", "PUBLISHED", now));
        repository.insert(versionWithStatus("CALENDAR_CONFIG_CURRENT", "CURRENT", now));
        repository.insert(versionWithStatus("CALENDAR_CONFIG_HISTORICAL", "HISTORICAL", now));
        repository.insert(versionWithStatus("CALENDAR_CONFIG_DISABLED", "DISABLED", now));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        Map<String, OperationConfigVersionRowView> rows = new LinkedHashMap<>();
        for (OperationConfigVersionRowView row : service.listVersions(adminContext())) {
            rows.put(row.getVersionNo(), row);
        }

        assertTrue(actionEnabled(rows.get("DEFAULT_CALENDAR_CONFIG"), "EDIT"));
        assertTrue(actionEnabled(rows.get("DEFAULT_CALENDAR_CONFIG"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("DEFAULT_CALENDAR_CONFIG"), "COPY"));
        assertFalse(actionEnabled(rows.get("DEFAULT_CALENDAR_CONFIG"), "DELETE"));
        assertFalse(actionEnabled(rows.get("DEFAULT_CALENDAR_CONFIG"), "PUBLISH"));

        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DRAFT"), "EDIT"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DRAFT"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DRAFT"), "COPY"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DRAFT"), "DELETE"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DRAFT"), "PUBLISH"));

        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_PUBLISHED"), "EDIT"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_PUBLISHED"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_PUBLISHED"), "COPY"));
        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_PUBLISHED"), "DELETE"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_PUBLISHED"), "DISABLE"));

        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_CURRENT"), "EDIT"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_CURRENT"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_CURRENT"), "COPY"));
        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_CURRENT"), "DELETE"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_CURRENT"), "DISABLE"));

        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_HISTORICAL"), "EDIT"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_HISTORICAL"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_HISTORICAL"), "COPY"));
        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_HISTORICAL"), "DELETE"));

        assertFalse(actionEnabled(rows.get("CALENDAR_CONFIG_DISABLED"), "EDIT"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DISABLED"), "DETAIL"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DISABLED"), "COPY"));
        assertTrue(actionEnabled(rows.get("CALENDAR_CONFIG_DISABLED"), "DELETE"));
    }

    @Test
    void deleteDraftAndDisabledVersionRemovesThemFromListAndMarksDeleted() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(versionWithStatus("CALENDAR_CONFIG_DISABLED", "DISABLED", now));

        service.deleteVersion(adminContext(), draft.getVersionNo());
        service.deleteVersion(adminContext(), "CALENDAR_CONFIG_DISABLED");

        assertFalse(service.listVersions(adminContext()).stream().anyMatch(row -> draft.getVersionNo().equals(row.getVersionNo())));
        assertFalse(service.listVersions(adminContext()).stream().anyMatch(row -> "CALENDAR_CONFIG_DISABLED".equals(row.getVersionNo())));
        OperationConfigVersionDetailView deleted = service.getDetail(adminContext(), draft.getVersionNo());
        assertEquals("DELETED", deleted.getStatus());
        assertTrue(deleted.getAuditTrail().stream().anyMatch(audit -> "DELETE".equals(audit.getOperation())));
        OperationConfigVersionDetailView disabledDeleted = service.getDetail(adminContext(), "CALENDAR_CONFIG_DISABLED");
        assertEquals("DELETED", disabledDeleted.getStatus());
        assertTrue(disabledDeleted.getAuditTrail().stream().anyMatch(audit -> "DELETE".equals(audit.getOperation())));
    }

    @Test
    void deleteRejectsSystemDefaultAndActiveVersions() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(versionWithStatus("CALENDAR_CONFIG_PUBLISHED", "PUBLISHED", now));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        assertThrows(IllegalStateException.class, () -> service.deleteVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG"));
        assertThrows(IllegalStateException.class, () -> service.deleteVersion(adminContext(), "CALENDAR_CONFIG_PUBLISHED"));
        assertTrue(service.listVersions(adminContext()).stream().anyMatch(row -> "CALENDAR_CONFIG_PUBLISHED".equals(row.getVersionNo())));
    }

    @Test
    void publishCalendarDraftSetsCurrentWithoutLifecycleDraft() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        OperationConfigVersionDetailView current = service.publishVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionPublishRequest(null, null, null, "publish calendar")
        );

        assertEquals("CURRENT", current.getStatus());
        assertTrue(current.getAuditTrail().stream().anyMatch(audit -> "PUBLISH".equals(audit.getOperation())));
        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), current.getConfigType());
        assertFalse(current.getActions().stream()
                .filter(action -> "EDIT".equals(action.getAction()))
                .findFirst()
                .orElseThrow()
                .isEnabled());
        assertEquals(draft.getVersionNo(), service.currentVersion(
                adminContext(),
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                307L,
                "STR108065-NAE",
                "AE"
        ).getVersionNo());
    }

    @Test
    void publishLifecycleDraftSetsCurrentWithoutCalendarDraft() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_LIFECYCLE_CONFIG");

        OperationConfigVersionDetailView current = service.publishVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionPublishRequest(null, null, null, "publish lifecycle")
        );

        assertEquals("CURRENT", current.getStatus());
        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), current.getConfigType());
        assertEquals(draft.getVersionNo(), service.currentVersion(
                adminContext(),
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                307L,
                "STR108065-NAE",
                "AE"
        ).getVersionNo());
    }

    @Test
    void publishingSameTypeSameScopeMovesPreviousCurrentToHistoricalAndKeepsOtherTypeCurrent() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView firstCalendar = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");
        service.publishVersion(adminContext(), firstCalendar.getVersionNo(), new OperationConfigVersionPublishRequest(null, null, null, null));
        OperationConfigVersionRowView lifecycle = service.copyVersion(adminContext(), "DEFAULT_LIFECYCLE_CONFIG");
        service.publishVersion(adminContext(), lifecycle.getVersionNo(), new OperationConfigVersionPublishRequest(null, null, null, null));
        OperationConfigVersionRowView secondCalendar = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        service.publishVersion(adminContext(), secondCalendar.getVersionNo(), new OperationConfigVersionPublishRequest(null, null, null, null));

        Map<String, OperationConfigVersionRowView> rows = new LinkedHashMap<>();
        for (OperationConfigVersionRowView row : service.listVersions(adminContext())) {
            rows.put(row.getVersionNo(), row);
        }
        assertEquals("HISTORICAL", rows.get(firstCalendar.getVersionNo()).getStatus());
        assertEquals("CURRENT", rows.get(secondCalendar.getVersionNo()).getStatus());
        assertEquals("CURRENT", rows.get(lifecycle.getVersionNo()).getStatus());
    }

    @Test
    void currentVersionUsesLatestCurrentWhenDuplicateCurrentRowsExist() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime older = LocalDateTime.of(2026, 5, 25, 9, 30);
        LocalDateTime newer = LocalDateTime.of(2026, 5, 25, 10, 30);
        repository.insert(new OperationConfigTypedVersion(
                88010L,
                "CALENDAR_CONFIG_OLD_CURRENT",
                "旧 current",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "307/STR108065-NAE/AE",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"旧\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.10\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                older,
                older
        ));
        repository.insert(new OperationConfigTypedVersion(
                88011L,
                "CALENDAR_CONFIG_NEW_CURRENT",
                "新 current",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "307/STR108065-NAE/AE",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"新\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                newer,
                newer
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        OperationConfigVersionDetailView current = service.currentVersion(
                adminContext(),
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                307L,
                "STR108065-NAE",
                "AE"
        );

        assertEquals("CALENDAR_CONFIG_NEW_CURRENT", current.getVersionNo());
    }

    @Test
    void disableCurrentVersionStopsCurrentResolutionAndKeepsAuditedDetailReadable() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");
        service.publishVersion(adminContext(), draft.getVersionNo(), new OperationConfigVersionPublishRequest(null, null, null, null));

        OperationConfigVersionDetailView disabled = service.disableVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionDisableRequest("验收停用")
        );

        assertEquals("DISABLED", disabled.getStatus());
        assertEquals(draft.getVersionNo(), service.getDetail(adminContext(), draft.getVersionNo()).getVersionNo());
        assertEquals("DEFAULT_CALENDAR_CONFIG", service.currentVersion(
                adminContext(),
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                307L,
                "STR108065-NAE",
                "AE"
        ).getVersionNo());
        OperationConfigVersionAuditView audit = disabled.getAuditTrail().stream()
                .filter(item -> "DISABLE".equals(item.getOperation()))
                .findFirst()
                .orElseThrow();
        assertEquals(1L, audit.getOperatorUserId());
        assertEquals("CURRENT", audit.getFromStatus());
        assertEquals("DISABLED", audit.getToStatus());
        assertEquals("验收停用", audit.getReason());
    }

    @Test
    void disableRejectsSystemDefaultAndDraftVersions() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        OperationConfigVersionRowView draft = service.copyVersion(adminContext(), "DEFAULT_CALENDAR_CONFIG");

        assertThrows(IllegalStateException.class, () -> service.disableVersion(
                adminContext(),
                "DEFAULT_CALENDAR_CONFIG",
                new OperationConfigVersionDisableRequest("default")
        ));
        assertThrows(IllegalStateException.class, () -> service.disableVersion(
                adminContext(),
                draft.getVersionNo(),
                new OperationConfigVersionDisableRequest("draft")
        ));
    }

    @Test
    void operatorPublishRejectsForgedStoreScope() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        BusinessAccessContext operator = operatorContext();
        OperationConfigVersionRowView draft = service.copyVersion(operator, "DEFAULT_CALENDAR_CONFIG");

        assertThrows(BusinessAccessDeniedException.class, () -> service.publishVersion(
                operator,
                draft.getVersionNo(),
                new OperationConfigVersionPublishRequest(307L, "STR-FORGED", "AE", "forged")
        ));
    }

    @Test
    void bossPublishRejectsForgedOwnerScope() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        BusinessAccessContext boss = bossContext();
        OperationConfigVersionRowView draft = service.copyVersion(boss, "DEFAULT_CALENDAR_CONFIG");

        assertThrows(BusinessAccessDeniedException.class, () -> service.publishVersion(
                boss,
                draft.getVersionNo(),
                new OperationConfigVersionPublishRequest(999L, "STR108065-NAE", "AE", "forged")
        ));
    }

    @Test
    void operatorPublishAllowedStoreUsesRequestedScope() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );
        BusinessAccessContext operator = operatorContext();
        OperationConfigVersionRowView draft = service.copyVersion(operator, "DEFAULT_CALENDAR_CONFIG");

        OperationConfigVersionDetailView published = service.publishVersion(
                operator,
                draft.getVersionNo(),
                new OperationConfigVersionPublishRequest(307L, "STR108065-NAE", "AE", "allowed")
        );

        assertEquals("CURRENT", published.getStatus());
        assertEquals("307/STR108065-NAE/AE", published.getScopeSummary());
    }

    @Test
    void bossCanViewOwnImportedDraftWithDescriptiveScopeSummary() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 6, 27, 12, 22);
        repository.insert(new OperationConfigTypedVersion(
                88010L,
                "CALENDAR_FACTOR_DRAFT_20260627",
                "线上销量节日因子草稿 20260627",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "DRAFT",
                "CALENDAR_CONFIG_88007",
                "线上销量因子导入",
                "线上销量节日因子草稿：高置信站点类目因子 + 低置信站点全品兜底",
                128,
                "全局草稿 / 站点+大类目高置信因子 / 站点全品兜底",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"黄色星期五 / SA / paper\",\"cadence\":null,\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-11-20 ~ 2026-11-30 / 1.2500\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"}]",
                307L,
                307L,
                now,
                now
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        List<OperationConfigVersionRowView> rows = service.listVersions(bossContext());
        OperationConfigVersionDetailView detail = service.getDetail(bossContext(), "CALENDAR_FACTOR_DRAFT_20260627");

        assertTrue(rows.stream().anyMatch(row -> "CALENDAR_FACTOR_DRAFT_20260627".equals(row.getVersionNo())));
        assertEquals("DRAFT", detail.getStatus());
        assertEquals(1, detail.getItems().size());
    }

    @Test
    void listVersionsFiltersScopedCustomRowsForUnauthorizedOperator() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 9, 30);
        repository.insert(new OperationConfigTypedVersion(
                88010L,
                "CALENDAR_CONFIG_ALLOWED",
                "授权店铺日历",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "307/STR108065-NAE/AE",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"授权\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                now,
                now
        ));
        repository.insert(new OperationConfigTypedVersion(
                88011L,
                "CALENDAR_CONFIG_FORGED",
                "未授权店铺日历",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "307/STR-FORGED/AE",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"未授权\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                now,
                now
        ));
        OperationConfigVersionLibraryService service = new OperationConfigVersionLibraryService(
                new OperationConfigDefaultVersionCatalog(),
                repository
        );

        List<OperationConfigVersionRowView> rows = service.listVersions(operatorContext());

        assertTrue(rows.stream().anyMatch(row -> "CALENDAR_CONFIG_ALLOWED".equals(row.getVersionNo())));
        assertFalse(rows.stream().anyMatch(row -> "CALENDAR_CONFIG_FORGED".equals(row.getVersionNo())));
        assertTrue(rows.stream().anyMatch(row -> "DEFAULT_CALENDAR_CONFIG".equals(row.getVersionNo())));
    }

    private static OperationConfigTypedVersion versionWithStatus(String versionNo, String status, LocalDateTime now) {
        return new OperationConfigTypedVersion(
                99000L + Math.abs(versionNo.hashCode() % 1000),
                versionNo,
                versionNo,
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                status,
                "DEFAULT_CALENDAR_CONFIG",
                "运营主管",
                "1 条日历配置",
                1,
                "3 个店铺",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"自定义节日系数\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"1.20\",\"resultShape\":null,\"note\":null}]",
                1L,
                2L,
                now,
                now
        );
    }

    private static boolean actionEnabled(OperationConfigVersionRowView row, String action) {
        return row.getActions().stream()
                .filter(item -> action.equals(item.getAction()))
                .findFirst()
                .map(OperationConfigVersionActionView::isEnabled)
                .orElse(false);
    }

    private static BusinessAccessContext adminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config/versions", "/api/operations-config"))
                .build();
    }

    private static BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .menuPaths(Set.of("/operations/config/versions", "/api/operations-config"))
                .build();
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(401L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .menuPaths(Set.of("/operations/config/versions", "/api/operations-config"))
                .build();
    }

    private static class InMemoryOperationConfigTypedVersionRepository implements OperationConfigTypedVersionRepository {
        private long nextId = 88000L;
        private final Map<String, OperationConfigTypedVersion> versions = new LinkedHashMap<>();

        @Override
        public Long nextVersionId() {
            return nextId++;
        }

        @Override
        public void insert(OperationConfigTypedVersion version) {
            versions.put(version.getVersionNo(), version);
        }

        @Override
        public List<OperationConfigTypedVersion> listVersions() {
            return new ArrayList<>(versions.values());
        }

        @Override
        public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
            return Optional.ofNullable(versions.get(versionNo));
        }

        @Override
        public void update(OperationConfigTypedVersion version) {
            versions.put(version.getVersionNo(), version);
        }

        @Override
        public void deleteByVersionNo(String versionNo) {
            versions.remove(versionNo);
        }
    }
}

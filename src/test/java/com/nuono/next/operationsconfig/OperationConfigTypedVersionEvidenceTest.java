package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OperationConfigTypedVersionEvidenceTest {

    @Test
    void defaultForBusinessCalendarReturnsCalendarMetadata() {
        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.defaultFor(
                OperationConfigVersionType.BUSINESS_CALENDAR
        );

        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), evidence.getConfigType());
        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO, evidence.getVersionNo());
        assertEquals("默认日历配置", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void defaultForProductLifecycleReturnsLifecycleMetadata() {
        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.defaultFor(
                OperationConfigVersionType.PRODUCT_LIFECYCLE
        );

        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), evidence.getConfigType());
        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO, evidence.getVersionNo());
        assertEquals("默认生命周期配置", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void defaultForReplenishmentPlanReturnsReplenishmentMetadata() {
        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.defaultFor(
                OperationConfigVersionType.REPLENISHMENT_PLAN
        );

        assertEquals(OperationConfigVersionType.REPLENISHMENT_PLAN.name(), evidence.getConfigType());
        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO, evidence.getVersionNo());
        assertEquals("默认补货计划参数", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void resolveProductLifecycleIgnoresCurrentTypedVersionAndReturnsDefaultMetadata() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                89001L,
                "LIFECYCLE_CURRENT",
                "Current Lifecycle",
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                "CURRENT",
                "运营配置",
                "307/STR108065-NAE/SA",
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));

        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                repository,
                OperationConfigVersionType.PRODUCT_LIFECYCLE,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertEquals(OperationConfigVersionType.PRODUCT_LIFECYCLE.name(), evidence.getConfigType());
        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO, evidence.getVersionNo());
        assertEquals("默认生命周期配置", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void resolveBusinessCalendarReturnsCurrentTypedVersionMetadata() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                89002L,
                "CALENDAR_CURRENT",
                "Current Calendar",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                "运营配置",
                "307/STR108065-NAE/SA",
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));

        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                repository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertEquals(OperationConfigVersionType.BUSINESS_CALENDAR.name(), evidence.getConfigType());
        assertEquals("CALENDAR_CURRENT", evidence.getVersionNo());
        assertEquals("Current Calendar", evidence.getVersionName());
        assertEquals("运营配置", evidence.getSourceLabel());
    }

    @Test
    void resolveReplenishmentPlanReturnsExactCurrentBeforeGlobalAndSystemDefault() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90001L,
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                "Persisted Default Replenishment",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "全局默认",
                LocalDateTime.of(2026, 7, 6, 9, 0)
        ));
        repository.insert(version(
                90002L,
                "REPLENISHMENT_PLAN_GLOBAL",
                "Global Replenishment",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "CURRENT",
                "运营主管",
                "全局当前",
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));
        repository.insert(version(
                90003L,
                "REPLENISHMENT_PLAN_EXACT",
                "Exact Replenishment",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "CURRENT",
                "店铺配置",
                "307/STR108065-NAE/SA",
                LocalDateTime.of(2026, 7, 6, 11, 0)
        ));

        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                repository,
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                307L,
                "str108065-nae",
                "sa"
        );

        assertEquals("REPLENISHMENT_PLAN_EXACT", evidence.getVersionNo());
        assertEquals("Exact Replenishment", evidence.getVersionName());
        assertEquals("店铺配置", evidence.getSourceLabel());
    }

    @Test
    void resolveReplenishmentPlanReturnsGlobalCurrentWhenExactMissing() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90002L,
                "REPLENISHMENT_PLAN_GLOBAL",
                "Global Replenishment",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "CURRENT",
                "运营主管",
                "全局当前",
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));

        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                repository,
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertEquals("REPLENISHMENT_PLAN_GLOBAL", evidence.getVersionNo());
        assertEquals("Global Replenishment", evidence.getVersionName());
        assertEquals("运营主管", evidence.getSourceLabel());
    }

    @Test
    void resolveReplenishmentPlanReturnsPersistedSystemDefaultWhenCurrentsMissing() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90001L,
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                "Persisted Default Replenishment",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "SYSTEM_DEFAULT",
                "系统默认",
                "全局默认",
                LocalDateTime.of(2026, 7, 6, 9, 0)
        ));

        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                repository,
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO, evidence.getVersionNo());
        assertEquals("Persisted Default Replenishment", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void resolveReplenishmentPlanReturnsDefaultMetadataWhenNoVersionExists() {
        OperationConfigTypedVersionEvidence evidence = OperationConfigTypedVersionEvidence.resolve(
                new InMemoryOperationConfigTypedVersionRepository(),
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO, evidence.getVersionNo());
        assertEquals("默认补货计划参数", evidence.getVersionName());
        assertEquals("系统默认", evidence.getSourceLabel());
    }

    @Test
    void nullConfigTypeFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> OperationConfigTypedVersionEvidence.defaultFor(null));
        assertThrows(IllegalArgumentException.class, () -> OperationConfigTypedVersionEvidence.resolve(
                new InMemoryOperationConfigTypedVersionRepository(),
                null,
                307L,
                "STR108065-NAE",
                "SA"
        ));
    }

    private static OperationConfigTypedVersion version(
            Long id,
            String versionNo,
            String displayName,
            String configType,
            String status,
            String sourceLabel,
            String scopeSummary,
            LocalDateTime updatedAt
    ) {
        return new OperationConfigTypedVersion(
                id,
                versionNo,
                displayName,
                configType,
                status,
                null,
                sourceLabel,
                "1 条配置",
                1,
                scopeSummary,
                "[]",
                0L,
                0L,
                updatedAt.minusDays(1),
                updatedAt
        );
    }
}

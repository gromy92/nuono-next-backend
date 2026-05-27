package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.versioning.VersionPublishAction;
import com.nuono.next.versioning.VersionPublishAuditLog;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishRepository;
import com.nuono.next.versioning.VersionPublishService;
import com.nuono.next.versioning.VersionPublishStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationConfigBundleServiceTest {

    @Test
    void createEmptyDraftMakesSuiteVersionVisibleAndAudited() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );

        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 Ramadan baseline")
        );

        List<OperationConfigBundleVersionView> versions = service.listVersions(bossContext());

        assertEquals(1, versions.size());
        OperationConfigBundleVersionView row = versions.get(0);
        assertEquals(draft.getId(), row.getId());
        assertEquals(VersionPublishStatus.DRAFT.name(), row.getStatus());
        assertEquals("老板发布", row.getPublishSourceLabel());
        assertEquals("未设置范围", row.getScopeSummary());
        assertEquals(0, row.getAffectedStoreCount());
        assertEquals(0, row.getActivityRuleCount());
        assertEquals("未配置", row.getLifecycleRuleSummary());
        assertNotNull(row.getPublishRecordId());
        assertFalse(row.getVersionNo().isBlank());

        List<VersionPublishAuditLog> auditLogs = versionRepository.listAudit(
                OperationConfigBundleService.DOMAIN_TYPE,
                row.getId()
        );
        assertEquals(1, auditLogs.size());
        assertEquals(VersionPublishAction.CREATE_DRAFT, auditLogs.get(0).getAction());
    }

    @Test
    void updateScopeStoresAffectedStoresOnTheBundleVersion() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        scopeRepository.addStore(307L, 51011L, "PRJ108065", "canman", "STR108065-NSA", "SA");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 Ramadan baseline")
        );

        OperationConfigBundleVersionView updated = service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(
                                new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"),
                                new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NSA", "SA")
                        )
                )
        );

        assertEquals("已选择 2 个店铺", updated.getScopeSummary());
        assertEquals(2, updated.getAffectedStoreCount());
        assertEquals(2, updated.getScopeStores().size());
        assertEquals("STR108065-NAE", updated.getScopeStores().get(0).getStoreCode());
        assertEquals("STR108065-NSA", updated.getScopeStores().get(1).getStoreCode());
    }

    @Test
    void systemAdminBossSelectionPublishesBossWideBundleForConcreteStores() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addBoss(307L, "canman");
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        scopeRepository.addStore(307L, 51011L, "PRJ108065", "canman", "STR108065-NSA", "SA");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                adminContext(),
                new OperationConfigBundleDraftCommand("系统 Ramadan 基线")
        );

        OperationConfigBundleVersionView scoped = service.updateScope(
                adminContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(List.of(307L), List.of())
        );
        OperationConfigBundleVersionView published = service.publish(adminContext(), draft.getId(), "publish admin baseline");
        OperationConfigCurrentBundleView current = service.resolveCurrent(
                bossContext(),
                307L,
                "STR108065-NAE",
                "AE"
        );

        assertEquals("* / *", scoped.getScopeSummary());
        assertEquals(1, scoped.getAffectedStoreCount());
        assertEquals(1, scoped.getScopeStores().size());
        assertEquals(OperationConfigScopeService.BOSS_WIDE_STORE_CODE, scoped.getScopeStores().get(0).getStoreCode());
        assertEquals(VersionPublishStatus.PUBLISHED.name(), published.getStatus());
        assertEquals("系统发布", published.getPublishSourceLabel());
        assertEquals(draft.getId(), current.getBundle().getId());
        assertEquals("BOSS_WIDE", current.getMatchType());
        assertEquals(VersionPublishAction.PUBLISH, versionRepository.listAudit(
                OperationConfigBundleService.DOMAIN_TYPE,
                draft.getId()
        ).get(1).getAction());
    }

    @Test
    void systemAdminScopeRequiresExactlyOneBossAndRejectsForgedConcreteStore() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addBoss(307L, "canman");
        scopeRepository.addBoss(401L, "other");
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                adminContext(),
                new OperationConfigBundleDraftCommand("系统 Ramadan 基线")
        );

        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                adminContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(List.of(), List.of())
        ));
        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                adminContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(List.of(307L, 401L), List.of())
        ));
        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                adminContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(307L),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        ));
    }

    @Test
    void bossPublishesAuthorizedStoreBundleAndCurrentResolverRejectsOutsideStore() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        scopeRepository.addStore(307L, 51011L, "PRJ108065", "canman", "STR108065-NSA", "SA");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("老板 Ramadan 基线")
        );
        service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );

        OperationConfigBundleVersionView published = service.publish(bossContext(), draft.getId(), "publish boss baseline");
        OperationConfigCurrentBundleView current = service.resolveCurrent(
                bossContext(),
                307L,
                "STR108065-NAE",
                "AE"
        );

        assertEquals(VersionPublishStatus.PUBLISHED.name(), published.getStatus());
        assertEquals("老板发布", published.getPublishSourceLabel());
        assertEquals(draft.getId(), current.getBundle().getId());
        assertEquals("STORE_SITE", current.getMatchType());
        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(401L, "STR401-NAE", "AE"))
                )
        ));
        assertThrows(BusinessAccessDeniedException.class, () -> service.resolveCurrent(
                bossContext(),
                401L,
                "STR401-NAE",
                "AE"
        ));
    }

    @Test
    void operationsManagerAndOperatorScopesAreLimitedToAuthorizedStores() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        scopeRepository.addStore(307L, 51011L, "PRJ108065", "canman", "STR108065-NSA", "SA");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView managerDraft = service.createEmptyDraft(
                operationsManagerContext(),
                new OperationConfigBundleDraftCommand("主管 Ramadan 基线")
        );
        OperationConfigBundleVersionView operatorDraft = service.createEmptyDraft(
                operatorContext(),
                new OperationConfigBundleDraftCommand("运营 Ramadan 基线")
        );

        OperationConfigBundleVersionView managerScoped = service.updateScope(
                operationsManagerContext(),
                managerDraft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(
                                new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"),
                                new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NSA", "SA")
                        )
                )
        );
        OperationConfigBundleVersionView operatorScoped = service.updateScope(
                operatorContext(),
                operatorDraft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );

        assertEquals(2, managerScoped.getAffectedStoreCount());
        assertEquals(1, operatorScoped.getAffectedStoreCount());
        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                operatorContext(),
                operatorDraft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NSA", "SA"))
                )
        ));
        assertThrows(BusinessAccessDeniedException.class, () -> service.updateScope(
                operatorContext(),
                operatorDraft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(307L),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "*", "*"))
                )
        ));
    }

    @Test
    void scopedBundleIsHiddenAndBlockedWhenCreatorLosesStoreAuthorization() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                operatorContext(),
                new OperationConfigBundleDraftCommand("运营 Ramadan 基线")
        );
        service.updateScope(
                operatorContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );

        List<OperationConfigBundleVersionView> versionsAfterAccessLoss =
                service.listVersions(operatorWithoutStoreAccessContext());

        assertTrue(versionsAfterAccessLoss.isEmpty());
        assertThrows(BusinessAccessDeniedException.class, () -> service.copyVersion(
                operatorWithoutStoreAccessContext(),
                draft.getId()
        ));
        assertThrows(BusinessAccessDeniedException.class, () -> service.deleteVersion(
                operatorWithoutStoreAccessContext(),
                draft.getId()
        ));
    }

    @Test
    void currentResolverDoesNotReturnBundleFromAnotherConcreteStore() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        scopeRepository.addStore(307L, 51011L, "PRJ108065", "canman", "STR108065-NSA", "SA");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("老板 Saudi 基线")
        );
        service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NSA", "SA"))
                )
        );
        service.publish(bossContext(), draft.getId(), "publish Saudi baseline");

        OperationConfigCurrentBundleView current = service.resolveCurrent(
                bossContext(),
                307L,
                "STR108065-NAE",
                "AE"
        );

        assertEquals("NONE", current.getMatchType());
        assertNull(current.getBundle());
    }

    @Test
    void exactStorePublishedBundleWinsOverBossWideFallback() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addBoss(307L, "canman");
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView adminDraft = service.createEmptyDraft(
                adminContext(),
                new OperationConfigBundleDraftCommand("系统 Ramadan 基线")
        );
        service.updateScope(
                adminContext(),
                adminDraft.getId(),
                new OperationConfigBundleScopeCommand(List.of(307L), List.of())
        );
        service.publish(adminContext(), adminDraft.getId(), "publish admin baseline");
        OperationConfigBundleVersionView bossDraft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("老板 Ramadan 精准基线")
        );
        service.updateScope(
                bossContext(),
                bossDraft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );
        service.publish(bossContext(), bossDraft.getId(), "publish boss baseline");

        OperationConfigCurrentBundleView current = service.resolveCurrent(
                bossContext(),
                307L,
                "STR108065-NAE",
                "AE"
        );

        assertEquals(bossDraft.getId(), current.getBundle().getId());
        assertEquals("STORE_SITE", current.getMatchType());
    }

    @Test
    void listVersionsKeepsAdminAndBossCreatedBundlesInTheirOwnViews() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );

        OperationConfigBundleVersionView bossDraft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("毕翠红老板草稿")
        );
        OperationConfigBundleVersionView adminDraft = service.createEmptyDraft(
                adminContext(),
                new OperationConfigBundleDraftCommand("系统管理员草稿")
        );

        List<OperationConfigBundleVersionView> adminVersions = service.listVersions(adminContext());
        List<OperationConfigBundleVersionView> bossVersions = service.listVersions(bossContext());

        assertEquals(List.of(adminDraft.getId()), adminVersions.stream()
                .map(OperationConfigBundleVersionView::getId)
                .collect(Collectors.toList()));
        assertEquals(List.of(bossDraft.getId()), bossVersions.stream()
                .map(OperationConfigBundleVersionView::getId)
                .collect(Collectors.toList()));
    }

    @Test
    void listDefaultVersionsReturnsCalendarAndLifecycleDefaultsFromUploadedSheet() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );

        List<OperationConfigDefaultVersionView> defaults = service.listDefaultVersions(adminContext());

        assertEquals(2, defaults.size());
        OperationConfigDefaultVersionView calendar = defaults.get(0);
        assertEquals("DEFAULT_CALENDAR_CONFIG", calendar.getVersionNo());
        assertEquals("默认日历配置", calendar.getDisplayName());
        assertEquals("SYSTEM_DEFAULT", calendar.getStatus());
        assertEquals("business_calendar", calendar.getConfigType());
        assertEquals(13, calendar.getItemCount());
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "斋月 (Ramadan)".equals(item.getItemName()) && "日期范围".equals(item.getValueType())));
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "月度薪酬爆发系数".equals(item.getItemName()) && "每月5日".equals(item.getCadence())));
        assertTrue(calendar.getItems().stream().anyMatch(item ->
                "季节产品".equals(item.getItemName()) && "选择 夏季/雨季".equals(item.getValueType())));

        OperationConfigDefaultVersionView lifecycle = defaults.get(1);
        assertEquals("DEFAULT_LIFECYCLE_CONFIG", lifecycle.getVersionNo());
        assertEquals("默认生命周期配置", lifecycle.getDisplayName());
        assertEquals("product_lifecycle", lifecycle.getConfigType());
        assertEquals(14, lifecycle.getItemCount());
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "新品期最长周期".equals(item.getItemName()) && "60".equals(item.getDefaultValue())));
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "成长期最小销量环比增长率".equals(item.getItemName()) && "0.5".equals(item.getDefaultValue())));
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "稳定期波动率范围".equals(item.getItemName()) && "[0.3, 0.5]".equals(item.getDefaultValue())));
        assertTrue(lifecycle.getItems().stream().anyMatch(item ->
                "长尾期最大月销红量".equals(item.getItemName()) && "10".equals(item.getDefaultValue())));
    }

    @Test
    void listDefaultVersionsIsOnlyVisibleToSystemAdministrator() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );

        assertTrue(service.listDefaultVersions(bossContext()).isEmpty());
    }

    @Test
    void copyVersionCreatesNewDraftWithCopiedScopeAndContentSummary() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView source = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 Ramadan baseline")
        );
        service.updateScope(
                bossContext(),
                source.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );

        OperationConfigBundleVersionView copied = service.copyVersion(bossContext(), source.getId());

        assertEquals(2, service.listVersions(bossContext()).size());
        assertEquals(VersionPublishStatus.DRAFT.name(), copied.getStatus());
        assertEquals("2026 Ramadan baseline 副本", copied.getDisplayName());
        assertEquals("STR108065-NAE / AE", copied.getScopeSummary());
        assertEquals(1, copied.getAffectedStoreCount());
        assertEquals(1, copied.getScopeStores().size());
        assertEquals(source.getActivityRuleCount(), copied.getActivityRuleCount());
        assertEquals(source.getLifecycleRuleSummary(), copied.getLifecycleRuleSummary());
        assertFalse(source.getId().equals(copied.getId()));
    }

    @Test
    void publishRefreshesFullSuiteContentSummaryAndAuditSnapshot() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        InMemoryOperationLifecycleRuleRepository lifecycleRepository = new InMemoryOperationLifecycleRuleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        VersionPublishService versionPublishService = new VersionPublishService(versionRepository);
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                calendarRepository,
                lifecycleRepository,
                new OperationConfigScopeService(scopeRepository),
                versionPublishService
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 full suite")
        );
        service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );
        calendarRepository.insertRule(calendarRule(92000L, draft.getId(), 90000L));
        lifecycleRepository.insertRule(lifecycleRule(93000L, draft.getId(), 90001L));
        insertPublishRecord(versionRepository, 90000L, OperationCalendarRuleService.DOMAIN_TYPE, 92000L);
        insertPublishRecord(versionRepository, 90001L, OperationLifecycleRuleService.DOMAIN_TYPE, 93000L);

        OperationConfigBundleVersionView published = service.publish(bossContext(), draft.getId(), "publish full suite");

        assertEquals(VersionPublishStatus.PUBLISHED.name(), published.getStatus());
        assertEquals(1, published.getActivityRuleCount());
        assertEquals("生命周期规则 1 组", published.getLifecycleRuleSummary());
        VersionPublishAuditLog publishAudit = versionRepository.listAudit(
                OperationConfigBundleService.DOMAIN_TYPE,
                draft.getId()
        ).stream()
                .filter(audit -> VersionPublishAction.PUBLISH.equals(audit.getAction()))
                .findFirst()
                .orElseThrow();
        assertTrue(publishAudit.getAfterSnapshot().contains("\"activityRuleCount\":1"));
        assertTrue(publishAudit.getAfterSnapshot().contains("\"lifecycleRuleSummary\":\"生命周期规则 1 组\""));
    }

    @Test
    void copyVersionCreatesNewDraftWithCopiedScopeActivityFactorsAndLifecycleThresholds() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        InMemoryOperationLifecycleRuleRepository lifecycleRepository = new InMemoryOperationLifecycleRuleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        VersionPublishService versionPublishService = new VersionPublishService(versionRepository);
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                calendarRepository,
                lifecycleRepository,
                new OperationConfigScopeService(scopeRepository),
                versionPublishService
        );
        OperationConfigBundleVersionView source = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 full suite")
        );
        service.updateScope(
                bossContext(),
                source.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );
        calendarRepository.insertRule(calendarRule(92000L, source.getId(), 90000L));
        lifecycleRepository.insertRule(lifecycleRule(93000L, source.getId(), 90001L));
        insertPublishRecord(versionRepository, 90000L, OperationCalendarRuleService.DOMAIN_TYPE, 92000L);
        insertPublishRecord(versionRepository, 90001L, OperationLifecycleRuleService.DOMAIN_TYPE, 93000L);
        OperationConfigBundleVersionView published = service.publish(bossContext(), source.getId(), "publish source");

        OperationConfigBundleVersionView copied = service.copyVersion(bossContext(), published.getId());

        assertEquals(VersionPublishStatus.DRAFT.name(), copied.getStatus());
        assertEquals("STR108065-NAE / AE", copied.getScopeSummary());
        assertEquals(1, copied.getActivityRuleCount());
        assertEquals("生命周期规则 1 组", copied.getLifecycleRuleSummary());
        List<OperationCalendarRule> copiedCalendarRules = calendarRepository.listRulesByBundleVersion(copied.getId());
        assertEquals(1, copiedCalendarRules.size());
        assertEquals(copied.getId(), copiedCalendarRules.get(0).getBundleVersionId());
        assertFalse(92000L == copiedCalendarRules.get(0).getId());
        assertEquals(new BigDecimal("1.3500"), copiedCalendarRules.get(0).getFactorValue());
        assertEquals(OperationConfigPublishStatus.DRAFT, copiedCalendarRules.get(0).getPublishStatus());
        assertFalse(90000L == copiedCalendarRules.get(0).getPublishRecordId());
        List<OperationLifecycleRule> copiedLifecycleRules = lifecycleRepository.listRulesByBundleVersion(copied.getId());
        assertEquals(1, copiedLifecycleRules.size());
        assertEquals(copied.getId(), copiedLifecycleRules.get(0).getBundleVersionId());
        assertEquals(new BigDecimal("22.0000"), copiedLifecycleRules.get(0).getGrowthMinMonthlySales());
        assertEquals(OperationConfigPublishStatus.DRAFT, copiedLifecycleRules.get(0).getPublishStatus());
        assertTrue(versionRepository.listAudit(OperationConfigBundleService.DOMAIN_TYPE, copied.getId()).stream()
                .anyMatch(audit -> VersionPublishAction.COPY_FROM_VERSION.equals(audit.getAction())));
    }

    @Test
    void publishingSameScopeBundleMovesPreviousVersionToHistorical() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView first = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 first suite")
        );
        service.updateScope(
                bossContext(),
                first.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );
        service.publish(bossContext(), first.getId(), "publish first");
        OperationConfigBundleVersionView second = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 second suite")
        );
        service.updateScope(
                bossContext(),
                second.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );

        service.publish(bossContext(), second.getId(), "publish second");

        Map<Long, String> statuses = service.listVersions(bossContext()).stream()
                .collect(Collectors.toMap(OperationConfigBundleVersionView::getId, OperationConfigBundleVersionView::getStatus));
        assertEquals(VersionPublishStatus.HISTORICAL.name(), statuses.get(first.getId()));
        assertEquals(VersionPublishStatus.PUBLISHED.name(), statuses.get(second.getId()));
    }

    @Test
    void disablingPublishedBundleStopsCurrentResolutionButPreservesVersionHistoryAndAudit() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        scopeRepository.addStore(307L, 51004L, "PRJ108065", "canman", "STR108065-NAE", "AE");
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView draft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("2026 disable suite")
        );
        service.updateScope(
                bossContext(),
                draft.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(307L, "STR108065-NAE", "AE"))
                )
        );
        service.publish(bossContext(), draft.getId(), "publish before disable");
        assertEquals(draft.getId(), service.resolveCurrent(bossContext(), 307L, "STR108065-NAE", "AE")
                .getBundle()
                .getId());

        service.deleteVersion(bossContext(), draft.getId());

        OperationConfigBundleVersionView disabled = service.listVersions(bossContext()).stream()
                .filter(version -> draft.getId().equals(version.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(VersionPublishStatus.DISABLED.name(), disabled.getStatus());
        OperationConfigCurrentBundleView current = service.resolveCurrent(bossContext(), 307L, "STR108065-NAE", "AE");
        assertEquals("NONE", current.getMatchType());
        assertNull(current.getBundle());
        assertEquals(1, bundleRepository.listScopeStores(draft.getId()).size());
        assertEquals(VersionPublishAction.DISABLE, versionRepository.listAudit(
                OperationConfigBundleService.DOMAIN_TYPE,
                draft.getId()
        ).get(2).getAction());
    }

    @Test
    void deleteVersionRemovesBundleAndRejectsCrossViewDelete() {
        InMemoryOperationConfigBundleRepository bundleRepository = new InMemoryOperationConfigBundleRepository();
        InMemoryOperationConfigScopeRepository scopeRepository = new InMemoryOperationConfigScopeRepository();
        InMemoryVersionPublishRepository versionRepository = new InMemoryVersionPublishRepository();
        OperationConfigBundleService service = new OperationConfigBundleService(
                bundleRepository,
                new OperationConfigScopeService(scopeRepository),
                new VersionPublishService(versionRepository)
        );
        OperationConfigBundleVersionView bossDraft = service.createEmptyDraft(
                bossContext(),
                new OperationConfigBundleDraftCommand("毕翠红老板草稿")
        );

        assertThrows(BusinessAccessDeniedException.class, () -> service.deleteVersion(adminContext(), bossDraft.getId()));
        service.deleteVersion(bossContext(), bossDraft.getId());

        assertEquals(List.of(), service.listVersions(bossContext()));
        assertEquals(List.of(), bundleRepository.listScopeStores(bossDraft.getId()));
    }

    private static void insertPublishRecord(
            InMemoryVersionPublishRepository repository,
            Long recordId,
            String domainType,
            Long domainRefId
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);
        repository.insertRecord(new VersionPublishRecord(
                recordId,
                domainType,
                domainRefId,
                domainType + "-" + domainRefId,
                VersionPublishStatus.DRAFT,
                "STR108065-NAE/AE",
                null,
                null,
                null,
                null,
                307L,
                307L,
                now,
                now
        ));
    }

    private static OperationCalendarRule calendarRule(Long id, Long bundleVersionId, Long publishRecordId) {
        return new OperationCalendarRule(
                id,
                307L,
                "STR108065-NAE",
                "AE",
                "Ramadan uplift",
                "holiday",
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 3, 19),
                null,
                "all_products",
                null,
                new BigDecimal("1.3500"),
                "demand_uplift",
                true,
                publishRecordId,
                OperationConfigPublishStatus.DRAFT,
                "boss",
                "老板发布",
                307L,
                307L,
                LocalDateTime.of(2026, 5, 25, 10, 0),
                LocalDateTime.of(2026, 5, 25, 10, 0),
                bundleVersionId
        );
    }

    private static OperationLifecycleRule lifecycleRule(Long id, Long bundleVersionId, Long publishRecordId) {
        return new OperationLifecycleRule(
                id,
                307L,
                "STR108065-NAE",
                "AE",
                "LIFECYCLE_CONFIG_v1000",
                "DEFAULT_V1",
                OperationLifecycleRuleThresholds.defaultV1().withGrowthMinMonthlySales(new BigDecimal("22.0000")),
                publishRecordId,
                OperationConfigPublishStatus.DRAFT,
                "boss",
                "老板发布",
                307L,
                307L,
                LocalDateTime.of(2026, 5, 25, 10, 0),
                LocalDateTime.of(2026, 5, 25, 10, 0),
                bundleVersionId
        );
    }

    private static BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NAE", "STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L, "STR108065-NSA", 307L))
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }

    private static BusinessAccessContext operationsManagerContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(2)
                .roleName("运营主管")
                .storeCodes(Set.of("STR108065-NAE", "STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L, "STR108065-NSA", 307L))
                .menuPaths(Set.of("/operations/config/versions"))
                .build();
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(602L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .menuPaths(Set.of("/operations/config/versions"))
                .build();
    }

    private static BusinessAccessContext operatorWithoutStoreAccessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(602L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of())
                .storeOwnerUserIds(Map.of())
                .menuPaths(Set.of("/operations/config/versions"))
                .build();
    }

    private static BusinessAccessContext adminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config"))
                .build();
    }

    private static class InMemoryOperationConfigBundleRepository implements OperationConfigBundleRepository {
        private long nextBundleId = 86000L;
        private final Map<Long, OperationConfigBundle> bundles = new LinkedHashMap<>();
        private final Map<Long, List<OperationConfigBundleScopeStore>> scopes = new LinkedHashMap<>();

        @Override
        public Long nextBundleId() {
            return nextBundleId++;
        }

        @Override
        public void insert(OperationConfigBundle bundle) {
            bundles.put(bundle.getId(), bundle);
        }

        @Override
        public List<OperationConfigBundle> listBundles() {
            return new ArrayList<>(bundles.values());
        }

        @Override
        public Optional<OperationConfigBundle> findBundle(Long bundleId) {
            return Optional.ofNullable(bundles.get(bundleId));
        }

        @Override
        public void replaceScopeStores(Long bundleId, List<OperationConfigBundleScopeStore> scopeStores) {
            scopes.put(bundleId, new ArrayList<>(scopeStores));
        }

        @Override
        public List<OperationConfigBundleScopeStore> listScopeStores(Long bundleId) {
            return new ArrayList<>(scopes.getOrDefault(bundleId, List.of()));
        }

        @Override
        public void updateScopeSummary(Long bundleId, String scopeSummary, Integer affectedStoreCount, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    scopeSummary,
                    affectedStoreCount,
                    bundle.getActivityRuleCount(),
                    bundle.getLifecycleRuleSummary(),
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void updateActivityRuleCount(Long bundleId, Integer activityRuleCount, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    bundle.getScopeSummary(),
                    bundle.getAffectedStoreCount(),
                    activityRuleCount,
                    bundle.getLifecycleRuleSummary(),
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void updateLifecycleRuleSummary(Long bundleId, String lifecycleRuleSummary, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    bundle.getScopeSummary(),
                    bundle.getAffectedStoreCount(),
                    bundle.getActivityRuleCount(),
                    lifecycleRuleSummary,
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void deleteBundle(Long bundleId) {
            bundles.remove(bundleId);
            scopes.remove(bundleId);
        }
    }

    private static class InMemoryOperationCalendarRuleRepository implements OperationCalendarRuleRepository {
        private long nextRuleId = 92500L;
        private final Map<Long, OperationCalendarRule> rules = new LinkedHashMap<>();

        @Override
        public Long nextRuleId() {
            return nextRuleId++;
        }

        @Override
        public void insertRule(OperationCalendarRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public void updateRule(OperationCalendarRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public OperationCalendarRule findRule(Long id) {
            return rules.get(id);
        }

        @Override
        public List<OperationCalendarRule> listActiveRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(OperationCalendarRule::isEnabled)
                    .filter(rule -> OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationCalendarRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationCalendarRule> listRulesByBundleVersion(Long bundleVersionId) {
            return rules.values().stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int countRulesByBundleVersion(Long bundleVersionId) {
            return listRulesByBundleVersion(bundleVersionId).size();
        }
    }

    private static class InMemoryOperationLifecycleRuleRepository implements OperationLifecycleRuleRepository {
        private long nextRuleId = 93500L;
        private final Map<Long, OperationLifecycleRule> rules = new LinkedHashMap<>();

        @Override
        public Long nextRuleId() {
            return nextRuleId++;
        }

        @Override
        public void insertRule(OperationLifecycleRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public void updateRule(OperationLifecycleRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public OperationLifecycleRule findRule(Long id) {
            return rules.get(id);
        }

        @Override
        public List<OperationLifecycleRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<OperationLifecycleRule> findLatestPublished(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .filter(rule -> OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()))
                    .max(Comparator.comparing(OperationLifecycleRule::getUpdatedAt)
                            .thenComparing(OperationLifecycleRule::getId));
        }

        @Override
        public Optional<OperationLifecycleRule> findLatestDraft(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .filter(rule -> OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus()))
                    .max(Comparator.comparing(OperationLifecycleRule::getUpdatedAt)
                            .thenComparing(OperationLifecycleRule::getId));
        }

        @Override
        public List<OperationLifecycleRule> listRulesByBundleVersion(Long bundleVersionId) {
            return rules.values().stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int countRulesByBundleVersion(Long bundleVersionId) {
            return listRulesByBundleVersion(bundleVersionId).size();
        }
    }

    private static class InMemoryOperationConfigScopeRepository implements OperationConfigScopeRepository {
        private final List<OperationConfigBossOption> bosses = new ArrayList<>();
        private final List<OperationConfigStoreScope> stores = new ArrayList<>();

        void addBoss(Long ownerUserId, String displayName) {
            bosses.add(new OperationConfigBossOption(ownerUserId, displayName, null));
        }

        void addStore(Long ownerUserId, Long logicalStoreId, String projectCode, String projectName, String storeCode, String siteCode) {
            stores.add(new OperationConfigStoreScope(ownerUserId, logicalStoreId, projectCode, projectName, storeCode, siteCode));
        }

        @Override
        public List<OperationConfigBossOption> listBossOptions() {
            return bosses;
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
            return stores.stream()
                    .filter(store -> bossUserIds.contains(store.getOwnerUserId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
            return stores.stream()
                    .filter(store -> storeCodes.contains(store.getStoreCode()))
                    .collect(Collectors.toList());
        }
    }

    private static class InMemoryVersionPublishRepository implements VersionPublishRepository {
        private long nextRecordId = 80000L;
        private long nextAuditId = 81000L;
        private final Map<Long, VersionPublishRecord> records = new LinkedHashMap<>();
        private final List<VersionPublishAuditLog> auditLogs = new ArrayList<>();

        @Override
        public Long nextRecordId() {
            return nextRecordId++;
        }

        @Override
        public Long nextAuditId() {
            return nextAuditId++;
        }

        @Override
        public void insertRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public void updateRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public VersionPublishRecord findRecord(Long id) {
            return records.get(id);
        }

        @Override
        public Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .filter(record -> VersionPublishStatus.PUBLISHED.equals(record.getStatus()))
                    .max(Comparator.comparing(VersionPublishRecord::getPublishedAt)
                            .thenComparing(VersionPublishRecord::getId));
        }

        @Override
        public List<VersionPublishRecord> listRecords(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .collect(Collectors.toList());
        }

        @Override
        public void insertAudit(VersionPublishAuditLog auditLog) {
            auditLogs.add(auditLog);
        }

        @Override
        public List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId) {
            return auditLogs.stream()
                    .filter(log -> domainType.equals(log.getDomainType()))
                    .filter(log -> domainRefId.equals(log.getDomainRefId()))
                    .collect(Collectors.toList());
        }
    }
}

package com.nuono.next.operationsconfig;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OperationConfigBundleControllerTest {

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private OperationConfigBundleService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new OperationConfigBundleController(accessResolver, service))
                .build();
    }

    @Test
    void versionsEndpointReturnsSuiteVersionRows() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.listVersions(context)).thenReturn(List.of(new OperationConfigBundleVersionView(
                86000L,
                80000L,
                "OPS_CONFIG_86000",
                "2026 Ramadan baseline",
                "DRAFT",
                "boss",
                "老板发布",
                "未设置范围",
                0,
                0,
                "未配置",
                null,
                null,
                307L,
                LocalDateTime.of(2026, 5, 23, 10, 0),
                List.of()
        )));

        mvc.perform(get("/api/operations-config/bundles/versions"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Operations-Config-Compatibility", "bundle-legacy"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(86000))
                .andExpect(jsonPath("$[0].versionNo").value("OPS_CONFIG_86000"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].publishSourceLabel").value("老板发布"))
                .andExpect(jsonPath("$[0].scopeSummary").value("未设置范围"))
                .andExpect(jsonPath("$[0].activityRuleCount").value(0))
                .andExpect(jsonPath("$[0].lifecycleRuleSummary").value("未配置"));
    }

    @Test
    void defaultVersionsEndpointReturnsSystemDefaultCalendarAndLifecycleRows() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.listDefaultVersions(context)).thenReturn(List.of(
                new OperationConfigDefaultVersionView(
                        "DEFAULT_CALENDAR_CONFIG",
                        "默认日历配置",
                        "business_calendar",
                        "SYSTEM_DEFAULT",
                        "系统默认",
                        "数据-工作表1.csv",
                        "8 个日期范围，5 个系数/选择项",
                        List.of(new OperationConfigDefaultVersionItemView(
                                "业务日历",
                                "斋月 (Ramadan)",
                                "提前一年",
                                "日期范围",
                                null,
                                null,
                                null
                        ))
                ),
                new OperationConfigDefaultVersionView(
                        "DEFAULT_LIFECYCLE_CONFIG",
                        "默认生命周期配置",
                        "product_lifecycle",
                        "SYSTEM_DEFAULT",
                        "系统默认",
                        "数据-工作表1.csv",
                        "14 条 DEFAULT_V1 配置",
                        List.of(new OperationConfigDefaultVersionItemView(
                                "新品期",
                                "新品期最长周期",
                                "随时",
                                "数值",
                                "60",
                                null,
                                null
                        ))
                )
        ));

        mvc.perform(get("/api/operations-config/bundles/default-versions"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Operations-Config-Compatibility", "bundle-legacy"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value("DEFAULT_CALENDAR_CONFIG"))
                .andExpect(jsonPath("$[0].displayName").value("默认日历配置"))
                .andExpect(jsonPath("$[0].items[0].itemName").value("斋月 (Ramadan)"))
                .andExpect(jsonPath("$[1].versionNo").value("DEFAULT_LIFECYCLE_CONFIG"))
                .andExpect(jsonPath("$[1].displayName").value("默认生命周期配置"))
                .andExpect(jsonPath("$[1].items[0].defaultValue").value("60"));

        verify(service).listDefaultVersions(context);
    }

    @Test
    void createDraftEndpointCreatesEmptySuiteDraftFromBackendSession() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.createEmptyDraft(eq(context), any(OperationConfigBundleDraftCommand.class)))
                .thenReturn(new OperationConfigBundleVersionView(
                        86001L,
                        80001L,
                        "OPS_CONFIG_86001",
                        "2026 Eid baseline",
                        "DRAFT",
                        "boss",
                        "老板发布",
                        "未设置范围",
                        0,
                        0,
                        "未配置",
                        null,
                        null,
                        307L,
                        LocalDateTime.of(2026, 5, 23, 10, 5),
                        List.of()
                ));

        mvc.perform(post("/api/operations-config/bundles/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"2026 Eid baseline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(86001))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.displayName").value("2026 Eid baseline"));

        ArgumentCaptor<OperationConfigBundleDraftCommand> captor =
                ArgumentCaptor.forClass(OperationConfigBundleDraftCommand.class);
        verify(service).createEmptyDraft(eq(context), captor.capture());
        assertEquals("2026 Eid baseline", captor.getValue().getDisplayName());
    }

    @Test
    void updateScopeEndpointStoresAffectedStoresOnTheSuiteVersion() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.updateScope(eq(context), eq(86000L), any(OperationConfigBundleScopeCommand.class)))
                .thenReturn(new OperationConfigBundleVersionView(
                        86000L,
                        80000L,
                        "OPS_CONFIG_86000",
                        "2026 Ramadan baseline",
                        "DRAFT",
                        "boss",
                        "老板发布",
                        "已选择 2 个店铺",
                        2,
                        0,
                        "未配置",
                        null,
                        null,
                        307L,
                        LocalDateTime.of(2026, 5, 23, 10, 0),
                        List.of(
                                new OperationConfigBundleScopeStore(307L, "STR108065-NAE", "AE"),
                                new OperationConfigBundleScopeStore(307L, "STR108065-NSA", "SA")
                        )
                ));

        mvc.perform(put("/api/operations-config/bundles/86000/scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stores\":[{\"ownerUserId\":307,\"storeCode\":\"STR108065-NAE\",\"siteCode\":\"AE\"},{\"ownerUserId\":307,\"storeCode\":\"STR108065-NSA\",\"siteCode\":\"SA\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeSummary").value("已选择 2 个店铺"))
                .andExpect(jsonPath("$.affectedStoreCount").value(2))
                .andExpect(jsonPath("$.scopeStores", hasSize(2)))
                .andExpect(jsonPath("$.scopeStores[0].storeCode").value("STR108065-NAE"));

        ArgumentCaptor<OperationConfigBundleScopeCommand> captor =
                ArgumentCaptor.forClass(OperationConfigBundleScopeCommand.class);
        verify(service).updateScope(eq(context), eq(86000L), captor.capture());
        assertEquals(2, captor.getValue().getStores().size());
    }

    @Test
    void copyEndpointCreatesNewSuiteDraftFromExistingVersion() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.copyVersion(context, 86000L))
                .thenReturn(new OperationConfigBundleVersionView(
                        86002L,
                        80002L,
                        "OPS_CONFIG_86002",
                        "2026 Ramadan baseline 副本",
                        "DRAFT",
                        "boss",
                        "老板发布",
                        "已选择 2 个店铺",
                        2,
                        3,
                        "DEFAULT_V1",
                        null,
                        null,
                        307L,
                        LocalDateTime.of(2026, 5, 23, 10, 10),
                        List.of(
                                new OperationConfigBundleScopeStore(307L, "STR108065-NAE", "AE"),
                                new OperationConfigBundleScopeStore(307L, "STR108065-NSA", "SA")
                        )
                ));

        mvc.perform(post("/api/operations-config/bundles/86000/copies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(86002))
                .andExpect(jsonPath("$.displayName").value("2026 Ramadan baseline 副本"))
                .andExpect(jsonPath("$.scopeStores", hasSize(2)));

        verify(service).copyVersion(context, 86000L);
    }

    @Test
    void publishEndpointPublishesSuiteVersionFromBackendSession() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.publish(context, 86000L, "publish empty bundle"))
                .thenReturn(new OperationConfigBundleVersionView(
                        86000L,
                        80000L,
                        "OPS_CONFIG_86000",
                        "2026 Ramadan baseline",
                        "PUBLISHED",
                        "boss",
                        "老板发布",
                        "STR108065-NAE / AE",
                        1,
                        0,
                        "未配置",
                        307L,
                        LocalDateTime.of(2026, 5, 23, 10, 20),
                        307L,
                        LocalDateTime.of(2026, 5, 23, 10, 0),
                        List.of(new OperationConfigBundleScopeStore(307L, "STR108065-NAE", "AE"))
                ));

        mvc.perform(post("/api/operations-config/bundles/86000/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"publish empty bundle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(86000))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishSourceLabel").value("老板发布"))
                .andExpect(jsonPath("$.affectedStoreCount").value(1));

        verify(service).publish(context, 86000L, "publish empty bundle");
    }

    @Test
    void currentEndpointResolvesPublishedBundleForConcreteStore() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        OperationConfigBundleVersionView bundle = new OperationConfigBundleVersionView(
                86000L,
                80000L,
                "OPS_CONFIG_86000",
                "2026 Ramadan baseline",
                "PUBLISHED",
                "boss",
                "老板发布",
                "STR108065-NAE / AE",
                1,
                0,
                "未配置",
                307L,
                LocalDateTime.of(2026, 5, 23, 10, 20),
                307L,
                LocalDateTime.of(2026, 5, 23, 10, 0),
                List.of(new OperationConfigBundleScopeStore(307L, "STR108065-NAE", "AE"))
        );
        when(service.resolveCurrent(context, 307L, "STR108065-NAE", "AE"))
                .thenReturn(new OperationConfigCurrentBundleView(bundle, "STORE_SITE"));

        mvc.perform(get("/api/operations-config/bundles/current")
                        .param("ownerUserId", "307")
                        .param("storeCode", "STR108065-NAE")
                        .param("siteCode", "AE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundle.id").value(86000))
                .andExpect(jsonPath("$.bundle.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.matchType").value("STORE_SITE"));

        verify(service).resolveCurrent(context, 307L, "STR108065-NAE", "AE");
    }

    @Test
    void deleteEndpointRemovesSuiteVersionFromVisibleList() throws Exception {
        BusinessAccessContext context = bossContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);

        mvc.perform(delete("/api/operations-config/bundles/86000"))
                .andExpect(status().isNoContent());

        verify(service).deleteVersion(context, 86000L);
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
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }
}

package com.nuono.next.operationsconfig;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OperationConfigVersionLibraryControllerTest {

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private OperationConfigVersionLibraryService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new OperationConfigVersionLibraryController(accessResolver, service))
                .build();
    }

    @Test
    void versionsEndpointReturnsTypedSystemDefaultRows() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.listVersions(context)).thenReturn(List.of(
                new OperationConfigVersionRowView(
                        "DEFAULT_CALENDAR_CONFIG",
                        "默认日历配置",
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "日历版本",
                        "SYSTEM_DEFAULT",
                        "系统默认",
                        "系统默认",
                        "13 条默认配置",
                        13,
                        "全局默认",
                        null,
                        LocalDateTime.of(2026, 5, 25, 0, 0),
                        List.of(
                                new OperationConfigVersionActionView("EDIT", "编辑", false, "系统默认版本不可编辑"),
                                new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                                new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                                new OperationConfigVersionActionView("DELETE", "删除", false, "系统默认版本不可删除")
                        )
                ),
                new OperationConfigVersionRowView(
                        "DEFAULT_LIFECYCLE_CONFIG",
                        "默认生命周期配置",
                        OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                        "生命周期版本",
                        "SYSTEM_DEFAULT",
                        "系统默认",
                        "系统默认",
                        "25 条 DEFAULT_V1 配置",
                        25,
                        "全局默认",
                        null,
                        LocalDateTime.of(2026, 5, 25, 0, 0),
                        List.of(
                                new OperationConfigVersionActionView("EDIT", "编辑", false, "系统默认版本不可编辑"),
                                new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                                new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                                new OperationConfigVersionActionView("DELETE", "删除", false, "系统默认版本不可删除")
                        )
                )
        ));

        mvc.perform(get("/api/operations-config/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value("DEFAULT_CALENDAR_CONFIG"))
                .andExpect(jsonPath("$[0].displayName").value("默认日历配置"))
                .andExpect(jsonPath("$[0].configType").value("BUSINESS_CALENDAR"))
                .andExpect(jsonPath("$[0].configTypeLabel").value("日历版本"))
                .andExpect(jsonPath("$[0].status").value("SYSTEM_DEFAULT"))
                .andExpect(jsonPath("$[0].statusLabel").value("系统默认"))
                .andExpect(jsonPath("$[0].actions", hasSize(4)))
                .andExpect(jsonPath("$[0].actions[0].action").value("EDIT"))
                .andExpect(jsonPath("$[0].actions[0].enabled").value(false))
                .andExpect(jsonPath("$[0].actions[1].action").value("DETAIL"))
                .andExpect(jsonPath("$[0].actions[1].enabled").value(true))
                .andExpect(jsonPath("$[0].actions[2].action").value("COPY"))
                .andExpect(jsonPath("$[0].actions[2].enabled").value(true))
                .andExpect(jsonPath("$[0].actions[3].action").value("DELETE"))
                .andExpect(jsonPath("$[0].actions[3].enabled").value(false))
                .andExpect(jsonPath("$[1].versionNo").value("DEFAULT_LIFECYCLE_CONFIG"))
                .andExpect(jsonPath("$[1].displayName").value("默认生命周期配置"))
                .andExpect(jsonPath("$[1].configType").value("PRODUCT_LIFECYCLE"))
                .andExpect(jsonPath("$[1].configTypeLabel").value("生命周期版本"));

        verify(service).listVersions(context);
    }

    @Test
    void detailEndpointReturnsSystemDefaultCalendarRows() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.getDetail(context, "DEFAULT_CALENDAR_CONFIG")).thenReturn(new OperationConfigVersionDetailView(
                "DEFAULT_CALENDAR_CONFIG",
                "默认日历配置",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "日历版本",
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                "13 条默认配置",
                13,
                "全局默认",
                null,
                LocalDateTime.of(2026, 5, 25, 0, 0),
                List.of(new OperationConfigVersionActionView("COPY", "复制版本", true, null)),
                List.of(new OperationConfigDefaultVersionItemView(
                        "业务日历",
                        "斋月 (Ramadan)",
                        "提前一年",
                        "日期范围",
                        null,
                        null,
                        null
                ))
        ));

        mvc.perform(get("/api/operations-config/versions/DEFAULT_CALENDAR_CONFIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("DEFAULT_CALENDAR_CONFIG"))
                .andExpect(jsonPath("$.configType").value("BUSINESS_CALENDAR"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].itemName").value("斋月 (Ramadan)"));

        verify(service).getDetail(context, "DEFAULT_CALENDAR_CONFIG");
    }

    @Test
    void detailEndpointReturnsSystemDefaultLifecycleRows() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.getDetail(context, "DEFAULT_LIFECYCLE_CONFIG")).thenReturn(new OperationConfigVersionDetailView(
                "DEFAULT_LIFECYCLE_CONFIG",
                "默认生命周期配置",
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                "生命周期版本",
                "SYSTEM_DEFAULT",
                "系统默认",
                "系统默认",
                "25 条 DEFAULT_V1 配置",
                25,
                "全局默认",
                null,
                LocalDateTime.of(2026, 5, 25, 0, 0),
                List.of(new OperationConfigVersionActionView("COPY", "复制版本", true, null)),
                List.of(new OperationConfigDefaultVersionItemView(
                        "稳定期",
                        "稳定期波动率范围",
                        "随时",
                        "数组",
                        "[0.3, 0.5]",
                        null,
                        null
                ))
        ));

        mvc.perform(get("/api/operations-config/versions/DEFAULT_LIFECYCLE_CONFIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("DEFAULT_LIFECYCLE_CONFIG"))
                .andExpect(jsonPath("$.configType").value("PRODUCT_LIFECYCLE"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].itemName").value("稳定期波动率范围"))
                .andExpect(jsonPath("$.items[0].defaultValue").value("[0.3, 0.5]"));

        verify(service).getDetail(context, "DEFAULT_LIFECYCLE_CONFIG");
    }

    @Test
    void copyEndpointCreatesSameTypeDraftFromDefaultVersion() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.copyVersion(context, "DEFAULT_CALENDAR_CONFIG")).thenReturn(new OperationConfigVersionRowView(
                "CALENDAR_CONFIG_88000",
                "默认日历配置 副本",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "日历版本",
                "DRAFT",
                "草稿",
                "系统管理员",
                "13 条默认配置",
                13,
                "未设置范围",
                1L,
                LocalDateTime.of(2026, 5, 25, 10, 0),
                List.of(
                        new OperationConfigVersionActionView("EDIT", "编辑", true, null),
                        new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                        new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                        new OperationConfigVersionActionView("DELETE", "删除", true, null)
                )
        ));

        mvc.perform(post("/api/operations-config/versions/DEFAULT_CALENDAR_CONFIG/copies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("CALENDAR_CONFIG_88000"))
                .andExpect(jsonPath("$.displayName").value("默认日历配置 副本"))
                .andExpect(jsonPath("$.configType").value("BUSINESS_CALENDAR"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(service).copyVersion(context, "DEFAULT_CALENDAR_CONFIG");
    }

    @Test
    void updateEndpointSavesCalendarDraftContent() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.updateVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionUpdateRequest.class)))
                .thenReturn(new OperationConfigVersionDetailView(
                        "CALENDAR_CONFIG_88000",
                        "默认日历配置 副本",
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "日历版本",
                        "DRAFT",
                        "草稿",
                        "系统管理员",
                        "1 条日历配置",
                        1,
                        "未设置范围",
                        1L,
                        LocalDateTime.of(2026, 5, 25, 10, 30),
                        List.of(new OperationConfigVersionActionView("EDIT", "编辑", true, null)),
                        List.of(new OperationConfigDefaultVersionItemView(
                                "业务日历",
                                "斋月 2027",
                                "提前一年",
                                "日期范围",
                                "2027-02-08 ~ 2027-03-09",
                                null,
                                null
                        ))
                ));

        mvc.perform(put("/api/operations-config/versions/CALENDAR_CONFIG_88000")
                        .contentType("application/json")
                        .content("{\"configType\":\"BUSINESS_CALENDAR\",\"items\":[{\"groupName\":\"业务日历\",\"itemName\":\"斋月 2027\",\"cadence\":\"提前一年\",\"valueType\":\"日期范围\",\"defaultValue\":\"2027-02-08 ~ 2027-03-09\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("CALENDAR_CONFIG_88000"))
                .andExpect(jsonPath("$.configType").value("BUSINESS_CALENDAR"))
                .andExpect(jsonPath("$.summary").value("1 条日历配置"))
                .andExpect(jsonPath("$.items[0].itemName").value("斋月 2027"));

        verify(service).updateVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionUpdateRequest.class));
    }

    @Test
    void deleteEndpointDeletesDraftVersion() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);

        mvc.perform(delete("/api/operations-config/versions/CALENDAR_CONFIG_88000"))
                .andExpect(status().isNoContent());

        verify(service).deleteVersion(context, "CALENDAR_CONFIG_88000");
    }

    @Test
    void publishEndpointPublishesTypedDraftVersion() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.publishVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionPublishRequest.class)))
                .thenReturn(new OperationConfigVersionDetailView(
                        "CALENDAR_CONFIG_88000",
                        "默认日历配置 副本",
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "日历版本",
                        "CURRENT",
                        "当前生效",
                        "系统管理员",
                        "13 条日历配置",
                        13,
                        "307/STR108065-NAE/AE",
                        1L,
                        LocalDateTime.of(2026, 5, 25, 11, 0),
                        List.of(new OperationConfigVersionActionView("EDIT", "编辑", false, "只有草稿版本可编辑")),
                        List.of(new OperationConfigDefaultVersionItemView(
                                "业务日历",
                                "斋月 (Ramadan)",
                                "提前一年",
                                "日期范围",
                                null,
                                null,
                                null
                        ))
                ));

        mvc.perform(post("/api/operations-config/versions/CALENDAR_CONFIG_88000/publish")
                        .contentType("application/json")
                        .content("{\"ownerUserId\":307,\"storeCode\":\"STR108065-NAE\",\"siteCode\":\"AE\",\"message\":\"publish\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("CALENDAR_CONFIG_88000"))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.scopeSummary").value("307/STR108065-NAE/AE"));

        verify(service).publishVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionPublishRequest.class));
    }

    @Test
    void currentEndpointReturnsCurrentTypedVersion() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.currentVersion(context, OperationConfigVersionType.BUSINESS_CALENDAR.name(), 307L, "STR108065-NAE", "AE"))
                .thenReturn(new OperationConfigVersionDetailView(
                        "CALENDAR_CONFIG_88000",
                        "默认日历配置 副本",
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "日历版本",
                        "CURRENT",
                        "当前生效",
                        "系统管理员",
                        "13 条日历配置",
                        13,
                        "307/STR108065-NAE/AE",
                        1L,
                        LocalDateTime.of(2026, 5, 25, 11, 0),
                        List.of(new OperationConfigVersionActionView("DETAIL", "查看详情", true, null)),
                        List.of()
                ));

        mvc.perform(get("/api/operations-config/versions/current")
                        .param("configType", OperationConfigVersionType.BUSINESS_CALENDAR.name())
                        .param("ownerUserId", "307")
                        .param("storeCode", "STR108065-NAE")
                        .param("siteCode", "AE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("CALENDAR_CONFIG_88000"))
                .andExpect(jsonPath("$.status").value("CURRENT"));

        verify(service).currentVersion(context, OperationConfigVersionType.BUSINESS_CALENDAR.name(), 307L, "STR108065-NAE", "AE");
    }

    @Test
    void disableEndpointDisablesPublishedVersionWithAudit() throws Exception {
        BusinessAccessContext context = adminContext();
        when(accessResolver.resolve(any(MockHttpServletRequest.class))).thenReturn(context);
        when(service.disableVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionDisableRequest.class)))
                .thenReturn(new OperationConfigVersionDetailView(
                        "CALENDAR_CONFIG_88000",
                        "默认日历配置 副本",
                        OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                        "日历版本",
                        "DISABLED",
                        "已停用",
                        "系统管理员",
                        "13 条日历配置",
                        13,
                        "全局当前",
                        1L,
                        LocalDateTime.of(2026, 5, 25, 11, 30),
                        List.of(new OperationConfigVersionActionView("DETAIL", "查看详情", true, null)),
                        List.of(),
                        List.of(new OperationConfigVersionAuditView(
                                1L,
                                "系统管理员",
                                "DISABLE",
                                "CURRENT",
                                "DISABLED",
                                "验收停用",
                                LocalDateTime.of(2026, 5, 25, 11, 30)
                        ))
                ));

        mvc.perform(post("/api/operations-config/versions/CALENDAR_CONFIG_88000/disable")
                        .contentType("application/json")
                        .content("{\"reason\":\"验收停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("CALENDAR_CONFIG_88000"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.auditTrail[0].fromStatus").value("CURRENT"))
                .andExpect(jsonPath("$.auditTrail[0].toStatus").value("DISABLED"))
                .andExpect(jsonPath("$.auditTrail[0].reason").value("验收停用"));

        verify(service).disableVersion(any(BusinessAccessContext.class), any(String.class), any(OperationConfigVersionDisableRequest.class));
    }

    private static BusinessAccessContext adminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config/versions", "/api/operations-config"))
                .storeOwnerUserIds(Map.of())
                .build();
    }
}

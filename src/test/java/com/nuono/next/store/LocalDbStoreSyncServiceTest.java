package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbStoreSyncServiceTest {

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private LocalDbBootstrapStatusService localDbBootstrapStatusService;

    @Mock
    private NoonSessionGateway noonSessionGateway;

    @Mock
    private NoonProjectAuthRecoveryQueue projectAuthRecoveryQueue;

    private LocalDbStoreSyncService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbStoreSyncService(
                storeSyncMapper,
                localDbBootstrapStatusService,
                noonSessionGateway,
                projectAuthRecoveryQueue
        );
    }

    @Test
    void shouldBuildReleaseStyleStoreOverviewWithProjectGrouping() {
        when(localDbBootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuono_new_dev", List.of("user"), List.of("user"), List.of())
        );
        when(storeSyncMapper.listOwnerOptions()).thenReturn(List.of(ownerOption(307L, "毕翠红")));
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        StoreSyncStoreRecord connectedProject = project(
                7000L,
                "星耀",
                "XINGYAO",
                true,
                "boss@example.idp.noon.partners",
                1
        );
        connectedProject.setNoonPartnerCookie("sid=verified");
        when(storeSyncMapper.listOwnerProjects(307L)).thenReturn(List.of(connectedProject));
        when(storeSyncMapper.listOwnerProjectSites(307L, List.of("XINGYAO"))).thenReturn(List.of(
                store(7001L, "星耀", "xingyao-AE", "AE", true, "XINGYAO"),
                store(7002L, "星耀", "xingyao-SA", "SA", false, "XINGYAO")
        ));
        when(storeSyncMapper.listManagersByProjectCodes(List.of("XINGYAO"), 307L)).thenReturn(List.of(
                managerRow("XINGYAO", 308L, "马天龙", "运营主管")
        ));

        StoreSyncOverview overview = service.buildOverview(307L);

        assertEquals(1, overview.getStores().size());
        assertEquals("XINGYAO", overview.getStores().get(0).getStoreCode());
        assertEquals(2, overview.getStores().get(0).getSiteStores().size());
        assertEquals("AE", overview.getStores().get(0).getSiteStores().get(0).getSite());
        assertEquals("正常", overview.getStores().get(0).getConnectionStatus());
        assertEquals(1, overview.getSummary().getTotalStores());
        assertEquals(1, overview.getSummary().getConnectedStores());
        assertEquals(2, overview.getSummary().getTotalSiteStores());
        assertEquals("绑定 Noon 商家后台账号后，店铺信息将自动获取。当前已按发布版项目级口径加载 毕翠红 的店铺管理视图。", overview.getMessage());
    }

    @Test
    void shouldKeepAuthorizedProjectUnboundWhenProjectRowHasNoCredential() {
        when(localDbBootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuono_new_dev", List.of("user"), List.of("user"), List.of())
        );
        when(storeSyncMapper.listOwnerOptions()).thenReturn(List.of(ownerOption(307L, "毕翠红")));
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        when(storeSyncMapper.listOwnerProjects(307L)).thenReturn(List.of(
                project(7000L, "canman", "PRJ108065", true, null, 0)
        ));
        when(storeSyncMapper.listOwnerProjectSites(307L, List.of("PRJ108065"))).thenReturn(List.of(
                store(7001L, "canman", "STR108065-NAE", "AE", true, "PRJ108065"),
                store(7002L, "canman", "STR108065-NSA", "SA", true, "PRJ108065")
        ));
        when(storeSyncMapper.listManagersByProjectCodes(List.of("PRJ108065"), 307L)).thenReturn(List.of());

        StoreSyncOverview overview = service.buildOverview(307L);

        assertEquals("未绑定", overview.getStores().get(0).getConnectionStatus());
        assertEquals(null, overview.getStores().get(0).getNoonUser());
        assertEquals("108065", overview.getStores().get(0).getNoonPartnerId());
        assertEquals(Boolean.TRUE, overview.getStores().get(0).getIsAuthorized());
        assertEquals(0, overview.getSummary().getConnectedStores());
        assertEquals(0, overview.getSummary().getConnectedSiteStores());
    }

    @Test
    void shouldBindStoreWithoutDirectOtpAndWithoutPersistingMailboxSecret() {
        StoreBindCommand command = new StoreBindCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("PRJ7001");

        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ7001")).thenReturn(
                project(7000L, "新店铺", "PRJ7001", true, null, 0)
        );
        when(noonSessionGateway.configuredMerchantEmail()).thenReturn("unified@example.com");
        when(storeSyncMapper.listOwnerProjectSites(307L, List.of("PRJ7001"))).thenReturn(List.of(
                store(7001L, "新店铺", "STR7001-NAE", "AE", true, "PRJ7001")
        ));
        when(storeSyncMapper.updateProjectEmailBinding(any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(projectAuthRecoveryQueue.enqueueProject(307L, "PRJ7001", "STR7001-NAE"))
                .thenReturn(Optional.of(91L));

        StoreBindingResult result = service.bindStore(command);

        assertEquals(true, result.isSuccess());
        assertEquals("店铺 新店铺 已绑定共享邮箱，并覆盖 1 个站点。", result.getMessage());
        verify(storeSyncMapper).updateProjectEmailBinding(
                eq(7000L),
                eq(307L),
                eq("unified@example.com"),
                eq(null),
                eq("7001"),
                eq(307L)
        );
        verify(projectAuthRecoveryQueue).enqueueProject(307L, "PRJ7001", "STR7001-NAE");
    }

    @Test
    void shouldRequireExplicitProjectCodeWithoutStartingDirectEmailOtp() {
        StoreCreateCommand command = new StoreCreateCommand();
        command.setOwnerUserId(307L);
        command.setProjectName("新店铺");
        command.setStoreCode("STR7001-NAE");
        command.setSite("AE");

        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createStore(command));

        assertEquals("请输入 Noon Project Code。", error.getMessage());
        verify(storeSyncMapper).selectOwnerContext(307L);
        verifyNoMoreInteractions(storeSyncMapper);
    }

    @Test
    void shouldCreateStoreAfterNoonMerchantProjectIsSelected() {
        StoreCreateCommand command = new StoreCreateCommand();
        command.setOwnerUserId(307L);
        command.setProjectName("新店铺");
        command.setProjectCode("PRJ7001");
        command.setStoreCode("STR7001-NAE");
        command.setSite("AE");
        command.setOrgCode("ORG7001");
        command.setOrgName("新组织");

        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        when(noonSessionGateway.configuredMerchantEmail()).thenReturn("unified@example.com");
        when(storeSyncMapper.nextStoreId()).thenReturn(7010L);
        when(projectAuthRecoveryQueue.enqueueProject(307L, "PRJ7001", "STR7001-NAE"))
                .thenReturn(Optional.of(91L));

        StoreBindingResult result = service.createStore(command);

        assertEquals(true, result.isSuccess());
        assertEquals("店铺 新店铺 已绑定到当前账号视图。", result.getMessage());
        verify(storeSyncMapper).insertOwnerProject(
                eq(307L),
                eq("ORG7001"),
                eq("新组织"),
                eq("PRJ7001"),
                eq("新店铺"),
                eq("unified@example.com"),
                eq(null),
                eq("7001"),
                eq(true),
                eq(true)
        );
        verify(storeSyncMapper).insertOwnerSiteStore(
                eq(7010L),
                eq(307L),
                eq("ORG7001"),
                eq("新组织"),
                eq("PRJ7001"),
                eq("新店铺"),
                eq("STR7001-NAE"),
                eq("AE"),
                eq(true)
        );
        verify(projectAuthRecoveryQueue).enqueueProject(307L, "PRJ7001", "STR7001-NAE");
    }

    @Test
    void shouldSurfaceConfiguredNoonMerchantCredentialErrorWhenCreateStoreMissingConfig() {
        StoreCreateCommand command = new StoreCreateCommand();
        command.setOwnerUserId(307L);
        command.setProjectName("新店铺");
        command.setStoreCode("STR7001-NAE");
        command.setSite("AE");

        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext(307L, "毕翠红"));
        command.setProjectCode("PRJ7001");
        when(noonSessionGateway.configuredMerchantEmail())
                .thenThrow(new IllegalStateException("未配置统一 Noon 商家后台邮箱和邮箱授权码。"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.createStore(command));

        assertEquals("未配置统一 Noon 商家后台邮箱和邮箱授权码。", error.getMessage());
    }

    @Test
    void shouldKeepConnectionTestCookieOnlyWhenPersistedCookieIsExpired() {
        StoreSyncStoreRecord project = project(
                7000L,
                "新店铺",
                "PRJ7001",
                true,
                "merchant@example.com",
                1
        );
        project.setNoonPartnerCookie("sid=expired");
        when(noonSessionGateway.openRequestCountScope()).thenReturn(requestCountScope());
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ7001")).thenReturn(project);
        doThrow(new IllegalStateException("auth_required"))
                .when(noonSessionGateway)
                .validateCatalogSessionWithCookie(
                        "sid=expired",
                        "PRJ7001",
                        "PRJ7001",
                        "merchant@example.com"
                );

        LocalDbStoreSyncService.StoreConnectionTestResult result = service.testConnection(307L, "PRJ7001");

        assertEquals(false, result.isConnected());
        verify(storeSyncMapper, never()).updateProjectConnectionFailure(any(), any(), any());
        verify(noonSessionGateway, never()).login(any(), any(), any(), any(), any(), any());
    }

    private StoreSyncOwnerOption ownerOption(Long id, String name) {
        StoreSyncOwnerOption option = new StoreSyncOwnerOption();
        option.setId(id);
        option.setRealName(name);
        option.setAccountNo("user" + id);
        return option;
    }

    private NoonSessionGateway.RequestCountScope requestCountScope() {
        NoonSessionGateway scopeGateway = new NoonSessionGateway(
                new ObjectMapper(),
                storeSyncMapper,
                false,
                0L,
                true,
                "",
                "",
                "en-sa",
                "en",
                false,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                "HTTP",
                "",
                0,
                ""
        );
        return scopeGateway.openRequestCountScope();
    }

    private StoreSyncOwnerContext ownerContext(Long id, String name) {
        StoreSyncOwnerContext context = new StoreSyncOwnerContext();
        context.setId(id);
        context.setRealName(name);
        context.setAccountNo("user" + id);
        context.setCompanyName("诺诺商家");
        context.setNoonPartnerProjectUser("boss@example.idp.noon.partners");
        context.setNoonPartnerPwd("secret");
        return context;
    }

    private StoreSyncStoreRecord store(
            Long id,
            String projectName,
            String storeCode,
            String site,
            boolean authorized,
            String projectCode
    ) {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setId(id);
        record.setProjectName(projectName);
        record.setStoreCode(storeCode);
        record.setSite(site);
        record.setOwnerAuthorized(authorized);
        record.setProjectCode(projectCode);
        return record;
    }

    private StoreSyncStoreRecord project(
            Long id,
            String projectName,
            String projectCode,
            boolean authorized,
            String noonProjectUser,
            Integer bindStatus
    ) {
        StoreSyncStoreRecord record = store(id, projectName, projectCode, null, authorized, projectCode);
        record.setNoonPartnerProjectUser(noonProjectUser);
        record.setBindStatus(bindStatus);
        return record;
    }

    private StoreSyncManagerRow managerRow(String storeCode, Long id, String name, String role) {
        StoreSyncManagerRow row = new StoreSyncManagerRow();
        row.setStoreCode(storeCode);
        row.setId(id);
        row.setName(name);
        row.setRole(role);
        return row;
    }
}

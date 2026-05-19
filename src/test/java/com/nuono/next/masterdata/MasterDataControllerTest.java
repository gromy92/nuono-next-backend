package com.nuono.next.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.foundation.FoundationUserDetail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MasterDataControllerTest {

    @Mock
    private ObjectProvider<LocalDbMasterDataService> masterDataServiceProvider;

    @Mock
    private LocalDbMasterDataService masterDataService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private MasterDataController controller;

    @BeforeEach
    void setUp() {
        controller = new MasterDataController(masterDataServiceProvider, sessionTokenService);
    }

    @Test
    void shouldReturnUsersWhenServiceAvailable() {
        MockHttpServletRequest request = authenticatedRequest();
        when(masterDataServiceProvider.getIfAvailable()).thenReturn(masterDataService);
        when(masterDataService.getOperatorRoleLevel(10001L)).thenReturn(0);
        when(masterDataService.listUsers(10001L, 0, "merchant")).thenReturn(List.of(new MasterDataUserView()));

        assertEquals(1, controller.users(10001L, 0, "merchant", request).size());
        verify(masterDataService).listUsers(10001L, 0, "merchant");
    }

    @Test
    void shouldReturnUserDetailAndTranslateBadRequest() {
        MockHttpServletRequest request = authenticatedRequest();
        when(masterDataServiceProvider.getIfAvailable()).thenReturn(masterDataService);
        FoundationUserDetail detail = new FoundationUserDetail();
        detail.setId(10004L);
        when(masterDataService.getUserDetail(10004L, 10001L)).thenReturn(detail);

        assertEquals(10004L, controller.userDetail(10004L, request).getId());

        when(masterDataService.getUserDetail(99999L, 10001L)).thenThrow(new IllegalArgumentException("当前样本库里没有找到这条用户记录。"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.userDetail(99999L, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(error.getReason().contains("当前样本库里没有找到这条用户记录。"));
    }

    @Test
    void shouldTranslateAssignRoleSuccessAndFailures() {
        MockHttpServletRequest request = authenticatedRequest();
        when(masterDataServiceProvider.getIfAvailable()).thenReturn(masterDataService);
        MasterDataAssignRoleCommand command = new MasterDataAssignRoleCommand();
        command.setUserId(10004L);
        command.setRoleId(3L);

        when(masterDataService.assignRole(command)).thenReturn("已把用户 马天龙 调整为角色 运营主管，并同步刷新了用户菜单权限。");

        Map<String, Object> payload = controller.assignRole(command, request);
        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals("已把用户 马天龙 调整为角色 运营主管，并同步刷新了用户菜单权限。", payload.get("message"));
        assertEquals(10001L, command.getOperatorUserId());

        when(masterDataService.assignRole(command)).thenThrow(new IllegalArgumentException("目标角色不存在。"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.assignRole(command, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(error.getReason().contains("目标角色不存在。"));
    }

    @Test
    void shouldTranslateRoleAndMenuCrudResponses() {
        MockHttpServletRequest request = authenticatedRequest();
        when(masterDataServiceProvider.getIfAvailable()).thenReturn(masterDataService);

        MasterDataSaveRoleCommand roleCommand = new MasterDataSaveRoleCommand();
        when(masterDataService.createRole(roleCommand)).thenReturn("已新增角色 菜单运营。");
        assertEquals("已新增角色 菜单运营。", controller.createRole(roleCommand, request).get("message"));
        assertEquals(10001L, roleCommand.getOperatorUserId());

        when(masterDataService.updateRole(8L, roleCommand)).thenReturn("已更新角色 菜单运营。");
        assertEquals("已更新角色 菜单运营。", controller.updateRole(8L, roleCommand, request).get("message"));

        when(masterDataService.deleteRole(8L, 10001L)).thenReturn("已删除角色 菜单运营。");
        assertEquals("已删除角色 菜单运营。", controller.deleteRole(8L, 99999L, request).get("message"));

        MasterDataSaveMenuCommand menuCommand = new MasterDataSaveMenuCommand();
        when(masterDataService.createMenu(menuCommand)).thenReturn("已新增菜单 菜单维护。");
        assertEquals("已新增菜单 菜单维护。", controller.createMenu(menuCommand, request).get("message"));
        assertEquals(10001L, menuCommand.getOperatorUserId());

        when(masterDataService.updateMenu(26L, menuCommand)).thenReturn("已更新菜单 菜单维护。");
        assertEquals("已更新菜单 菜单维护。", controller.updateMenu(26L, menuCommand, request).get("message"));

        when(masterDataService.deleteMenu(26L, 10001L)).thenReturn("已删除菜单 菜单维护。");
        assertEquals("已删除菜单 菜单维护。", controller.deleteMenu(26L, 99999L, request).get("message"));
    }

    @Test
    void shouldRejectRequestsWhenServiceUnavailable() {
        when(masterDataServiceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.users(null, null, null, new MockHttpServletRequest())
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertInstanceOf(ResponseStatusException.class, error);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        return request;
    }
}

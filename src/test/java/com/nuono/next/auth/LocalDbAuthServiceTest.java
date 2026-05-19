package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbAuthServiceTest {

    @Mock
    private AuthMapper authMapper;

    private LocalDbAuthService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbAuthService(authMapper);
    }

    @Test
    void shouldLoginWithCurrentPlainPassword() {
        AuthLoginCommand command = command("admin", "admin123");
        when(authMapper.selectLoginAccount("admin")).thenReturn(
                account("admin", "admin123", 1, "系统管理员", null, null)
        );
        when(authMapper.selectGrantedMenus(10004L)).thenReturn(List.of(grantedMenu(10L, "用户管理", "/api/user/manage")));

        AuthLoginResult result = service.login(command);

        assertEquals("admin", result.getAccountNo());
        assertEquals("系统管理员", result.getRoleName());
        assertEquals(1, result.getGrantedMenus().size());
    }

    @Test
    void shouldLoginWithLegacySaltedPassword() {
        AuthLoginCommand command = command("ops001", "Ahoney$123");
        when(authMapper.selectLoginAccount("ops001")).thenReturn(
                account(
                        "ops001",
                        LegacyPasswordCodec.encryptWithSalt("Ahoney$123", LegacyPasswordCodec.LEGACY_SALT),
                        1,
                        "运营",
                        null,
                        null
                )
        );
        when(authMapper.selectGrantedMenus(10004L)).thenReturn(List.of(grantedMenu(25L, "角色分配", "/api/user/role")));

        AuthLoginResult result = service.login(command);

        assertEquals("ops001", result.getAccountNo());
        assertEquals("运营", result.getRoleName());
    }

    @Test
    void shouldLoginWithLegacySuperPassword() {
        AuthLoginCommand command = command("boss001", "Ahoney$123");
        when(authMapper.selectLoginAccount("boss001")).thenReturn(
                account(
                        "boss001",
                        LegacyPasswordCodec.encryptWithSalt("any-real-password", LegacyPasswordCodec.LEGACY_SALT),
                        1,
                        "老板",
                        null,
                        null
                )
        );
        when(authMapper.selectGrantedMenus(10001L)).thenReturn(List.of(grantedMenu(24L, "采购", "/api/purchase/order")));

        AuthLoginResult result = service.login(command);

        assertEquals("boss001", result.getAccountNo());
        assertEquals(10001L, result.getDefaultOwnerUserId());
    }

    @Test
    void shouldRejectWrongPassword() {
        AuthLoginCommand command = command("admin", "wrong-pass");
        when(authMapper.selectLoginAccount("admin")).thenReturn(
                account("admin", "admin123", 1, "系统管理员", null, null)
        );

        assertEquals(
                "账号或密码不正确。",
                assertThrows(IllegalArgumentException.class, () -> service.login(command)).getMessage()
        );
    }

    @Test
    void shouldRejectDisabledAccount() {
        AuthLoginCommand command = command("admin", "admin123");
        when(authMapper.selectLoginAccount("admin")).thenReturn(
                account("admin", "admin123", 0, "系统管理员", null, null)
        );

        assertEquals(
                "当前账号已停用，暂时不能登录。",
                assertThrows(IllegalArgumentException.class, () -> service.login(command)).getMessage()
        );
    }

    @Test
    void shouldRejectAccountsOutsideEffectiveWindow() {
        AuthLoginCommand future = command("future001", "admin123");
        when(authMapper.selectLoginAccount("future001")).thenReturn(
                account("future001", "admin123", 1, "运营", LocalDateTime.now().plusDays(1), null)
        );
        assertEquals(
                "当前账号未在有效期内，暂时不能登录。",
                assertThrows(IllegalArgumentException.class, () -> service.login(future)).getMessage()
        );

        AuthLoginCommand expired = command("expired001", "admin123");
        when(authMapper.selectLoginAccount("expired001")).thenReturn(
                account("expired001", "admin123", 1, "运营", null, LocalDateTime.now().minusDays(1))
        );
        assertEquals(
                "当前账号未在有效期内，暂时不能登录。",
                assertThrows(IllegalArgumentException.class, () -> service.login(expired)).getMessage()
        );
    }

    @Test
    void shouldReturnSampleAccountsFromMapper() {
        AuthSampleAccount sample = new AuthSampleAccount();
        sample.setAccountNo("admin");
        sample.setPassword("admin123");
        when(authMapper.listSampleAccounts()).thenReturn(List.of(sample));

        List<AuthSampleAccount> accounts = service.listSampleAccounts();

        assertEquals(1, accounts.size());
        verify(authMapper).listSampleAccounts();
    }

    @Test
    void shouldChangeCurrentUserPassword() {
        AuthChangePasswordCommand command = changePasswordCommand(10004L, "Next123!");
        when(authMapper.updateCurrentUserPassword(10004L, "Next123!")).thenReturn(1);

        String message = service.changePassword(command);

        assertEquals("密码修改成功", message);
        verify(authMapper).updateCurrentUserPassword(10004L, "Next123!");
    }

    @Test
    void shouldRejectInvalidChangePasswordPayload() {
        assertEquals(
                "密码需为 6-14 位，不能包含空格或中文。",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.changePassword(changePasswordCommand(10004L, "短密码"))
                ).getMessage()
        );
    }

    private AuthLoginCommand command(String accountNo, String password) {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo(accountNo);
        command.setPassword(password);
        return command;
    }

    private AuthChangePasswordCommand changePasswordCommand(Long userId, String newPassword) {
        AuthChangePasswordCommand command = new AuthChangePasswordCommand();
        command.setUserId(userId);
        command.setNewPassword(newPassword);
        return command;
    }

    private AuthLoginAccount account(
            String accountNo,
            String storedPassword,
            Integer status,
            String roleName,
            LocalDateTime effectiveTime,
            LocalDateTime expiredTime
    ) {
        AuthLoginAccount account = new AuthLoginAccount();
        account.setUserId(roleName != null && roleName.equals("老板") ? 10001L : 10004L);
        account.setAccountNo(accountNo);
        account.setStoredPassword(storedPassword);
        account.setRealName(accountNo);
        account.setRoleName(roleName);
        if ("系统管理员".equals(roleName)) {
            account.setLevel(0);
        } else if ("老板".equals(roleName)) {
            account.setLevel(1);
        } else {
            account.setLevel(2);
        }
        account.setCompanyName("Nuono");
        account.setStatus(status);
        account.setStoreCount(1);
        account.setAuthorizedStoreCount(1);
        account.setBindingStatus("PROJECT_BOUND");
        account.setEffectiveTime(effectiveTime);
        account.setExpiredTime(expiredTime);
        return account;
    }

    private AuthGrantedMenu grantedMenu(Long menuId, String menuName, String urlPath) {
        AuthGrantedMenu grantedMenu = new AuthGrantedMenu();
        grantedMenu.setMenuId(menuId);
        grantedMenu.setMenuName(menuName);
        grantedMenu.setUrlPath(urlPath);
        return grantedMenu;
    }
}

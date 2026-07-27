package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbAuthPasswordMigrationTest {

    @Mock
    private AuthMapper authMapper;

    private UserPasswordService passwordService;
    private LocalDbAuthService service;

    @BeforeEach
    void setUp() {
        passwordService = new UserPasswordService(4, new SecureRandom());
        service = new LocalDbAuthService(authMapper, passwordService);
    }

    @Test
    void shouldLoginWithModernCredentialWithoutWritingItAgain() {
        String storedCredential = passwordService.encode("Modern123!");
        when(authMapper.selectLoginAccount("modern001")).thenReturn(
                account("modern001", storedCredential)
        );
        when(authMapper.selectGrantedMenus(10004L)).thenReturn(List.of());

        AuthLoginResult result = service.login(command("modern001", "Modern123!"));

        assertEquals("modern001", result.getAccountNo());
        verify(authMapper, never()).upgradePasswordIfUnchanged(anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRejectLegacyLoginWhenConcurrentPasswordChangeWinsTheUpgradeRace() {
        AuthLoginAccount legacyAccount = account("ops001", "Legacy123!");
        AuthLoginAccount changedAccount = account("ops001", passwordService.encode("Changed123!"));
        when(authMapper.selectLoginAccount("ops001")).thenReturn(legacyAccount, changedAccount);
        when(authMapper.upgradePasswordIfUnchanged(
                eq(10004L),
                eq("Legacy123!"),
                anyString()
        )).thenReturn(0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(command("ops001", "Legacy123!"))
        );

        assertEquals("账号或密码不正确。", error.getMessage());
        verify(authMapper, never()).selectGrantedMenus(anyLong());
    }

    @Test
    void shouldAcceptLegacyLoginWhenAnotherLoginAlreadyUpgradedTheSamePassword() {
        AuthLoginAccount legacyAccount = account("ops001", "Legacy123!");
        AuthLoginAccount upgradedAccount = account("ops001", passwordService.encode("Legacy123!"));
        when(authMapper.selectLoginAccount("ops001")).thenReturn(legacyAccount, upgradedAccount);
        when(authMapper.upgradePasswordIfUnchanged(
                eq(10004L),
                eq("Legacy123!"),
                anyString()
        )).thenReturn(0);
        when(authMapper.selectGrantedMenus(10004L)).thenReturn(List.of());

        AuthLoginResult result = service.login(command("ops001", "Legacy123!"));

        assertEquals("ops001", result.getAccountNo());
        verify(authMapper).selectUserStores(10004L);
    }

    private AuthLoginCommand command(String accountNo, String password) {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo(accountNo);
        command.setPassword(password);
        return command;
    }

    private AuthLoginAccount account(String accountNo, String storedCredential) {
        AuthLoginAccount account = new AuthLoginAccount();
        account.setUserId(10004L);
        account.setAccountNo(accountNo);
        account.setStoredPassword(storedCredential);
        account.setRealName(accountNo);
        account.setRoleName("运营");
        account.setLevel(2);
        account.setCompanyName("Nuono");
        account.setStatus(1);
        account.setStoreCount(1);
        account.setAuthorizedStoreCount(1);
        account.setBindingStatus("PROJECT_BOUND");
        return account;
    }
}

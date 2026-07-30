package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbAuthPasswordChangeTest {

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
    void shouldChangeCurrentUserPassword() {
        AuthChangePasswordCommand command = command("Current123!", "Next123!");
        when(authMapper.selectCurrentPasswordCredential(10004L)).thenReturn("Current123!");
        when(authMapper.updateCurrentUserPassword(
                org.mockito.ArgumentMatchers.eq(10004L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("Current123!"),
                anyString()
        )).thenReturn(1);

        String message = service.changePassword(command);

        assertEquals("密码修改成功", message);
        ArgumentCaptor<String> credentialCaptor = ArgumentCaptor.forClass(String.class);
        verify(authMapper).updateCurrentUserPassword(
                org.mockito.ArgumentMatchers.eq(10004L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("Current123!"),
                credentialCaptor.capture()
        );
        assertFalse(credentialCaptor.getValue().equals("Next123!"));
        assertTrue(passwordService.matches("Next123!", credentialCaptor.getValue()));
    }

    @Test
    void shouldRejectWrongPersonalPasswordIncludingTheUniversalAdminPassword() {
        AuthChangePasswordCommand command = command("Ahoney$123", "Next123!");
        when(authMapper.selectCurrentPasswordCredential(10004L)).thenReturn("Current123!");

        assertEquals(
                "当前密码不正确。",
                assertThrows(IllegalArgumentException.class, () -> service.changePassword(command)).getMessage()
        );
        verify(authMapper, never()).updateCurrentUserPassword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                anyString(),
                anyString()
        );
    }

    @Test
    void shouldRequireTheCurrentPersonalPassword() {
        AuthChangePasswordCommand command = command(null, "Next123!");

        assertEquals(
                "请输入当前密码。",
                assertThrows(IllegalArgumentException.class, () -> service.changePassword(command)).getMessage()
        );
        verify(authMapper, never()).selectCurrentPasswordCredential(
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldRejectAStaleSessionInsteadOfOverwritingAnAdministratorReset() {
        AuthChangePasswordCommand command = command("Current123!", "Next123!");
        when(authMapper.selectCurrentPasswordCredential(10004L)).thenReturn("Current123!");
        when(authMapper.updateCurrentUserPassword(
                org.mockito.ArgumentMatchers.eq(10004L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("Current123!"),
                anyString()
        )).thenReturn(0);

        assertThrows(AuthSessionChangedException.class, () -> service.changePassword(command));
    }

    @Test
    void shouldRejectInvalidChangePasswordPayload() {
        assertEquals(
                "密码需为 6-14 位，不能包含空格或中文。",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.changePassword(command("Current123!", "短密码"))
                ).getMessage()
        );
    }

    private AuthChangePasswordCommand command(String currentPassword, String newPassword) {
        AuthChangePasswordCommand command = new AuthChangePasswordCommand();
        command.setUserId(10004L);
        command.setExpectedCredentialVersion(7L);
        command.setCurrentPassword(currentPassword);
        command.setNewPassword(newPassword);
        return command;
    }
}

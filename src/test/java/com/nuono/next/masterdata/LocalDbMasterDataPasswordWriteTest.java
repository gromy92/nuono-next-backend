package com.nuono.next.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.UserPasswordService;
import com.nuono.next.foundation.LocalDbFoundationOverviewService;
import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbMasterDataPasswordWriteTest {

    @Mock
    private MasterDataMapper masterDataMapper;

    @Mock
    private LocalDbFoundationOverviewService foundationOverviewService;

    @Mock
    private UserPasswordService passwordService;

    private LocalDbMasterDataService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbMasterDataService(masterDataMapper, foundationOverviewService, passwordService);
    }

    @Test
    void shouldEncodeInitialPasswordBeforeCreatingUser() {
        MasterDataSaveUserCommand command = new MasterDataSaveUserCommand();
        command.setAccountNo("new-user");
        command.setPassword("Initial123!");
        command.setRoleId(4L);
        command.setOperatorUserId(10001L);

        when(masterDataMapper.selectRoleSeed(4L)).thenReturn(seed(4L, "运营", 3));
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));
        when(masterDataMapper.nextUserId()).thenReturn(10010L);
        when(masterDataMapper.listRoleMenuIds(4L)).thenReturn(List.of());
        when(passwordService.encode("Initial123!")).thenReturn("encoded-initial-password");

        assertEquals(10010L, service.createUser(command));

        ArgumentCaptor<String> credentialCaptor = ArgumentCaptor.forClass(String.class);
        verify(masterDataMapper).insertUser(
                eq(10010L),
                any(),
                any(),
                eq("new-user"),
                credentialCaptor.capture(),
                any(),
                eq(4L),
                any(),
                any(),
                any(),
                eq(3),
                eq(1),
                any(LocalDateTime.class),
                eq(10001L)
        );
        assertEquals("encoded-initial-password", credentialCaptor.getValue());
        assertFalse(credentialCaptor.getValue().equals("Initial123!"));
    }

    @Test
    void shouldEncodeEditedPasswordBeforeUpdatingUser() {
        MasterDataUserView existing = user(10004L, "ops", 3, 10002L);
        existing.setRoleId(4L);
        MasterDataSaveUserCommand command = new MasterDataSaveUserCommand();
        command.setPassword("Edited123!");
        command.setOperatorUserId(10001L);

        when(masterDataMapper.selectUserView(10004L)).thenReturn(existing);
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));
        when(passwordService.encode("Edited123!")).thenReturn("encoded-edited-password");

        service.updateUser(10004L, command);

        verify(masterDataMapper).updateUserPassword(10004L, "encoded-edited-password", 10001L);
        verify(passwordService).encode("Edited123!");
    }

    @Test
    void shouldGenerateAndEncodeOneTimePasswordWhenResetRequestOmitsPassword() {
        MasterDataResetPasswordCommand command = new MasterDataResetPasswordCommand();
        command.setOperatorUserId(10001L);
        MasterDataUserView existing = user(10004L, "ops", 3, 10002L);
        String temporaryPassword = "Tmp9!Abc2#Xy7Z";

        when(masterDataMapper.selectUserView(10004L)).thenReturn(existing);
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));
        when(passwordService.generateTemporaryPassword()).thenReturn(temporaryPassword);
        when(passwordService.encode(temporaryPassword)).thenReturn("encoded-temporary-password");

        String message = service.resetUserPassword(10004L, command);

        verify(masterDataMapper).updateUserPassword(10004L, "encoded-temporary-password", 10001L);
        assertTrue(message.contains(temporaryPassword));
        assertTrue(message.contains("仅显示一次"));
        assertFalse(message.contains("123456"));
    }

    @Test
    void shouldEncodeExplicitResetPasswordWithoutEchoingIt() {
        MasterDataResetPasswordCommand command = new MasterDataResetPasswordCommand();
        command.setOperatorUserId(10001L);
        command.setPassword("Explicit123!");
        MasterDataUserView existing = user(10004L, "ops", 3, 10002L);

        when(masterDataMapper.selectUserView(10004L)).thenReturn(existing);
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));
        when(passwordService.encode("Explicit123!")).thenReturn("encoded-explicit-password");

        String message = service.resetUserPassword(10004L, command);

        verify(masterDataMapper).updateUserPassword(10004L, "encoded-explicit-password", 10001L);
        assertFalse(message.contains("Explicit123!"));
    }

    private MasterDataUserView user(Long id, String accountNo, Integer roleLevel, Long createdBy) {
        MasterDataUserView user = new MasterDataUserView();
        user.setId(id);
        user.setAccountNo(accountNo);
        user.setRoleLevel(roleLevel);
        user.setCreatedBy(createdBy);
        user.setStatus(1);
        user.setAccountType("internal");
        return user;
    }

    private MasterDataRoleAssignmentSeed seed(Long id, String name, Integer level) {
        MasterDataRoleAssignmentSeed seed = new MasterDataRoleAssignmentSeed();
        seed.setId(id);
        seed.setName(name);
        seed.setLevel(level);
        return seed;
    }
}

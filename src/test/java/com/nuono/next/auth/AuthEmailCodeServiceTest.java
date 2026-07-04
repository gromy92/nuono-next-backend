package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.AuthEmailCodeChallengeMapper;
import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthEmailCodeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-03T06:00:00Z"), ZONE);

    @Mock
    private AuthEmailCodeChallengeMapper challengeMapper;

    @Mock
    private AuthEmailCodeSender sender;

    @Mock
    private AuthMapper authMapper;

    private AuthEmailCodeProperties properties;
    private LocalDbAuthService localDbAuthService;
    private AuthEmailCodeService service;

    @BeforeEach
    void setUp() {
        properties = new AuthEmailCodeProperties();
        properties.setAllowedEmail("login@example.com");
        properties.setLoginAccountNo("boss001");
        properties.setTtlSeconds(300);
        properties.setCooldownSeconds(60);
        properties.setMaxAttempts(5);
        localDbAuthService = new LocalDbAuthService(authMapper);
        service = new AuthEmailCodeService(
                challengeMapper,
                sender,
                localDbAuthService,
                properties,
                FIXED_CLOCK,
                () -> "123456",
                () -> "fixed-salt"
        );
    }

    @Test
    void shouldCreateHashedLoginChallengeAndSendCodeToAllowedEmail() {
        AuthEmailCodeRequestCommand command = new AuthEmailCodeRequestCommand();
        command.setEmail(" LOGIN@example.com ");

        service.requestLoginCode(command);

        ArgumentCaptor<AuthEmailCodeChallengeRecord> challengeCaptor =
                ArgumentCaptor.forClass(AuthEmailCodeChallengeRecord.class);
        verify(challengeMapper).consumeActiveChallenges("login@example.com", "LOGIN", now());
        verify(challengeMapper).insertChallenge(challengeCaptor.capture());
        AuthEmailCodeChallengeRecord challenge = challengeCaptor.getValue();
        assertEquals("login@example.com", challenge.getEmail());
        assertEquals("LOGIN", challenge.getPurpose());
        assertEquals("fixed-salt", challenge.getCodeSalt());
        assertFalse(challenge.getCodeHash().contains("123456"));
        assertEquals(now().plusSeconds(300), challenge.getExpiresAt());
        verify(sender).sendLoginCode("login@example.com", "123456", now().plusSeconds(300));
    }

    @Test
    void shouldRejectEmailOutsideAllowedMailboxBeforeSendingCode() {
        AuthEmailCodeRequestCommand command = new AuthEmailCodeRequestCommand();
        command.setEmail("other@example.com");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.requestLoginCode(command)
        );

        assertEquals("当前邮箱不允许登录。", error.getMessage());
        verify(sender, never()).sendLoginCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRequireSingleAllowedMailboxConfiguration() {
        properties.setAllowedEmail(null);
        AuthEmailCodeRequestCommand command = new AuthEmailCodeRequestCommand();
        command.setEmail("login@example.com");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.requestLoginCode(command)
        );

        assertEquals("邮箱验证码登录未配置允许邮箱。", error.getMessage());
        verify(sender, never()).sendLoginCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldLoginWithValidEmailCodeAndConsumeChallenge() {
        AuthEmailCodeLoginCommand command = new AuthEmailCodeLoginCommand();
        command.setEmail("login@example.com");
        command.setCode("123456");
        AuthEmailCodeChallengeRecord challenge = challenge("login@example.com", "123456", 0);
        when(challengeMapper.selectLatestActiveChallenge("login@example.com", "LOGIN", now())).thenReturn(challenge);
        when(authMapper.selectLoginAccount("boss001")).thenReturn(
                account("boss001", 10001L, "老板", 1)
        );
        when(authMapper.selectGrantedMenus(10001L)).thenReturn(List.of(grantedMenu(24L, "采购", "/api/purchase/order")));

        AuthLoginResult result = service.login(command);

        assertEquals("boss001", result.getAccountNo());
        assertEquals(10001L, result.getDefaultOwnerUserId());
        verify(challengeMapper).consumeChallenge(9001L, now());
    }

    @Test
    void shouldIncrementAttemptsAndRejectInvalidEmailCode() {
        AuthEmailCodeLoginCommand command = new AuthEmailCodeLoginCommand();
        command.setEmail("login@example.com");
        command.setCode("000000");
        AuthEmailCodeChallengeRecord challenge = challenge("login@example.com", "123456", 0);
        when(challengeMapper.selectLatestActiveChallenge("login@example.com", "LOGIN", now())).thenReturn(challenge);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.login(command));

        assertEquals("邮箱验证码不正确或已过期。", error.getMessage());
        verify(challengeMapper).incrementAttempts(9001L, now());
    }

    private AuthEmailCodeChallengeRecord challenge(String email, String code, int attemptCount) {
        AuthEmailCodeChallengeRecord challenge = new AuthEmailCodeChallengeRecord();
        challenge.setId(9001L);
        challenge.setEmail(email);
        challenge.setPurpose("LOGIN");
        challenge.setCodeSalt("fixed-salt");
        challenge.setCodeHash(AuthEmailCodeHashSupport.hash(email, "LOGIN", code, "fixed-salt"));
        challenge.setExpiresAt(now().plusSeconds(300));
        challenge.setAttemptCount(attemptCount);
        return challenge;
    }

    private AuthLoginAccount account(String accountNo, Long userId, String roleName, Integer status) {
        AuthLoginAccount account = new AuthLoginAccount();
        account.setUserId(userId);
        account.setAccountNo(accountNo);
        account.setStoredPassword("unused");
        account.setRealName(roleName);
        account.setRoleName(roleName);
        account.setRoleId(2L);
        account.setLevel("老板".equals(roleName) ? 1 : 2);
        account.setStatus(status);
        account.setStoreCount(1);
        account.setAuthorizedStoreCount(1);
        account.setBindingStatus("PROJECT_BOUND");
        return account;
    }

    private AuthGrantedMenu grantedMenu(Long menuId, String menuName, String urlPath) {
        AuthGrantedMenu grantedMenu = new AuthGrantedMenu();
        grantedMenu.setMenuId(menuId);
        grantedMenu.setMenuName(menuName);
        grantedMenu.setUrlPath(urlPath);
        return grantedMenu;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(FIXED_CLOCK);
    }
}

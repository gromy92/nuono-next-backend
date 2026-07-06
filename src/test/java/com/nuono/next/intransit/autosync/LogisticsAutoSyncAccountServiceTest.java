package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncAccountServiceTest {

    @Mock
    private LogisticsAutoSyncMapper mapper;

    @Test
    void saveNewAccountStoresUnverifiedEncryptedDefaultsAndHash() {
        LogisticsAutoSyncAccountService service = service();
        when(mapper.nextAccountId()).thenReturn(180001L);
        when(mapper.insertAccount(any(LogisticsAutoSyncAccount.class))).thenReturn(1);
        LogisticsAutoSyncAccountCommand command = command(null, " yitong ", " user@example.com ", "clear-password");
        command.setCommitEnabled(true);
        command.setMinIntervalHours(999);
        ArgumentCaptor<LogisticsAutoSyncAccount> rowCaptor = ArgumentCaptor.forClass(LogisticsAutoSyncAccount.class);

        LogisticsAutoSyncAccountView view = service.save(command, 501L);

        verify(mapper).insertAccount(rowCaptor.capture());
        LogisticsAutoSyncAccount row = rowCaptor.getValue();
        assertThat(row.getId()).isEqualTo(180001L);
        assertThat(row.getOwnerUserId()).isEqualTo(307L);
        assertThat(row.getOperatorUserId()).isEqualTo(408L);
        assertThat(row.getSourceSystem()).isEqualTo("ET");
        assertThat(row.getForwarderName()).isEqualTo("Forwarder");
        assertThat(row.getLoginAccount()).isEqualTo("user@example.com");
        assertThat(row.getLoginAccountHash()).isEqualTo(sha256Hex("ET|user@example.com"));
        assertThat(row.getPasswordCipher()).startsWith("v1:");
        assertThat(row.getPasswordCipher()).doesNotContain("clear-password");
        assertThat(cipher().decrypt(row.getPasswordCipher())).isEqualTo("clear-password");
        assertThat(row.getEnabled()).isFalse();
        assertThat(row.getScheduleEnabled()).isFalse();
        assertThat(row.getCommitEnabled()).isTrue();
        assertThat(row.getMinIntervalHours()).isEqualTo(168);
        assertThat(row.getVerificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(row.getCreatedBy()).isEqualTo(501L);
        assertThat(row.getUpdatedBy()).isEqualTo(501L);
        assertThat(view.getAccountId()).isEqualTo(180001L);
        assertThat(view.getLoginAccountMasked()).isEqualTo("us***om");
    }

    @Test
    void updateWithoutPasswordPreservesCipherAndVerificationStatus() {
        LogisticsAutoSyncAccountService service = service();
        LogisticsAutoSyncAccount existing = existingAccount();
        existing.setVerificationStatus("READY");
        existing.setPasswordCipher(cipher().encrypt("old-password"));
        when(mapper.selectAccountById(180001L)).thenReturn(existing);
        when(mapper.updateAccount(any(LogisticsAutoSyncAccount.class))).thenReturn(1);
        LogisticsAutoSyncAccountCommand command = command(180001L, "CHIC", "login@example.com", null);
        command.setMinIntervalHours(0);
        ArgumentCaptor<LogisticsAutoSyncAccount> rowCaptor = ArgumentCaptor.forClass(LogisticsAutoSyncAccount.class);

        service.save(command, 502L);

        verify(mapper).updateAccount(rowCaptor.capture());
        LogisticsAutoSyncAccount row = rowCaptor.getValue();
        assertThat(cipher().decrypt(row.getPasswordCipher())).isEqualTo("old-password");
        assertThat(row.getVerificationStatus()).isEqualTo("READY");
        assertThat(row.getMinIntervalHours()).isEqualTo(1);
        assertThat(row.getUpdatedBy()).isEqualTo(502L);
    }

    @Test
    void updateResetsVerificationWhenIdentityOrPasswordChanges() {
        LogisticsAutoSyncAccountService service = service();
        LogisticsAutoSyncAccount existing = existingAccount();
        existing.setVerificationStatus("READY");
        existing.setPasswordCipher(cipher().encrypt("old-password"));
        when(mapper.selectAccountById(180001L)).thenReturn(existing);
        when(mapper.updateAccount(any(LogisticsAutoSyncAccount.class))).thenReturn(1);
        LogisticsAutoSyncAccountCommand command = command(180001L, "YITE", "login@example.com", "new-password");
        ArgumentCaptor<LogisticsAutoSyncAccount> rowCaptor = ArgumentCaptor.forClass(LogisticsAutoSyncAccount.class);

        service.save(command, 502L);

        verify(mapper).updateAccount(rowCaptor.capture());
        LogisticsAutoSyncAccount row = rowCaptor.getValue();
        assertThat(row.getSourceSystem()).isEqualTo("YITE");
        assertThat(row.getVerificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(cipher().decrypt(row.getPasswordCipher())).isEqualTo("new-password");
    }

    @Test
    void listAndRequireReturnOnlySafeAccountViews() {
        LogisticsAutoSyncAccountService service = service();
        LogisticsAutoSyncAccount row = existingAccount();
        row.setPasswordCipher(cipher().encrypt("hidden-password"));
        when(mapper.listAccounts(307L)).thenReturn(List.of(row));
        when(mapper.selectAccountById(180001L)).thenReturn(row);

        List<LogisticsAutoSyncAccountView> views = service.list(307L);
        LogisticsAutoSyncAccount required = service.requireAccount(180001L);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.getAccountId()).isEqualTo(180001L);
            assertThat(view.getLoginAccountMasked()).isEqualTo("lo***om");
        });
        assertThat(required).isSameAs(row);
        assertThat(fieldNames(LogisticsAutoSyncAccountView.class))
                .doesNotContain("password", "passwordCipher", "cookie", "token");
    }

    @Test
    void requireAccountRejectsMissingAccount() {
        LogisticsAutoSyncAccountService service = service();
        when(mapper.selectAccountById(180001L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireAccount(180001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流自动同步账号不存在");
    }

    @Test
    void viewMasksLoginAccountAndCopiesSafeFields() {
        LogisticsAutoSyncAccount row = account("abcdefij");
        row.setId(180001L);
        row.setOwnerUserId(307L);
        row.setOperatorUserId(408L);
        row.setSourceSystem("ET");
        row.setForwarderName("ET Forwarder");
        row.setEnabled(true);
        row.setScheduleEnabled(true);
        row.setCommitEnabled(false);
        row.setScheduleWindowStart(LocalTime.of(1, 0));
        row.setScheduleWindowEnd(LocalTime.of(5, 30));
        row.setMinIntervalHours(24);
        row.setVerificationStatus("UNVERIFIED");
        row.setLastLoginStatus("SUCCESS");
        row.setLastPreviewStatus("PREVIEWED");
        row.setLastSyncStatus("SKIPPED");
        row.setLastTaskId(9901L);
        row.setLastSyncedAt(LocalDateTime.of(2026, 7, 6, 1, 2));
        row.setNextEligibleAt(LocalDateTime.of(2026, 7, 7, 1, 2));
        row.setCooldownUntil(LocalDateTime.of(2026, 7, 6, 3, 2));
        row.setLastFailureCode("LOGIN_FAILED");
        row.setLastFailureMessage("masked failure");
        row.setCreatedAt(LocalDateTime.of(2026, 7, 1, 8, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 7, 6, 8, 0));

        LogisticsAutoSyncAccountView view = LogisticsAutoSyncAccountView.from(row);

        assertThat(view.getAccountId()).isEqualTo(180001L);
        assertThat(view.getOwnerUserId()).isEqualTo(307L);
        assertThat(view.getOperatorUserId()).isEqualTo(408L);
        assertThat(view.getSourceSystem()).isEqualTo("ET");
        assertThat(view.getForwarderName()).isEqualTo("ET Forwarder");
        assertThat(view.getLoginAccountMasked()).isEqualTo("ab***ij");
        assertThat(view.getEnabled()).isTrue();
        assertThat(view.getScheduleEnabled()).isTrue();
        assertThat(view.getCommitEnabled()).isFalse();
        assertThat(view.getScheduleWindowStart()).isEqualTo(LocalTime.of(1, 0));
        assertThat(view.getScheduleWindowEnd()).isEqualTo(LocalTime.of(5, 30));
        assertThat(view.getMinIntervalHours()).isEqualTo(24);
        assertThat(view.getVerificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(view.getLastLoginStatus()).isEqualTo("SUCCESS");
        assertThat(view.getLastPreviewStatus()).isEqualTo("PREVIEWED");
        assertThat(view.getLastSyncStatus()).isEqualTo("SKIPPED");
        assertThat(view.getLastTaskId()).isEqualTo(9901L);
        assertThat(view.getLastSyncedAt()).isEqualTo(LocalDateTime.of(2026, 7, 6, 1, 2));
        assertThat(view.getNextEligibleAt()).isEqualTo(LocalDateTime.of(2026, 7, 7, 1, 2));
        assertThat(view.getCooldownUntil()).isEqualTo(LocalDateTime.of(2026, 7, 6, 3, 2));
        assertThat(view.getLastFailureCode()).isEqualTo("LOGIN_FAILED");
        assertThat(view.getLastFailureMessage()).isEqualTo("masked failure");
        assertThat(view.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 8, 0));
        assertThat(view.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 6, 8, 0));
    }

    @Test
    void viewMasksShortAndBlankLoginAccounts() {
        assertThat(LogisticsAutoSyncAccountView.from(account("abcd")).getLoginAccountMasked())
                .isEqualTo("a***");
        assertThat(LogisticsAutoSyncAccountView.from(account("a")).getLoginAccountMasked())
                .isEqualTo("a***");
        assertThat(LogisticsAutoSyncAccountView.from(account(" ")).getLoginAccountMasked())
                .isNull();
        assertThat(LogisticsAutoSyncAccountView.from(null)).isNull();
    }

    @Test
    void safeContractsDoNotExposePlaintextSecretsOrCipherInView() {
        assertThat(fieldNames(LogisticsAutoSyncAccount.class))
                .doesNotContain("password", "cookie", "token", "sessionCookie", "accessToken", "refreshToken");
        assertThat(fieldNames(LogisticsAutoSyncAccountView.class))
                .doesNotContain("password", "passwordCipher", "cookie", "token", "sessionCookie", "accessToken", "refreshToken");
    }

    private static LogisticsAutoSyncAccount account(String loginAccount) {
        LogisticsAutoSyncAccount row = new LogisticsAutoSyncAccount();
        row.setLoginAccount(loginAccount);
        return row;
    }

    private LogisticsAutoSyncAccountService service() {
        return new LogisticsAutoSyncAccountService(mapper, cipher());
    }

    private static LogisticsCredentialCipher cipher() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.setCredentialCipherSecret("test-logistics-cipher-secret");
        return new LogisticsCredentialCipher(properties);
    }

    private static LogisticsAutoSyncAccountCommand command(
            Long accountId,
            String sourceSystem,
            String loginAccount,
            String password
    ) {
        LogisticsAutoSyncAccountCommand command = new LogisticsAutoSyncAccountCommand();
        command.setAccountId(accountId);
        command.setOwnerUserId(307L);
        command.setOperatorUserId(408L);
        command.setSourceSystem(sourceSystem);
        command.setForwarderName(" Forwarder ");
        command.setLoginAccount(loginAccount);
        command.setPassword(password);
        return command;
    }

    private static LogisticsAutoSyncAccount existingAccount() {
        LogisticsAutoSyncAccount row = new LogisticsAutoSyncAccount();
        row.setId(180001L);
        row.setOwnerUserId(307L);
        row.setOperatorUserId(408L);
        row.setSourceSystem("CHIC");
        row.setForwarderName("Forwarder");
        row.setLoginAccount("login@example.com");
        row.setLoginAccountHash(sha256Hex("CHIC|login@example.com"));
        row.setEnabled(true);
        row.setScheduleEnabled(true);
        row.setCommitEnabled(false);
        row.setMinIntervalHours(24);
        row.setVerificationStatus("READY");
        return row;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}

package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LogisticsAutoSyncAccountServiceTest {

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

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}

package com.nuono.next.noon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nuono.next.noonauth.NoonAuthRecoveryCoordinator;
import com.nuono.next.noonauth.NoonAuthRecoveryScheduler;
import com.nuono.next.noonauth.NoonAuthRecoveryWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class NoonAccountManualOtpServiceTest {

    @Test
    void onlyAnExplicitOperatorActionSendsOneOtpAndTheSameChallengeCannotSendAgain() {
        RecordingGateway gateway = new RecordingGateway();
        NoonAccountManualOtpService service = service(gateway);

        assertThat(service.status().getStatus()).isEqualTo(NoonAccountSessionStatus.UNKNOWN);
        NoonAccountSessionView first = service.send(307L);
        NoonAccountSessionView repeated = service.send(307L);

        assertThat(gateway.sendCount).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(NoonAccountSessionStatus.OTP_SENT);
        assertThat(repeated.getChallengeId()).isEqualTo(first.getChallengeId());
    }

    @Test
    void submittedOtpIsNeverRetriedEvenWhenValidationFails() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failValidation = true;
        NoonAccountManualOtpService service = service(gateway);
        NoonAccountSessionView challenge = service.send(307L);

        assertThatThrownBy(() -> service.verify(307L, challenge.getChallengeId(), "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validation failed");

        assertThat(gateway.validationCount).isEqualTo(1);
        assertThat(service.status().getStatus()).isEqualTo(NoonAccountSessionStatus.MANUAL_OTP_REQUIRED);
        assertThatThrownBy(() -> service.verify(307L, challenge.getChallengeId(), "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已失效");
        assertThat(gateway.validationCount).isEqualTo(1);
    }

    @Test
    void anotherOperatorCannotTakeOverOrSubmitTheCurrentChallenge() {
        RecordingGateway gateway = new RecordingGateway();
        NoonAccountManualOtpService service = service(gateway);
        NoonAccountSessionView challenge = service.send(307L);

        assertThatThrownBy(() -> service.send(308L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("另一位账号管理员");
        assertThatThrownBy(() -> service.verify(308L, challenge.getChallengeId(), "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前账号管理员");

        assertThat(gateway.sendCount).isEqualTo(1);
        assertThat(gateway.validationCount).isZero();
    }

    @Test
    void attentionOnlyRaisesManualLoginAndHasNoLegacyQueueAdapter() {
        RecordingGateway gateway = new RecordingGateway();
        NoonAccountManualOtpService service = service(gateway);
        @SuppressWarnings("unchecked")
        ObjectProvider<NoonAccountManualOtpService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);

        NoonAccountSessionAttention attention = new NoonAccountSessionAttention(provider);
        attention.requireManualLogin();

        assertThat(service.status().getStatus()).isEqualTo(NoonAccountSessionStatus.MANUAL_OTP_REQUIRED);
        assertThat(attention.blocksProviderCalls()).isTrue();
        assertThat(gateway.sendCount).isZero();
        assertThat(gateway.validationCount).isZero();
    }

    @Test
    void oldRecoveryExecutorIsNotRegisteredAsAnApplicationComponent() {
        assertThat(NoonAuthRecoveryCoordinator.class.getAnnotation(Service.class)).isNull();
        assertThat(NoonAuthRecoveryWorker.class.getAnnotation(Service.class)).isNull();
        assertThat(NoonAuthRecoveryScheduler.class.getAnnotation(Component.class)).isNull();
    }

    private static NoonAccountManualOtpService service(RecordingGateway gateway) {
        return new NoonAccountManualOtpService(
                gateway,
                (grant, operatorUserId) -> new NoonAccountProjectSessionRefresher.RefreshResult(1, 0),
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom()
        );
    }

    private static final class RecordingGateway implements NoonAccountManualOtpGateway {
        private int sendCount;
        private int validationCount;
        private boolean failValidation;

        @Override
        public PreparedChallenge sendOneManualOtp() {
            sendCount++;
            return new PreparedChallenge(new Object());
        }

        @Override
        public AuthenticatedGrant validateSubmittedOtp(PreparedChallenge challenge, String otpCode) {
            validationCount++;
            if (failValidation) {
                throw new IllegalStateException("validation failed");
            }
            return new AuthenticatedGrant(new Object());
        }

        @Override
        public VerifiedProjectSession createVerifiedProjectSession(
                AuthenticatedGrant grant, String projectCode, String storeCode
        ) {
            throw new AssertionError("manual service delegates Project refresh separately");
        }
    }
}

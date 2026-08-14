package com.nuono.next.noon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAccountProjectSessionRefresherAdapterTest {

    @Test
    void oneValidatedAccountGrantFansOutToBoundProjectsWithoutAnotherOtp() {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        RecordingGateway gateway = new RecordingGateway();
        NoonAccountSessionProjectTarget first = target(307L, "PRJ307A", "STR307A");
        NoonAccountSessionProjectTarget second = target(307L, "PRJ307B", "STR307B");
        when(mapper.listBoundProjects()).thenReturn(List.of(first, second));
        when(mapper.persistProjectSession(anyLong(), any(), any(), any(), anyLong())).thenReturn(1);

        NoonAccountProjectSessionRefresher.RefreshResult result =
                new NoonAccountProjectSessionRefresherAdapter(mapper, gateway).refresh(
                        new NoonAccountManualOtpGateway.AuthenticatedGrant(new Object()), 1L
                );

        assertThat(result.getRefreshedProjects()).isEqualTo(2);
        assertThat(result.getFailedProjects()).isZero();
        assertThat(gateway.createdProjects).containsExactly("PRJ307A", "PRJ307B");
        assertThat(gateway.sendCount).isZero();
        verify(mapper).persistProjectSession(307L, "PRJ307A", "sid=PRJ307A", "identity-user", 1L);
        verify(mapper).persistProjectSession(307L, "PRJ307B", "sid=PRJ307B", "identity-user", 1L);
    }

    private static NoonAccountSessionProjectTarget target(Long ownerUserId, String projectCode, String storeCode) {
        NoonAccountSessionProjectTarget target = new NoonAccountSessionProjectTarget();
        target.setOwnerUserId(ownerUserId);
        target.setProjectCode(projectCode);
        target.setStoreCode(storeCode);
        return target;
    }

    private static final class RecordingGateway implements NoonAccountManualOtpGateway {
        private int sendCount;
        private final java.util.List<String> createdProjects = new java.util.ArrayList<>();

        @Override
        public PreparedChallenge sendOneManualOtp() {
            sendCount++;
            throw new AssertionError("refresh must not send OTP");
        }

        @Override
        public AuthenticatedGrant validateSubmittedOtp(PreparedChallenge challenge, String otpCode) {
            throw new AssertionError("refresh receives an already validated grant");
        }

        @Override
        public VerifiedProjectSession createVerifiedProjectSession(
                AuthenticatedGrant grant, String projectCode, String storeCode
        ) {
            createdProjects.add(projectCode);
            return new VerifiedProjectSession("sid=" + projectCode, "identity-user");
        }
    }
}

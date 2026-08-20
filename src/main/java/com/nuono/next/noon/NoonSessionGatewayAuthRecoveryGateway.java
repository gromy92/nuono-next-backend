package com.nuono.next.noon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonauth.NoonAuthCheckpointVault;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class NoonSessionGatewayAuthRecoveryGateway implements NoonAuthRecoveryGateway {
    private final NoonAuthCheckpointVault checkpointVault;
    private final Clock clock;
    private final NoonSharedEmailOtpAttempt sharedEmailOtpAttempt;

    @Autowired
    public NoonSessionGatewayAuthRecoveryGateway(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            @Value("${nuono.noon.auth.email-otp.poll-interval-millis:5000}") long pollIntervalMillis,
            @Value("${nuono.noon.auth.email-otp.poll-timeout-millis:90000}") long pollTimeoutMillis,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMailAuthCode,
            NoonAuthCheckpointVault checkpointVault,
            NoonAuthRecoveryProperties properties,
            ObjectMapper objectMapper
    ) {
        this(
                sessionGateway,
                emailOtpReader,
                Duration.ofMillis(Math.max(250L, pollIntervalMillis)),
                Duration.ofMillis(Math.max(1000L, pollTimeoutMillis)),
                Clock.systemUTC(),
                Thread::sleep,
                configuredMailAuthCode,
                checkpointVault,
                new NoonAuthGatewayCheckpointCodec(objectMapper),
                properties.checkpointTtl()
        );
    }

    NoonSessionGatewayAuthRecoveryGateway(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            Duration pollInterval,
            Duration pollTimeout,
            Clock clock,
            NoonOtpMailboxPoller.Sleeper sleeper,
            String configuredMailAuthCode,
            NoonAuthCheckpointVault checkpointVault,
            NoonAuthGatewayCheckpointCodec checkpointCodec,
            Duration checkpointTtl
    ) {
        this.checkpointVault = checkpointVault;
        this.clock = clock;
        this.sharedEmailOtpAttempt = new NoonSharedEmailOtpAttempt(
                sessionGateway,
                emailOtpReader,
                pollInterval,
                pollTimeout,
                clock,
                sleeper,
                configuredMailAuthCode,
                checkpointVault,
                checkpointCodec,
                checkpointTtl
        );
    }

    @Override
    public boolean canResume(long recoveryId) {
        try {
            return checkpointVault.load(recoveryId, clock.instant()).isPresent();
        } catch (RuntimeException unreadableCheckpoint) {
            return false;
        }
    }

    @Override
    public boolean requiresCheckpointSecret() {
        return true;
    }

    @Override
    public void clearCheckpoint(long recoveryId) {
        checkpointVault.clear(recoveryId);
    }

    @Override
    public synchronized NoonAuthRecoveryAttemptResult attempt(NoonAuthRecoveryAttemptCommand command) {
        return sharedEmailOtpAttempt.run(command);
    }
}

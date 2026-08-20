package com.nuono.next.noon;

import static com.nuono.next.noon.NoonAuthAttemptResults.failed;
import static com.nuono.next.noon.NoonAuthAttemptResults.transientIdentityFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.classifyIdentityFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.classifyMailboxFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.safeDiagnostic;

import com.nuono.next.noonauth.NoonAuthCheckpointVault;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class NoonOtpChallengeStarter {
    private final NoonSessionGateway sessionGateway;
    private final NoonEmailOtpReader emailOtpReader;
    private final Clock clock;
    private final String mailAuthCode;
    private final NoonAuthCheckpointVault vault;
    private final NoonAuthGatewayCheckpointCodec codec;
    private final Duration checkpointTtl;

    NoonOtpChallengeStarter(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            Clock clock,
            String mailAuthCode,
            NoonAuthCheckpointVault vault,
            NoonAuthGatewayCheckpointCodec codec,
            Duration checkpointTtl
    ) {
        this.sessionGateway = sessionGateway;
        this.emailOtpReader = emailOtpReader;
        this.clock = clock;
        this.mailAuthCode = mailAuthCode;
        this.vault = vault;
        this.codec = codec;
        this.checkpointTtl = checkpointTtl;
    }

    StartResult start(NoonAuthRecoveryAttemptCommand command) {
        final String email;
        try {
            email = sessionGateway.configuredMerchantLoginEmail();
            if (!StringUtils.hasText(mailAuthCode)) {
                throw new IllegalStateException("mailbox credential unavailable");
            }
        } catch (RuntimeException exception) {
            return StartResult.failed(failed(
                    NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED,
                    null,
                    "mailbox credential unavailable"
            ));
        }
        Optional<NoonAuthCheckpointVault.Checkpoint> stored = vault.load(
                command.getRecoveryId(), clock.instant()
        );
        if (stored.isPresent() && stored.get().getKind() == NoonAuthCheckpointVault.Kind.IDENTITY_GRANT) {
            return StartResult.grant(
                    email,
                    mailAuthCode,
                    sessionGateway.restoreEmailIdentityGrant(codec.decodeGrant(stored.get().getPayload()))
            );
        }
        if (stored.isPresent() && stored.get().getKind() == NoonAuthCheckpointVault.Kind.OTP_CHALLENGE) {
            NoonAuthGatewayCheckpointCodec.Challenge challenge =
                    codec.decodeChallenge(stored.get().getPayload());
            return StartResult.challenge(
                    email,
                    mailAuthCode,
                    sessionGateway.restoreEmailOtpGeneration(challenge.generation),
                    challenge.cursor,
                    challenge.sentAt,
                    true
            );
        }
        final NoonSessionGateway.EmailOtpGeneration generation;
        try {
            generation = sessionGateway.prepareEmailOtpGeneration(email);
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            Optional<NoonTransientErrorType> transientType =
                    NoonProjectTransientFailureClassifier.classify(exception);
            return StartResult.failed(transientType.isPresent()
                    ? transientIdentityFailure(
                            NoonAuthRecoveryFailureStage.IDENTITY_PREPARATION, transientType.get(), null)
                    : failed(classifyIdentityFailure(exception), null,
                            safeDiagnostic("identity preparation", exception)));
        }
        command.heartbeatOrThrow();
        final NoonEmailOtpReader.MailboxCursor cursor;
        try {
            cursor = emailOtpReader.snapshot(email, mailAuthCode);
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            NoonAuthRecoveryFailureCode mailboxFailure = classifyMailboxFailure(exception);
            Optional<NoonTransientErrorType> transientType =
                    NoonProjectTransientFailureClassifier.classify(exception);
            return StartResult.failed(mailboxFailure != NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED
                    && transientType.isPresent()
                    ? transientIdentityFailure(
                            NoonAuthRecoveryFailureStage.MAILBOX_SNAPSHOT, transientType.get(), null)
                    : failed(mailboxFailure, null, safeDiagnostic("mailbox snapshot", exception)));
        }
        command.heartbeatOrThrow();
        command.beforeOtpSendOrThrow();
        Instant sentAt = clock.instant();
        vault.save(
                command.getRecoveryId(),
                command.getGeneration(),
                NoonAuthCheckpointVault.Kind.OTP_CHALLENGE,
                codec.encodeChallenge(sessionGateway.snapshotEmailOtpGeneration(generation), cursor, sentAt),
                sentAt.plus(checkpointTtl)
        );
        return StartResult.challenge(email, mailAuthCode, generation, cursor, sentAt, false);
    }

    static final class StartResult {
        final String email;
        final String mailAuthCode;
        final NoonSessionGateway.EmailOtpGeneration generation;
        final NoonEmailOtpReader.MailboxCursor cursor;
        final Instant sentAt;
        final boolean resumedChallenge;
        final NoonSessionGateway.EmailIdentityGrant restoredGrant;
        final NoonAuthRecoveryAttemptResult failure;

        private StartResult(
                String email,
                String mailAuthCode,
                NoonSessionGateway.EmailOtpGeneration generation,
                NoonEmailOtpReader.MailboxCursor cursor,
                Instant sentAt,
                boolean resumedChallenge,
                NoonSessionGateway.EmailIdentityGrant restoredGrant,
                NoonAuthRecoveryAttemptResult failure
        ) {
            this.email = email;
            this.mailAuthCode = mailAuthCode;
            this.generation = generation;
            this.cursor = cursor;
            this.sentAt = sentAt;
            this.resumedChallenge = resumedChallenge;
            this.restoredGrant = restoredGrant;
            this.failure = failure;
        }

        static StartResult failed(NoonAuthRecoveryAttemptResult failure) {
            return new StartResult(null, null, null, null, null, false, null, failure);
        }

        static StartResult grant(String email, String mailAuthCode, NoonSessionGateway.EmailIdentityGrant grant) {
            return new StartResult(email, mailAuthCode, null, null, null, true, grant, null);
        }

        static StartResult challenge(
                String email,
                String mailAuthCode,
                NoonSessionGateway.EmailOtpGeneration generation,
                NoonEmailOtpReader.MailboxCursor cursor,
                Instant sentAt,
                boolean resumed
        ) {
            return new StartResult(
                    email, mailAuthCode, generation, cursor, sentAt, resumed, null, null
            );
        }
    }
}

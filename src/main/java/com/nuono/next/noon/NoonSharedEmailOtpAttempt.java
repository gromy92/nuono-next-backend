package com.nuono.next.noon;

import static com.nuono.next.noon.NoonAuthAttemptResults.failed;
import static com.nuono.next.noon.NoonAuthAttemptResults.transientFact;
import static com.nuono.next.noon.NoonAuthAttemptResults.transientIdentityFailures;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.classifyMailboxFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.classifyOtpValidationFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.classifySendFailure;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.safeDiagnostic;

import com.nuono.next.noonauth.NoonAuthCheckpointVault;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthTransientFailure;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class NoonSharedEmailOtpAttempt {
    private final NoonSessionGateway sessionGateway;
    private final NoonEmailOtpReader emailOtpReader;
    private final Clock clock;
    private final Duration pollTimeout;
    private final NoonAuthCheckpointVault vault;
    private final NoonAuthGatewayCheckpointCodec codec;
    private final Duration checkpointTtl;
    private final NoonOtpChallengeStarter starter;
    private final NoonOtpMailboxPoller mailboxPoller;
    private final NoonProjectSessionRecovery projectSessionRecovery;

    NoonSharedEmailOtpAttempt(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            Duration pollInterval,
            Duration pollTimeout,
            Clock clock,
            NoonOtpMailboxPoller.Sleeper sleeper,
            String mailAuthCode,
            NoonAuthCheckpointVault vault,
            NoonAuthGatewayCheckpointCodec codec,
            Duration checkpointTtl
    ) {
        this.sessionGateway = sessionGateway;
        this.emailOtpReader = emailOtpReader;
        this.clock = clock;
        this.pollTimeout = pollTimeout;
        this.vault = vault;
        this.codec = codec;
        this.checkpointTtl = checkpointTtl;
        this.starter = new NoonOtpChallengeStarter(
                sessionGateway, emailOtpReader, clock, mailAuthCode, vault, codec, checkpointTtl
        );
        this.mailboxPoller = new NoonOtpMailboxPoller(emailOtpReader, clock, pollInterval, sleeper);
        this.projectSessionRecovery = new NoonProjectSessionRecovery(sessionGateway);
    }

    NoonAuthRecoveryAttemptResult run(NoonAuthRecoveryAttemptCommand command) {
        if (command == null || command.getProjectTargets().isEmpty()) {
            return failed(NoonAuthRecoveryFailureCode.INTERNAL_FAILURE, null, "missing recovery targets");
        }
        command.heartbeatOrThrow();
        NoonOtpChallengeStarter.StartResult start = starter.start(command);
        if (start.failure != null) {
            return start.failure;
        }
        if (start.restoredGrant != null) {
            return authenticatedProjects(start.restoredGrant, start.email, null, command);
        }

        boolean sendResultUnknown = false;
        NoonTransientErrorType sendTransientType = null;
        List<NoonAuthTransientFailure> observedTransientFailures = new ArrayList<>();
        if (!start.resumedChallenge) {
            try {
                sessionGateway.sendEmailOtp(start.generation);
            } catch (RuntimeException exception) {
                command.heartbeatOrThrow();
                sendTransientType = NoonProjectTransientFailureClassifier.classify(exception).orElse(null);
                NoonAuthRecoveryFailureCode sendFailure = classifySendFailure(exception);
                if (sendTransientType != null) {
                    observedTransientFailures.add(transientFact(
                            NoonAuthRecoveryFailureStage.OTP_SEND, sendTransientType
                    ));
                    sendResultUnknown = true;
                } else if (sendFailure == NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED
                        || sendFailure == NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED
                        || sendFailure == NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED) {
                    vault.clear(command.getRecoveryId());
                    return failed(sendFailure, null, safeDiagnostic("otp send rejected", exception));
                } else {
                    sendResultUnknown = true;
                }
            }
        }
        command.heartbeatOrThrow();

        Instant deadline = start.sentAt.plus(pollTimeout);
        Set<String> excludedMessageKeyHashes = new LinkedHashSet<>(command.getExcludedMessageKeyHashes());
        NoonEmailOtpReader.OtpCandidate validatedCandidate = null;
        NoonSessionGateway.EmailIdentityGrant grant = null;
        String lastInvalidMessageKeyHash = null;
        int distinctCandidateValidationCount = 0;
        while (distinctCandidateValidationCount < 2 && grant == null) {
            final Optional<NoonEmailOtpReader.OtpCandidate> candidate;
            try {
                candidate = mailboxPoller.waitForCandidate(
                        start.email,
                        start.mailAuthCode,
                        start.cursor,
                        start.sentAt,
                        deadline,
                        excludedMessageKeyHashes,
                        command
                );
            } catch (LeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                NoonAuthRecoveryFailureCode mailboxFailure = classifyMailboxFailure(exception);
                Optional<NoonTransientErrorType> transientType =
                        NoonProjectTransientFailureClassifier.classify(exception);
                if (mailboxFailure != NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED
                        && transientType.isPresent()) {
                    observedTransientFailures.add(transientFact(
                            NoonAuthRecoveryFailureStage.MAILBOX_POLLING, transientType.get()
                    ));
                    return transientIdentityFailures(
                            observedTransientFailures, lastInvalidMessageKeyHash
                    );
                }
                return failed(
                        mailboxFailure, lastInvalidMessageKeyHash,
                        safeDiagnostic("mailbox polling", exception)
                );
            }
            if (candidate.isEmpty()) {
                if (lastInvalidMessageKeyHash != null) {
                    return failed(
                            NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED,
                            lastInvalidMessageKeyHash,
                            "otp validation: invalid or expired"
                    );
                }
                if (sendResultUnknown && sendTransientType != null) {
                    return transientIdentityFailures(observedTransientFailures, null);
                }
                return failed(
                        sendResultUnknown
                                ? NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN
                                : NoonAuthRecoveryFailureCode.OTP_NOT_FOUND,
                        null,
                        sendResultUnknown
                                ? "send result unknown and no matching mail"
                                : "matching otp mail not found"
                );
            }

            NoonEmailOtpReader.OtpCandidate otpCandidate = candidate.get();
            distinctCandidateValidationCount++;
            command.heartbeatOrThrow();
            try {
                grant = sessionGateway.validateEmailOtp(start.generation, otpCandidate.getCode());
                validatedCandidate = otpCandidate;
            } catch (LeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                command.heartbeatOrThrow();
                Optional<NoonTransientErrorType> transientType =
                        NoonProjectTransientFailureClassifier.classify(exception);
                if (transientType.isPresent()) {
                    observedTransientFailures.add(transientFact(
                            NoonAuthRecoveryFailureStage.OTP_VALIDATION, transientType.get()
                    ));
                    return transientIdentityFailures(
                            observedTransientFailures, otpCandidate.getMessageKeyHash()
                    );
                }
                NoonAuthRecoveryFailureCode failureCode = classifyOtpValidationFailure(exception);
                if (failureCode == NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED) {
                    lastInvalidMessageKeyHash = otpCandidate.getMessageKeyHash();
                    excludedMessageKeyHashes.add(lastInvalidMessageKeyHash);
                    if (distinctCandidateValidationCount < 2) {
                        continue;
                    }
                    return failed(failureCode, lastInvalidMessageKeyHash,
                            "otp validation: invalid or expired");
                }
                return failed(failureCode, otpCandidate.getMessageKeyHash(),
                        safeDiagnostic("otp validation", exception));
            }
            command.heartbeatOrThrow();
        }

        if (grant == null || validatedCandidate == null) {
            return failed(
                    NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED,
                    lastInvalidMessageKeyHash,
                    "otp validation: invalid or expired"
            );
        }
        vault.save(
                command.getRecoveryId(),
                command.getGeneration(),
                NoonAuthCheckpointVault.Kind.IDENTITY_GRANT,
                codec.encodeGrant(sessionGateway.snapshotEmailIdentityGrant(grant)),
                clock.instant().plus(checkpointTtl)
        );
        try {
            emailOtpReader.acknowledge(start.email, start.mailAuthCode, validatedCandidate);
        } catch (RuntimeException ignored) {
            // The persisted message hash protects against reusing an acknowledged identity grant.
        }
        command.heartbeatOrThrow();
        return authenticatedProjects(grant, start.email, validatedCandidate.getMessageKeyHash(), command);
    }

    private NoonAuthRecoveryAttemptResult authenticatedProjects(
            NoonSessionGateway.EmailIdentityGrant grant,
            String email,
            String messageKeyHash,
            NoonAuthRecoveryAttemptCommand command
    ) {
        return NoonAuthRecoveryAttemptResult.authenticated(
                messageKeyHash,
                projectSessionRecovery.recover(grant, email, command.getProjectTargets(), command)
        );
    }
}

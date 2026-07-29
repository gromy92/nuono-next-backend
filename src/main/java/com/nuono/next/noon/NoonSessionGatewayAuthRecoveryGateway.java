package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult.Code;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonAuthTransientFailure;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.mail.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class NoonSessionGatewayAuthRecoveryGateway implements NoonAuthRecoveryGateway {

    private final NoonSessionGateway sessionGateway;
    private final NoonEmailOtpReader emailOtpReader;
    private final Duration pollInterval;
    private final Duration pollTimeout;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public NoonSessionGatewayAuthRecoveryGateway(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            @Value("${nuono.noon.auth.email-otp.poll-interval-millis:5000}") long pollIntervalMillis,
            @Value("${nuono.noon.auth.email-otp.poll-timeout-millis:90000}") long pollTimeoutMillis
    ) {
        this(
                sessionGateway,
                emailOtpReader,
                Duration.ofMillis(Math.max(250L, pollIntervalMillis)),
                Duration.ofMillis(Math.max(1000L, pollTimeoutMillis)),
                Clock.systemUTC(),
                Thread::sleep
        );
    }

    NoonSessionGatewayAuthRecoveryGateway(
            NoonSessionGateway sessionGateway,
            NoonEmailOtpReader emailOtpReader,
            Duration pollInterval,
            Duration pollTimeout,
            Clock clock,
            Sleeper sleeper
    ) {
        this.sessionGateway = sessionGateway;
        this.emailOtpReader = emailOtpReader;
        this.pollInterval = pollInterval;
        this.pollTimeout = pollTimeout;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public synchronized NoonAuthRecoveryAttemptResult attempt(NoonAuthRecoveryAttemptCommand command) {
        if (command == null || command.getProjectTargets().isEmpty()) {
            return failed(NoonAuthRecoveryFailureCode.INTERNAL_FAILURE, null, "missing recovery targets");
        }
        command.heartbeatOrThrow();

        final String email;
        final String mailAuthCode;
        try {
            email = sessionGateway.configuredMerchantEmail();
            mailAuthCode = sessionGateway.configuredMerchantMailAuthCode();
        } catch (RuntimeException exception) {
            return failed(NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED, null, "mailbox credential unavailable");
        }

        final NoonSessionGateway.EmailOtpGeneration generation;
        try {
            generation = sessionGateway.prepareEmailOtpGeneration(email);
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            Optional<NoonTransientErrorType> transientType =
                    NoonProjectTransientFailureClassifier.classify(exception);
            if (transientType.isPresent()) {
                return transientIdentityFailure(
                        NoonAuthRecoveryFailureStage.IDENTITY_PREPARATION,
                        transientType.get(),
                        null
                );
            }
            return failed(classifyIdentityFailure(exception), null, safeDiagnostic("identity preparation", exception));
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
            if (mailboxFailure != NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED
                    && transientType.isPresent()) {
                return transientIdentityFailure(
                        NoonAuthRecoveryFailureStage.MAILBOX_SNAPSHOT,
                        transientType.get(),
                        null
                );
            }
            return failed(mailboxFailure, null, safeDiagnostic("mailbox snapshot", exception));
        }
        command.heartbeatOrThrow();
        command.beforeOtpSendOrThrow();

        Instant sentAt = clock.instant();
        boolean sendResultUnknown = false;
        NoonTransientErrorType sendTransientType = null;
        List<NoonAuthTransientFailure> observedTransientFailures = new ArrayList<>();
        try {
            sessionGateway.sendEmailOtp(generation);
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            sendTransientType = NoonProjectTransientFailureClassifier.classify(exception)
                    .orElse(null);
            NoonAuthRecoveryFailureCode sendFailure = classifySendFailure(exception);
            if (sendTransientType != null) {
                observedTransientFailures.add(transientFact(
                        NoonAuthRecoveryFailureStage.OTP_SEND,
                        sendTransientType
                ));
                sendResultUnknown = true;
            } else if (sendFailure == NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED
                    || sendFailure == NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED
                    || sendFailure == NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED) {
                return failed(sendFailure, null, safeDiagnostic("otp send rejected", exception));
            } else {
                sendResultUnknown = true;
            }
        }
        command.heartbeatOrThrow();

        Instant deadline = sentAt.plus(pollTimeout);
        Set<String> excludedMessageKeyHashes = new LinkedHashSet<>(command.getExcludedMessageKeyHashes());
        NoonEmailOtpReader.OtpCandidate validatedCandidate = null;
        NoonSessionGateway.EmailIdentityGrant grant = null;
        String lastInvalidMessageKeyHash = null;
        int distinctCandidateValidationCount = 0;
        while (distinctCandidateValidationCount < 2 && grant == null) {
            final Optional<NoonEmailOtpReader.OtpCandidate> candidate;
            try {
                candidate = waitForCandidate(
                        email,
                        mailAuthCode,
                        cursor,
                        sentAt,
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
                            NoonAuthRecoveryFailureStage.MAILBOX_POLLING,
                            transientType.get()
                    ));
                    return transientIdentityFailures(
                            observedTransientFailures,
                            lastInvalidMessageKeyHash
                    );
                }
                return failed(
                        mailboxFailure,
                        lastInvalidMessageKeyHash,
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
                NoonAuthRecoveryFailureCode code = sendResultUnknown
                        ? NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN
                        : NoonAuthRecoveryFailureCode.OTP_NOT_FOUND;
                if (sendResultUnknown && sendTransientType != null) {
                    return transientIdentityFailures(observedTransientFailures, null);
                }
                return failed(
                        code,
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
                grant = sessionGateway.validateEmailOtp(generation, otpCandidate.getCode());
                validatedCandidate = otpCandidate;
            } catch (LeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                command.heartbeatOrThrow();
                Optional<NoonTransientErrorType> transientType =
                        NoonProjectTransientFailureClassifier.classify(exception);
                if (transientType.isPresent()) {
                    observedTransientFailures.add(transientFact(
                            NoonAuthRecoveryFailureStage.OTP_VALIDATION,
                            transientType.get()
                    ));
                    return transientIdentityFailures(
                            observedTransientFailures,
                            otpCandidate.getMessageKeyHash()
                    );
                }
                NoonAuthRecoveryFailureCode failureCode = classifyOtpValidationFailure(exception);
                if (failureCode == NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED) {
                    lastInvalidMessageKeyHash = otpCandidate.getMessageKeyHash();
                    excludedMessageKeyHashes.add(lastInvalidMessageKeyHash);
                    if (distinctCandidateValidationCount < 2) {
                        continue;
                    }
                    return failed(
                            failureCode,
                            lastInvalidMessageKeyHash,
                            "otp validation: invalid or expired"
                    );
                }
                return failed(
                        failureCode,
                        otpCandidate.getMessageKeyHash(),
                        safeDiagnostic("otp validation", exception)
                );
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

        try {
            emailOtpReader.acknowledge(email, mailAuthCode, validatedCandidate);
        } catch (RuntimeException ignored) {
            // The recovery result persists the message hash, so a transient IMAP acknowledge failure
            // must not discard an already authenticated identity grant.
        }
        command.heartbeatOrThrow();

        List<NoonAuthRecoveryProjectResult> projectResults = new ArrayList<>();
        for (NoonAuthRecoveryProjectTarget target : command.getProjectTargets()) {
            projectResults.add(createAndValidateProject(grant, target, email, command));
        }
        command.heartbeatOrThrow();
        return NoonAuthRecoveryAttemptResult.authenticated(
                validatedCandidate.getMessageKeyHash(),
                projectResults
        );
    }

    private Optional<NoonEmailOtpReader.OtpCandidate> waitForCandidate(
            String email,
            String mailAuthCode,
            NoonEmailOtpReader.MailboxCursor cursor,
            Instant sentAt,
            Instant deadline,
            Set<String> excludedMessageKeyHashes,
            NoonAuthRecoveryAttemptCommand command
    ) {
        while (clock.instant().isBefore(deadline)) {
            command.heartbeatOrThrow();
            Optional<NoonEmailOtpReader.OtpCandidate> candidate = emailOtpReader.pollAfter(
                    email,
                    mailAuthCode,
                    cursor,
                    sentAt,
                    excludedMessageKeyHashes
            );
            command.heartbeatOrThrow();
            if (candidate.isPresent()
                    && StringUtils.hasText(candidate.get().getMessageKeyHash())
                    && !excludedMessageKeyHashes.contains(candidate.get().getMessageKeyHash())) {
                return candidate;
            }
            if (!clock.instant().isBefore(deadline)) {
                return Optional.empty();
            }
            sleep(pollInterval);
            command.heartbeatOrThrow();
        }
        return Optional.empty();
    }

    private NoonAuthRecoveryProjectResult createAndValidateProject(
            NoonSessionGateway.EmailIdentityGrant grant,
            NoonAuthRecoveryProjectTarget target,
            String expectedEmail,
            NoonAuthRecoveryAttemptCommand command
    ) {
        command.heartbeatOrThrow();
        final NoonSessionGateway.ProjectSessionCookie projectSession;
        try {
            projectSession = sessionGateway.createEmailOtpProjectSession(
                    grant,
                    target.getProjectCode(),
                    target.getStoreCode()
            );
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            String message = throwableMessage(exception).toLowerCase(Locale.ROOT);
            if (message.contains("不包含当前项目") || message.contains("does not contain")) {
                return NoonAuthRecoveryProjectResult.failed(
                        target,
                        Code.PROJECT_ACCESS_DENIED,
                        safeDiagnostic("project session", exception)
                );
            }
            Optional<NoonTransientErrorType> transientType =
                    NoonProjectTransientFailureClassifier.classify(exception);
            if (transientType.isPresent()) {
                return transientFailure(
                        target,
                        NoonAuthRecoveryFailureStage.PROJECT_SESSION_CREATE,
                        transientType.get()
                );
            }
            return NoonAuthRecoveryProjectResult.failed(
                    target,
                    Code.SESSION_CREATE_FAILED,
                    safeDiagnostic("project session", exception)
            );
        }
        command.heartbeatOrThrow();

        final JsonNode whoami;
        try {
            whoami = sessionGateway.whoamiWithProjectSession(
                    projectSession,
                    target.getStoreCode()
            );
            command.heartbeatOrThrow();
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            return projectValidationFailure(
                    target,
                    NoonAuthRecoveryFailureStage.WHOAMI_VALIDATION,
                    "whoami validation",
                    exception
            );
        }
        if (!NoonProjectSessionValidator.validatesProjectSession(
                whoami,
                expectedEmail,
                target.getProjectCode(),
                projectSession
        )) {
            return NoonAuthRecoveryProjectResult.failed(
                    target,
                    Code.COOKIE_VALIDATION_FAILED,
                    "project cookie validation: identity or target project not confirmed"
            );
        }

        try {
            sessionGateway.validateCatalogProjectSession(
                    projectSession,
                    target.getStoreCode()
            );
            command.heartbeatOrThrow();
            return NoonAuthRecoveryProjectResult.recovered(target, projectSession.getCookie(), grant.getUserCode());
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            return projectValidationFailure(
                    target,
                    NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                    "catalog validation",
                    exception
            );
        }
    }

    private NoonAuthRecoveryProjectResult projectValidationFailure(
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryFailureStage failureStage,
            String operation,
            RuntimeException exception
    ) {
        Optional<NoonTransientErrorType> transientType =
                NoonProjectTransientFailureClassifier.classify(exception);
        if (transientType.isPresent()) {
            return transientFailure(target, failureStage, transientType.get());
        }
        return NoonAuthRecoveryProjectResult.failed(
                target,
                Code.COOKIE_VALIDATION_FAILED,
                safeDiagnostic(operation, exception)
        );
    }

    private NoonAuthRecoveryProjectResult transientFailure(
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientType
    ) {
        return NoonAuthRecoveryProjectResult.transientFailure(
                target,
                failureStage,
                transientType,
                failureStage.name() + ": transient " + transientType.name()
        );
    }

    private NoonAuthRecoveryAttemptResult failed(
            NoonAuthRecoveryFailureCode code,
            String messageKeyHash,
            String diagnostic
    ) {
        return NoonAuthRecoveryAttemptResult.failed(code, messageKeyHash, diagnostic);
    }

    private NoonAuthRecoveryAttemptResult transientIdentityFailure(
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientType,
            String messageKeyHash
    ) {
        return transientIdentityFailures(
                List.of(transientFact(failureStage, transientType)),
                messageKeyHash
        );
    }

    private NoonAuthRecoveryAttemptResult transientIdentityFailures(
            List<NoonAuthTransientFailure> failures,
            String messageKeyHash
    ) {
        String diagnostic = failures.size() == 1
                ? failures.get(0).getSafeDiagnostic()
                : "multiple exact transient failures";
        return NoonAuthRecoveryAttemptResult.transientFailures(
                failures,
                messageKeyHash,
                diagnostic
        );
    }

    private NoonAuthTransientFailure transientFact(
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientType
    ) {
        return new NoonAuthTransientFailure(
                failureStage,
                transientType,
                failureStage.name() + ": transient " + transientType.name()
        );
    }

    private NoonAuthRecoveryFailureCode classifySendFailure(Throwable throwable) {
        NoonHttpException httpFailure = findNoonHttpException(throwable);
        if (httpFailure != null && httpFailure.hasStatusCode(401, 403)) {
            return NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("429")
                || message.contains("418")
                || message.contains("too many requests")
                || message.contains("rate limit")
                || message.contains("ip_channel")) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        if (message.contains("captcha")
                || message.contains("risk control")
                || message.contains("blocked by risk")) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        return NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN;
    }

    private NoonAuthRecoveryFailureCode classifyMailboxFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED;
            }
            current = current.getCause();
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("authentication failed")
                || message.contains("login failed")
                || message.contains("auth code")) {
            return NoonAuthRecoveryFailureCode.MAILBOX_AUTH_FAILED;
        }
        return NoonAuthRecoveryFailureCode.MAILBOX_UNAVAILABLE;
    }

    private NoonAuthRecoveryFailureCode classifyOtpValidationFailure(Throwable throwable) {
        NoonHttpException httpFailure = findNoonHttpException(throwable);
        if (httpFailure != null && httpFailure.hasStatusCode(418, 429)) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("429")
                || message.contains("418")
                || message.contains("too many requests")
                || message.contains("rate limit")
                || message.contains("ip_channel")) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        if (message.contains("captcha")
                || message.contains("risk control")
                || message.contains("blocked by risk")) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        if (httpFailure != null
                && httpFailure.hasStatusCode(400, 401)
                && httpFailure.responseBodyContainsAny("invalid", "expired", "验证码失效")) {
            return NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED;
        }
        if (message.contains("invalid")
                || message.contains("expired")
                || message.contains("credential")
                || message.contains("验证码")) {
            return NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED;
        }
        return NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
    }

    private NoonHttpException findNoonHttpException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                return (NoonHttpException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private NoonAuthRecoveryFailureCode classifyIdentityFailure(Throwable throwable) {
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("too many requests") || message.contains("rate limit")) {
            return NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED;
        }
        if (message.contains("captcha") || message.contains("risk control")) {
            return NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED;
        }
        return NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED;
    }

    private String safeDiagnostic(String operation, Throwable throwable) {
        String message = throwableMessage(throwable).toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("too many requests") || message.contains("rate limit")) {
            return operation + ": rate limited";
        }
        if (message.contains("captcha") || message.contains("risk control") || message.contains("blocked by risk")) {
            return operation + ": risk blocked";
        }
        if (message.contains("invalid") || message.contains("expired")) {
            return operation + ": invalid or expired";
        }
        return operation + ": failed";
    }

    private String throwableMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Noon 验证码邮件时被中断。", exception);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

}

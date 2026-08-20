package com.nuono.next.noon;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

final class NoonOtpMailboxPoller {
    private final NoonEmailOtpReader reader;
    private final Clock clock;
    private final Duration interval;
    private final Sleeper sleeper;

    NoonOtpMailboxPoller(NoonEmailOtpReader reader, Clock clock, Duration interval, Sleeper sleeper) {
        this.reader = reader;
        this.clock = clock;
        this.interval = interval;
        this.sleeper = sleeper;
    }

    Optional<NoonEmailOtpReader.OtpCandidate> waitForCandidate(
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
            Optional<NoonEmailOtpReader.OtpCandidate> candidate = reader.pollAfter(
                    email, mailAuthCode, cursor, sentAt, excludedMessageKeyHashes
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
            sleep();
            command.heartbeatOrThrow();
        }
        return Optional.empty();
    }

    private void sleep() {
        try {
            sleeper.sleep(Math.max(1L, interval.toMillis()));
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

package com.nuono.next.noon;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.StringUtils;

final class NoonEdgeAccessGuard {

    private final Clock clock;
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(Instant.EPOCH);

    NoonEdgeAccessGuard() {
        this(Clock.systemUTC());
    }

    NoonEdgeAccessGuard(Clock clock) {
        this.clock = clock;
    }

    void requireAvailable() {
        Instant now = clock.instant();
        Instant currentBlockedUntil = blockedUntil.get();
        if (!currentBlockedUntil.isAfter(now)) {
            return;
        }
        long remainingSeconds = Math.max(
                1L,
                Duration.between(now, currentBlockedUntil).getSeconds()
        );
        throw new NoonEdgeAccessDeniedException(
                "Noon 登录出口仍在冷却，约 "
                        + displayMinutes(remainingSeconds)
                        + " 分钟后再试；请先验证固定出口。"
        );
    }

    NoonEdgeAccessDeniedException block(long holdSeconds) {
        long safeHoldSeconds = Math.max(1L, holdSeconds);
        Instant proposedBlockedUntil = clock.instant().plusSeconds(safeHoldSeconds);
        blockedUntil.accumulateAndGet(
                proposedBlockedUntil,
                (current, proposed) -> current.isAfter(proposed) ? current : proposed
        );
        return new NoonEdgeAccessDeniedException(
                "Noon 登录出口被边缘访问策略拦截；已停止登录兜底并进入 "
                        + displayMinutes(safeHoldSeconds)
                        + " 分钟冷却，请验证固定出口后再重试。"
        );
    }

    static boolean matches(int statusCode, String responseBody) {
        if (statusCode != 403 || !StringUtils.hasText(responseBody)) {
            return false;
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT);
        boolean accessDenied = normalized.contains("<title>access denied</title>")
                || normalized.contains("<h1>access denied</h1>");
        boolean edgeSuite = normalized.contains("errors.edgesuite.net")
                || normalized.contains("errors&#46;edgesuite&#46;net");
        return accessDenied && edgeSuite;
    }

    private static long displayMinutes(long seconds) {
        return Math.max(1L, (seconds + 59L) / 60L);
    }
}

final class NoonEdgeAccessDeniedException extends IllegalStateException {
    NoonEdgeAccessDeniedException(String message) {
        super(message);
    }
}

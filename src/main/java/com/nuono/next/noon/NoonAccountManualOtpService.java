package com.nuono.next.noon;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Holds one short-lived, in-memory manual OTP challenge for the configured Noon account.
 *
 * <p>No OTP, PKCE material, access token or cookie is persisted. Restarting the process expires
 * the challenge and requires a new, explicitly requested send.</p>
 */
@Service
@Profile("local-db")
public final class NoonAccountManualOtpService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    private final NoonAccountManualOtpGateway gateway;
    private final NoonAccountProjectSessionRefresher projectSessionRefresher;
    private final Clock clock;
    private final SecureRandom random;
    private ActiveChallenge activeChallenge;
    private NoonAccountSessionStatus lastStatus = NoonAccountSessionStatus.UNKNOWN;
    private String lastMessage = "Noon 账号尚未校验；系统不会自动发送验证码。";

    NoonAccountManualOtpService(
            NoonAccountManualOtpGateway gateway,
            NoonAccountProjectSessionRefresher projectSessionRefresher,
            Clock clock,
            SecureRandom random
    ) {
        this.gateway = gateway;
        this.projectSessionRefresher = projectSessionRefresher;
        this.clock = clock;
        this.random = random;
    }

    public NoonAccountManualOtpService(
            NoonAccountManualOtpGateway gateway,
            NoonAccountProjectSessionRefresher projectSessionRefresher
    ) {
        this(gateway, projectSessionRefresher, Clock.systemUTC(), new SecureRandom());
    }

    public synchronized NoonAccountSessionView status() {
        expireIfNecessary();
        if (activeChallenge == null) {
            return new NoonAccountSessionView(
                    lastStatus,
                    null,
                    null,
                    lastMessage
            );
        }
        return activeChallenge.view();
    }

    public synchronized NoonAccountSessionView send(Long operatorUserId) {
        requireOperator(operatorUserId);
        expireIfNecessary();
        if (activeChallenge != null) {
            if (!operatorUserId.equals(activeChallenge.operatorUserId)) {
                throw new IllegalStateException("已有另一位账号管理员正在处理 Noon 验证码。");
            }
            return activeChallenge.view();
        }
        NoonAccountManualOtpGateway.PreparedChallenge prepared = gateway.sendOneManualOtp();
        Instant expiresAt = clock.instant().plus(CHALLENGE_TTL);
        activeChallenge = new ActiveChallenge(
                operatorUserId,
                newChallengeId(),
                prepared,
                expiresAt
        );
        lastStatus = NoonAccountSessionStatus.OTP_SENT;
        lastMessage = "验证码已由账号管理员主动请求；请输入一次验证码完成 Noon 登录。";
        return activeChallenge.view();
    }

    public synchronized NoonAccountSessionView verify(Long operatorUserId, String challengeId, String otpCode) {
        requireOperator(operatorUserId);
        expireIfNecessary();
        if (activeChallenge == null) {
            throw new IllegalStateException("验证码挑战已失效，请人工重新发送。"
            );
        }
        if (!operatorUserId.equals(activeChallenge.operatorUserId)
                || !activeChallenge.challengeId.equals(challengeId)) {
            throw new IllegalArgumentException("验证码挑战不属于当前账号管理员。"
            );
        }
        if (!StringUtils.hasText(otpCode)) {
            throw new IllegalArgumentException("请输入 Noon 验证码。"
            );
        }
        if (activeChallenge.verificationSubmitted) {
            throw new IllegalStateException("该验证码挑战已提交，系统不会重复校验。"
            );
        }
        activeChallenge.verificationSubmitted = true;
        try {
            NoonAccountProjectSessionRefresher.RefreshResult refreshed = projectSessionRefresher.refresh(
                    gateway.validateSubmittedOtp(activeChallenge.prepared, otpCode.trim()),
                    operatorUserId
            );
            if (refreshed.getFailedProjects() > 0) {
                lastStatus = NoonAccountSessionStatus.MANUAL_ACTION_REQUIRED;
                lastMessage = "账号已验证，但部分 Project 会话未更新；系统没有再次发送验证码。";
                return new NoonAccountSessionView(lastStatus, null, null, lastMessage);
            }
            lastStatus = NoonAccountSessionStatus.ACTIVE;
            lastMessage = "Noon 账号已验证，全部已绑定 Project 会话已更新。";
            return new NoonAccountSessionView(lastStatus, null, null, lastMessage);
        } catch (RuntimeException exception) {
            lastStatus = NoonAccountSessionStatus.MANUAL_OTP_REQUIRED;
            lastMessage = "本次验证码校验未完成；系统不会自动重试或再次发送，请人工重新发起。";
            throw exception;
        } finally {
            // A submitted OTP is one-shot even if Noon rejects it or the network result is unknown.
            activeChallenge = null;
        }
    }

    synchronized void recordDailySessionCheck(boolean active) {
        expireIfNecessary();
        if (activeChallenge != null) {
            return;
        }
        lastStatus = active ? NoonAccountSessionStatus.ACTIVE : NoonAccountSessionStatus.MANUAL_OTP_REQUIRED;
        lastMessage = active
                ? "Noon 账号日常会话校验通过。"
                : "Noon 账号会话已失效；请由账号管理员人工发送一次验证码。";
    }

    synchronized void recordAuthenticationRequired() {
        expireIfNecessary();
        if (activeChallenge != null) {
            return;
        }
        lastStatus = NoonAccountSessionStatus.MANUAL_OTP_REQUIRED;
        lastMessage = "Noon 账号会话已失效；请由账号管理员人工发送一次验证码。";
    }

    synchronized boolean blocksProviderCalls() {
        expireIfNecessary();
        return lastStatus == NoonAccountSessionStatus.MANUAL_OTP_REQUIRED
                || lastStatus == NoonAccountSessionStatus.OTP_SENT
                || lastStatus == NoonAccountSessionStatus.MANUAL_ACTION_REQUIRED;
    }

    private void expireIfNecessary() {
        if (activeChallenge != null && !clock.instant().isBefore(activeChallenge.expiresAt)) {
            activeChallenge = null;
            lastStatus = NoonAccountSessionStatus.MANUAL_OTP_REQUIRED;
            lastMessage = "验证码挑战已过期，请由账号管理员重新人工发送。";
        }
    }

    private String newChallengeId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireOperator(Long operatorUserId) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("缺少 Noon 账号管理员身份。"
            );
        }
    }

    private static final class ActiveChallenge {
        private final Long operatorUserId;
        private final String challengeId;
        private final NoonAccountManualOtpGateway.PreparedChallenge prepared;
        private final Instant expiresAt;
        private boolean verificationSubmitted;

        private ActiveChallenge(
                Long operatorUserId,
                String challengeId,
                NoonAccountManualOtpGateway.PreparedChallenge prepared,
                Instant expiresAt
        ) {
            this.operatorUserId = operatorUserId;
            this.challengeId = challengeId;
            this.prepared = prepared;
            this.expiresAt = expiresAt;
        }

        private NoonAccountSessionView view() {
            return new NoonAccountSessionView(
                    NoonAccountSessionStatus.OTP_SENT,
                    challengeId,
                    expiresAt,
                    "验证码已由账号管理员主动请求；请输入一次验证码完成 Noon 登录。"
            );
        }
    }
}

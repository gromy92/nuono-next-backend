package com.nuono.next.auth;

import com.nuono.next.infrastructure.mapper.AuthEmailCodeChallengeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class AuthEmailCodeService {

    static final String PURPOSE_LOGIN = "LOGIN";

    private static final Base64.Encoder SALT_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthEmailCodeChallengeMapper challengeMapper;
    private final AuthEmailCodeSender sender;
    private final LocalDbAuthService localDbAuthService;
    private final AuthEmailCodeProperties properties;
    private final Clock clock;
    private final Supplier<String> codeSupplier;
    private final Supplier<String> saltSupplier;

    @Autowired
    public AuthEmailCodeService(
            AuthEmailCodeChallengeMapper challengeMapper,
            AuthEmailCodeSender sender,
            LocalDbAuthService localDbAuthService,
            AuthEmailCodeProperties properties
    ) {
        this(
                challengeMapper,
                sender,
                localDbAuthService,
                properties,
                Clock.systemDefaultZone(),
                () -> generateNumericCode(properties.getCodeLength()),
                AuthEmailCodeService::generateSalt
        );
    }

    AuthEmailCodeService(
            AuthEmailCodeChallengeMapper challengeMapper,
            AuthEmailCodeSender sender,
            LocalDbAuthService localDbAuthService,
            AuthEmailCodeProperties properties,
            Clock clock,
            Supplier<String> codeSupplier,
            Supplier<String> saltSupplier
    ) {
        this.challengeMapper = challengeMapper;
        this.sender = sender;
        this.localDbAuthService = localDbAuthService;
        this.properties = properties;
        this.clock = clock;
        this.codeSupplier = codeSupplier;
        this.saltSupplier = saltSupplier;
    }

    @Transactional
    public void requestLoginCode(AuthEmailCodeRequestCommand command) {
        String email = normalizeAllowedEmail(command == null ? null : command.getEmail());
        LocalDateTime now = now();
        AuthEmailCodeChallengeRecord latest = challengeMapper.selectLatestChallenge(email, PURPOSE_LOGIN);
        if (latest != null && latest.getCreatedAt() != null
                && latest.getCreatedAt().plusSeconds(properties.getCooldownSeconds()).isAfter(now)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试。");
        }

        String code = codeSupplier.get();
        String salt = saltSupplier.get();
        LocalDateTime expiresAt = now.plusSeconds(properties.getTtlSeconds());
        AuthEmailCodeChallengeRecord challenge = new AuthEmailCodeChallengeRecord();
        challenge.setEmail(email);
        challenge.setPurpose(PURPOSE_LOGIN);
        challenge.setCodeSalt(salt);
        challenge.setCodeHash(AuthEmailCodeHashSupport.hash(email, PURPOSE_LOGIN, code, salt));
        challenge.setExpiresAt(expiresAt);
        challenge.setAttemptCount(0);

        challengeMapper.consumeActiveChallenges(email, PURPOSE_LOGIN, now);
        challengeMapper.insertChallenge(challenge);
        sender.sendLoginCode(email, code, expiresAt);
    }

    @Transactional
    public AuthLoginResult login(AuthEmailCodeLoginCommand command) {
        String email = normalizeAllowedEmail(command == null ? null : command.getEmail());
        String code = normalizeCode(command == null ? null : command.getCode());
        LocalDateTime now = now();
        AuthEmailCodeChallengeRecord challenge = challengeMapper.selectLatestActiveChallenge(email, PURPOSE_LOGIN, now);
        if (challenge == null || challenge.getId() == null) {
            throw invalidCode();
        }
        if (challenge.getAttemptCount() != null && challenge.getAttemptCount() >= properties.getMaxAttempts()) {
            challengeMapper.consumeChallenge(challenge.getId(), now);
            throw invalidCode();
        }
        String expectedHash = AuthEmailCodeHashSupport.hash(email, PURPOSE_LOGIN, code, challenge.getCodeSalt());
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                challenge.getCodeHash().getBytes(StandardCharsets.UTF_8)
        )) {
            challengeMapper.incrementAttempts(challenge.getId(), now);
            throw invalidCode();
        }
        challengeMapper.consumeChallenge(challenge.getId(), now);
        return localDbAuthService.loginByEmailCode(email, properties.getLoginAccountNo());
    }

    private String normalizeAllowedEmail(String value) {
        String email = normalizeEmail(value);
        String allowedEmail = normalizeOptionalEmail(properties.getAllowedEmail());
        if (!StringUtils.hasText(allowedEmail)) {
            throw new IllegalStateException("邮箱验证码登录未配置允许邮箱。");
        }
        if (!allowedEmail.equals(email)) {
            throw new IllegalArgumentException("当前邮箱不允许登录。");
        }
        return email;
    }

    private String normalizeEmail(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请输入邮箱。");
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex >= email.length() - 3
                || !email.substring(atIndex + 1).contains(".")) {
            throw new IllegalArgumentException("请输入有效邮箱。");
        }
        return email;
    }

    private String normalizeOptionalEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return normalizeEmail(value);
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请输入邮箱验证码。");
        }
        String code = value.trim();
        for (int index = 0; index < code.length(); index += 1) {
            if (!Character.isDigit(code.charAt(index))) {
                throw invalidCode();
            }
        }
        return code;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("邮箱验证码不正确或已过期。");
    }

    private static String generateNumericCode(int length) {
        int maxExclusive = 1;
        for (int index = 0; index < length; index += 1) {
            maxExclusive *= 10;
        }
        return String.format("%0" + length + "d", RANDOM.nextInt(maxExclusive));
    }

    private static String generateSalt() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return SALT_ENCODER.encodeToString(bytes);
    }
}

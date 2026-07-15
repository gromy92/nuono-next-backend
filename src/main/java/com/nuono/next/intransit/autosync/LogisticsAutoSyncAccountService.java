package com.nuono.next.intransit.autosync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LogisticsAutoSyncAccountService {
    private static final String STATUS_UNVERIFIED = "UNVERIFIED";
    private static final int DEFAULT_MIN_INTERVAL_HOURS = 24;
    private static final int MIN_INTERVAL_HOURS = 1;
    private static final int MAX_INTERVAL_HOURS = 168;

    private final LogisticsAutoSyncMapper mapper;
    private final LogisticsCredentialCipher credentialCipher;

    public LogisticsAutoSyncAccountService(
            LogisticsAutoSyncMapper mapper,
            LogisticsCredentialCipher credentialCipher
    ) {
        this.mapper = mapper;
        this.credentialCipher = credentialCipher;
    }

    @Transactional
    public LogisticsAutoSyncAccountView save(LogisticsAutoSyncAccountCommand command, Long actorUserId) {
        if (command == null) {
            throw new IllegalArgumentException("物流自动同步账号参数不能为空。");
        }
        Long accountId = command.getAccountId();
        if (accountId == null) {
            return LogisticsAutoSyncAccountView.from(createAccount(command, actorUserId));
        }
        LogisticsAutoSyncAccount existing = requireAccount(accountId);
        ensureSameOwner(existing, command.getOwnerUserId());
        LogisticsAutoSyncAccount updated = buildAccount(command, actorUserId, existing);
        if (mapper.updateAccount(updated) <= 0) {
            throw new IllegalStateException("物流自动同步账号更新失败。");
        }
        return LogisticsAutoSyncAccountView.from(updated);
    }

    @Transactional(readOnly = true)
    public List<LogisticsAutoSyncAccountView> list(Long ownerUserId) {
        requireValue(ownerUserId, "ownerUserId");
        return mapper.listAccounts(ownerUserId).stream()
                .map(LogisticsAutoSyncAccountView::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LogisticsAutoSyncAccount requireAccount(Long accountId) {
        requireValue(accountId, "accountId");
        LogisticsAutoSyncAccount row = mapper.selectAccountById(accountId);
        if (row == null) {
            throw new IllegalArgumentException("物流自动同步账号不存在。");
        }
        return row;
    }

    private LogisticsAutoSyncAccount createAccount(LogisticsAutoSyncAccountCommand command, Long actorUserId) {
        requireText(command.getPassword(), "password");
        LogisticsAutoSyncAccount row = buildAccount(command, actorUserId, null);
        row.setId(mapper.nextAccountId());
        row.setCreatedBy(actorUserId);
        if (mapper.insertAccount(row) <= 0) {
            throw new IllegalStateException("物流自动同步账号创建失败。");
        }
        return row;
    }

    private LogisticsAutoSyncAccount buildAccount(
            LogisticsAutoSyncAccountCommand command,
            Long actorUserId,
            LogisticsAutoSyncAccount existing
    ) {
        Long ownerUserId = requireValue(command.getOwnerUserId(), "ownerUserId");
        Long operatorUserId = requireValue(command.getOperatorUserId(), "operatorUserId");
        String sourceSystem = normalizeSourceSystem(command.getSourceSystem());
        String forwarderName = requireText(command.getForwarderName(), "forwarderName");
        String loginAccount = requireText(command.getLoginAccount(), "loginAccount");
        String normalizedLoginAccount = normalizeLoginAccount(loginAccount);
        boolean passwordChanged = StringUtils.hasText(command.getPassword());

        LogisticsAutoSyncAccount row = existing == null ? new LogisticsAutoSyncAccount() : copyRunState(existing);
        row.setOwnerUserId(ownerUserId);
        row.setOperatorUserId(operatorUserId);
        row.setSourceSystem(sourceSystem);
        row.setForwarderName(forwarderName);
        row.setLoginAccount(loginAccount);
        row.setLoginAccountHash(loginAccountHash(sourceSystem, normalizedLoginAccount));
        row.setPasswordCipher(resolvePasswordCipher(command, existing));
        row.setEnabled(Boolean.TRUE.equals(command.getEnabled()));
        row.setScheduleEnabled(Boolean.TRUE.equals(command.getScheduleEnabled()));
        row.setCommitEnabled(Boolean.TRUE.equals(command.getCommitEnabled()));
        row.setScheduleWindowStart(command.getScheduleWindowStart());
        row.setScheduleWindowEnd(command.getScheduleWindowEnd());
        row.setMinIntervalHours(clampMinIntervalHours(command.getMinIntervalHours()));
        row.setVerificationStatus(resolveVerificationStatus(existing, row, passwordChanged));
        row.setUpdatedBy(actorUserId);
        return row;
    }

    private LogisticsAutoSyncAccount copyRunState(LogisticsAutoSyncAccount existing) {
        LogisticsAutoSyncAccount row = new LogisticsAutoSyncAccount();
        row.setId(existing.getId());
        row.setLastLoginStatus(existing.getLastLoginStatus());
        row.setLastPreviewStatus(existing.getLastPreviewStatus());
        row.setLastSyncStatus(existing.getLastSyncStatus());
        row.setLastTaskId(existing.getLastTaskId());
        row.setLastSyncedAt(existing.getLastSyncedAt());
        row.setNextEligibleAt(existing.getNextEligibleAt());
        row.setCooldownUntil(existing.getCooldownUntil());
        row.setLastFailureCode(existing.getLastFailureCode());
        row.setLastFailureMessage(existing.getLastFailureMessage());
        row.setCreatedBy(existing.getCreatedBy());
        row.setCreatedAt(existing.getCreatedAt());
        row.setUpdatedAt(existing.getUpdatedAt());
        return row;
    }

    private static void ensureSameOwner(LogisticsAutoSyncAccount existing, Long ownerUserId) {
        Long requestedOwnerUserId = requireValue(ownerUserId, "ownerUserId");
        if (!Objects.equals(existing.getOwnerUserId(), requestedOwnerUserId)) {
            throw new IllegalArgumentException("物流自动同步账号不属于当前业务 owner。");
        }
    }

    private String resolvePasswordCipher(
            LogisticsAutoSyncAccountCommand command,
            LogisticsAutoSyncAccount existing
    ) {
        if (StringUtils.hasText(command.getPassword())) {
            return credentialCipher.encrypt(command.getPassword());
        }
        if (existing != null) {
            return existing.getPasswordCipher();
        }
        return null;
    }

    private static String resolveVerificationStatus(
            LogisticsAutoSyncAccount existing,
            LogisticsAutoSyncAccount row,
            boolean passwordChanged
    ) {
        if (existing == null || passwordChanged || identityChanged(existing, row)) {
            return STATUS_UNVERIFIED;
        }
        return StringUtils.hasText(existing.getVerificationStatus())
                ? existing.getVerificationStatus()
                : STATUS_UNVERIFIED;
    }

    private static boolean identityChanged(LogisticsAutoSyncAccount existing, LogisticsAutoSyncAccount row) {
        return !Objects.equals(existing.getOwnerUserId(), row.getOwnerUserId())
                || !Objects.equals(existing.getOperatorUserId(), row.getOperatorUserId())
                || !Objects.equals(existing.getSourceSystem(), row.getSourceSystem())
                || !Objects.equals(normalizeLoginAccount(existing.getLoginAccount()), normalizeLoginAccount(row.getLoginAccount()));
    }

    private static String normalizeSourceSystem(String value) {
        String source = requireText(value, "sourceSystem").toUpperCase(Locale.ROOT).replace("-", "_");
        switch (source) {
            case "CHIC":
            case "QI_KE":
            case "QIKE":
                return "CHIC";
            case "ET":
            case "YITONG":
            case "YI_TONG":
                return "ET";
            case "YITE":
            case "YI_TE":
                return "YITE";
            case "ZD":
            case "ZDSEA":
            case "ZHONGDONG":
                return "ZD";
            default:
                throw new IllegalArgumentException("不支持的物流自动同步渠道：" + value);
        }
    }

    private static String normalizeLoginAccount(String value) {
        return requireText(value, "loginAccount").toLowerCase(Locale.ROOT);
    }

    private static int clampMinIntervalHours(Integer value) {
        int hours = value == null ? DEFAULT_MIN_INTERVAL_HOURS : value;
        return Math.max(MIN_INTERVAL_HOURS, Math.min(MAX_INTERVAL_HOURS, hours));
    }

    private static String loginAccountHash(String sourceSystem, String normalizedLoginAccount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((sourceSystem + "|" + normalizedLoginAccount).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("物流自动同步账号 hash 生成失败。", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("物流自动同步账号缺少必填字段：" + fieldName);
        }
        return value.trim();
    }

    private static Long requireValue(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("物流自动同步账号缺少必填字段：" + fieldName);
        }
        return value;
    }
}

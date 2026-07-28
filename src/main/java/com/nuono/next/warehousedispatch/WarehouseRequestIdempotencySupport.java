package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.DispatchPlanView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

abstract class WarehouseRequestIdempotencySupport extends WarehouseDispatchValueSupport {

    protected static final class RequestFingerprint {
        private final String current;
        private final String legacy;

        private RequestFingerprint(String current, String legacy) {
            this.current = current;
            this.legacy = legacy;
        }

        protected String persistedValue() {
            return current;
        }

        private boolean matches(String persisted) {
            return current.equals(persisted) || legacy.equals(persisted);
        }
    }

    protected WarehouseRequestIdempotencySupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

    protected String normalizeDispatchClientRequestId(String value) {
        return normalizeClientRequestId(
                value,
                "发货申请缺少客户端请求号，请刷新后重试。",
                "发货申请客户端请求号不能超过 100 个字符。"
        );
    }

    protected String normalizeReceiptClientRequestId(String value) {
        return normalizeClientRequestId(
                value,
                "收货确认缺少客户端请求号，请刷新后重试。",
                "收货确认客户端请求号不能超过 100 个字符。"
        );
    }

    protected RequestFingerprint dispatchRequestFingerprint(CreateDispatchPlanCommand command) {
        List<String> sources = emptyIfNull(command.sources).stream()
                .map(this::dispatchSourceFingerprint)
                .sorted()
                .collect(Collectors.toList());
        return new RequestFingerprint(
                sha256(String.join("|",
                        fingerprintField(trimToNull(command.remark)),
                        fingerprintField(String.join(",", sources))
                )),
                legacyDispatchRequestFingerprint(command)
        );
    }

    protected RequestFingerprint confirmationRequestFingerprint(ConfirmationCommand command) {
        String confirmationType = normalizeConfirmationType(command.confirmationType);
        List<String> lines = emptyIfNull(command.lines).stream()
                .map(this::confirmationLineFingerprint)
                .sorted()
                .collect(Collectors.toList());
        return new RequestFingerprint(
                sha256(String.join("|",
                        fingerprintField(trimToNull(command.purchaseOrderId)),
                        fingerprintField(confirmationType),
                        fingerprintField(trimToNull(command.sourcePartyName)),
                        fingerprintField(trimToNull(command.remark)),
                        fingerprintField(String.join(",", lines))
                )),
                legacyConfirmationRequestFingerprint(command, confirmationType)
        );
    }

    protected DispatchPlanView lockAndReplayDispatchPlan(
            Long ownerUserId,
            String clientRequestId,
            RequestFingerprint requestFingerprint
    ) {
        requireRequestOwnerLock(ownerUserId);
        DispatchPlanRecord existing = mapper.selectDispatchPlanByClientRequestId(ownerUserId, clientRequestId);
        if (existing == null) {
            return null;
        }
        requireMatchingRequestFingerprint(
                existing.requestFingerprint,
                requestFingerprint,
                "同一客户端请求号不能提交不同的发货商品。"
        );
        return toDispatchPlanView(existing);
    }

    protected void requireMatchingRequestFingerprint(
            String persistedFingerprint,
            RequestFingerprint requestedFingerprint,
            String conflictMessage
    ) {
        if (StringUtils.hasText(persistedFingerprint)
                && !requestedFingerprint.matches(persistedFingerprint)) {
            throw new WarehouseRequestConflictException(conflictMessage);
        }
    }

    protected void requireRequestOwnerLock(Long ownerUserId) {
        Long lockedOwnerUserId = mapper.lockDispatchOwner(ownerUserId);
        if (ownerUserId == null || !ownerUserId.equals(lockedOwnerUserId)) {
            throw new IllegalStateException("仓库业务归属账号不存在，无法安全提交请求。");
        }
    }

    private String normalizeClientRequestId(String value, String missingMessage, String lengthMessage) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(missingMessage);
        }
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(lengthMessage);
        }
        return normalized;
    }

    private String dispatchSourceFingerprint(DispatchPlanSourceCommand source) {
        if (source == null) {
            return fingerprintField(null);
        }
        return String.join("|",
                fingerprintField(source.fulfillmentBalanceId),
                fingerprintField(nonNull(source.quantity)),
                fingerprintField(normalizedFingerprintToken(source.targetSiteCode)),
                fingerprintField(normalizedFingerprintToken(source.actualTransportMode))
        );
    }

    private String confirmationLineFingerprint(ConfirmationLineCommand line) {
        if (line == null) {
            return fingerprintField(null);
        }
        return String.join("|",
                fingerprintField(trimToNull(line.purchaseOrderItemId)),
                fingerprintField(line.purchaseOrderItemSiteId),
                fingerprintField(line.fulfillmentBalanceId),
                fingerprintField(line.confirmedQuantity),
                fingerprintField(line.abnormalQuantity),
                fingerprintField(line.normalReceivedQuantity),
                fingerprintField(line.replenishmentQuantity),
                fingerprintField(trimToNull(line.replenishmentReason)),
                fingerprintField(line.returnQuantity),
                fingerprintField(line.damageQuantity),
                fingerprintField(line.overReceivedQuantity),
                fingerprintField(trimToNull(line.keeperSnapshotJson)),
                fingerprintField(trimToNull(line.exceptionReason))
        );
    }

    private String legacyDispatchRequestFingerprint(CreateDispatchPlanCommand command) {
        List<String> sources = emptyIfNull(command.sources).stream()
                .filter(source -> source != null)
                .map(source -> String.valueOf(source.fulfillmentBalanceId) + "|"
                        + nonNull(source.quantity) + "|"
                        + defaultText(normalizedFingerprintToken(source.targetSiteCode), "") + "|"
                        + defaultText(normalizedFingerprintToken(source.actualTransportMode), ""))
                .sorted()
                .collect(Collectors.toList());
        return sha256(String.join(";", sources));
    }

    private String legacyConfirmationRequestFingerprint(
            ConfirmationCommand command,
            String confirmationType
    ) {
        List<String> lines = emptyIfNull(command.lines).stream()
                .filter(line -> line != null)
                .map(this::legacyConfirmationLineFingerprint)
                .sorted()
                .collect(Collectors.toList());
        return sha256(String.join(";",
                defaultText(trim(command.purchaseOrderId), ""),
                confirmationType,
                defaultText(trim(command.sourcePartyName), ""),
                defaultText(trim(command.remark), ""),
                String.join(",", lines)
        ));
    }

    private String legacyConfirmationLineFingerprint(ConfirmationLineCommand line) {
        return String.join("|",
                defaultText(trim(line.purchaseOrderItemId), ""),
                String.valueOf(line.purchaseOrderItemSiteId),
                String.valueOf(line.fulfillmentBalanceId),
                String.valueOf(line.confirmedQuantity),
                String.valueOf(line.abnormalQuantity),
                String.valueOf(line.normalReceivedQuantity),
                String.valueOf(line.replenishmentQuantity),
                defaultText(trim(line.replenishmentReason), ""),
                String.valueOf(line.returnQuantity),
                String.valueOf(line.damageQuantity),
                String.valueOf(line.overReceivedQuantity),
                defaultText(trim(line.keeperSnapshotJson), ""),
                defaultText(trim(line.exceptionReason), "")
        );
    }

    private String normalizedFingerprintToken(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String fingerprintField(Object value) {
        String text = value == null ? "<null>" : String.valueOf(value);
        return text.length() + ":" + text;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", exception);
        }
    }
}

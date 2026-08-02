package com.nuono.next.product;

import com.nuono.next.product.publish.ProductPublishCommandService;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classifies authorization failures before a durable product task is suspended.
 *
 * <p>The policy keeps the safety decision independent from task persistence: once an earlier write
 * may have happened, callers must require a Noon readback instead of replaying the original task.</p>
 */
final class ProductPublishAuthSuspensionPolicy {

    private ProductPublishAuthSuspensionPolicy() {
    }

    static Decision forPublish(IllegalStateException failure, String priorTaskResultStatus) {
        ProductWriteAuthRequiredException authRequired =
                ProductWriteAuthRequiredException.find(failure);
        if (authRequired == null) {
            ProductPublishWriteOutcomeUnknownException outcomeUnknown =
                    ProductPublishWriteOutcomeUnknownException.find(failure);
            if (outcomeUnknown == null) {
                return null;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("priorWriteCompleted", outcomeUnknown.isPriorWriteCompleted());
            metadata.put("writeOperation", outcomeUnknown.getOperation());
            return new Decision(
                    null,
                    true,
                    "Noon 发布可能已进入写入阶段，但官方结果无法确认。系统不会自动重放；"
                            + "请先从 Noon 同步核对，再人工决定是否重试。",
                    ProductPublishWriteOutcomeUnknownException.ERROR_CODE,
                    metadata
            );
        }
        boolean writeMayHaveOccurred = authRequired.isWriteMayHaveOccurred()
                || failure instanceof ProductGroupPartialPublishException
                || isWriteProgressStatus(priorTaskResultStatus);
        String message = writeMayHaveOccurred
                ? "Noon 授权恢复中，本次发布可能已进入写入阶段。系统不会自动重放；恢复后请先从 Noon 同步核对，再人工确认是否重试。"
                : "Noon 授权恢复中，本次发布已停止。系统不会自动重放；恢复后请人工重新确认。";
        return new Decision(
                authRequired,
                writeMayHaveOccurred,
                message,
                ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING,
                Map.of()
        );
    }

    static Decision forDelete(IllegalStateException failure, boolean afterUnmapStage) {
        ProductWriteAuthRequiredException authRequired =
                ProductWriteAuthRequiredException.find(failure);
        if (authRequired == null) {
            return null;
        }
        boolean writeMayHaveOccurred = authRequired.isWriteMayHaveOccurred() || afterUnmapStage;
        String message = writeMayHaveOccurred
                ? "Noon 授权恢复中，本次删除已进入写入阶段。系统不会自动继续删除或重建；恢复后请先核对 Noon 当前结果，再人工点击重试。"
                : "Noon 授权恢复中，本次删除已停在安全检查点；恢复成功后系统会自动继续原任务。";
        return new Decision(
                authRequired,
                writeMayHaveOccurred,
                message,
                ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING,
                Map.of()
        );
    }

    private static boolean isWriteProgressStatus(String status) {
        return "submitted".equalsIgnoreCase(status)
                || "pending_effective".equalsIgnoreCase(status)
                || "write_unknown".equalsIgnoreCase(status)
                || "verify_timeout".equalsIgnoreCase(status);
    }

    static final class Decision {
        private final ProductWriteAuthRequiredException authRequired;
        private final boolean writeMayHaveOccurred;
        private final String message;
        private final String errorCode;
        private final Map<String, Object> metadata;

        private Decision(
                ProductWriteAuthRequiredException authRequired,
                boolean writeMayHaveOccurred,
                String message,
                String errorCode,
                Map<String, Object> metadata
        ) {
            this.authRequired = authRequired;
            this.writeMayHaveOccurred = writeMayHaveOccurred;
            this.message = message;
            this.errorCode = errorCode;
            this.metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
        }

        Long getRecoveryId() {
            return authRequired != null ? authRequired.getRecoveryId() : null;
        }

        boolean isWriteMayHaveOccurred() {
            return writeMayHaveOccurred;
        }

        String getMessage() {
            return message;
        }

        String getErrorCode() {
            return errorCode;
        }

        Map<String, Object> newResultMetadata() {
            Map<String, Object> result = new LinkedHashMap<>(metadata);
            addResultMetadata(result);
            return result;
        }

        void addResultMetadata(Map<String, Object> metadata) {
            if (authRequired != null && authRequired.getRecoveryId() != null) {
                metadata.put("recoveryId", authRequired.getRecoveryId());
            }
            metadata.put("writeMayHaveOccurred", writeMayHaveOccurred);
        }
    }
}

package com.nuono.next.product.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductWriteAuthRecovery;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ProductPublishAuthRetryGuard {
    private static final Set<String> UNSAFE_REPLAY_ERROR_CODES = Set.of(
            "product_write_outcome_unknown",
            "group_partial_write_unknown",
            "noon_effect_not_confirmed"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    void requireSafeToRetry(
            ProductPublishTaskRecord task,
            ProductWriteAuthRecovery authRecovery
    ) {
        if (task == null) {
            return;
        }
        String errorCode = normalize(task.getErrorCode());
        if (ProductPublishCommandService.ERROR_CODE_NOON_AUTH_RECOVERY_PENDING
                .equalsIgnoreCase(errorCode)) {
            authRecovery.requireAvailable(
                    task.getOwnerUserId(),
                    task.getProjectCode(),
                    task.getStoreCode()
            );
        }
        if (isDelete(task)) {
            if (!ProductDeleteRetrySafety.canResume(task)) {
                throw new IllegalStateException(
                        "本次商品删除缺少可证明安全的恢复检查点，系统不会重复发送删除写入；请先在 Noon 后台核对。"
                );
            }
            return;
        }
        if (isUnsafeReplayError(errorCode) || writeMayHaveOccurred(task.getResultJson())) {
            throw new IllegalStateException(
                    "本次发布已有部分写入或结果不确定，不能直接重放原任务。请先从 Noon 同步，核对后再创建新的发布任务。"
            );
        }
    }

    private boolean writeMayHaveOccurred(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return false;
        }
        try {
            JsonNode result = objectMapper.readTree(resultJson);
            if (result == null || !result.isObject()) {
                return true;
            }
            JsonNode marker = result.get("writeMayHaveOccurred");
            return marker != null && (!marker.isBoolean() || marker.booleanValue());
        } catch (Exception exception) {
            return true;
        }
    }

    private boolean isDelete(ProductPublishTaskRecord task) {
        return ProductPublishTaskClassifier.isProductDelete(task);
    }

    private boolean isUnsafeReplayError(String errorCode) {
        return StringUtils.hasText(errorCode)
                && UNSAFE_REPLAY_ERROR_CODES.contains(errorCode.toLowerCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

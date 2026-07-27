package com.nuono.next.product;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Signals that an existing-product publish may have reached Noon and must not be replayed
 * automatically as a whole task.
 */
final class ProductPublishWriteOutcomeUnknownException extends IllegalStateException {

    static final String ERROR_CODE = "product_write_outcome_unknown";

    private final String operation;
    private final boolean priorWriteCompleted;

    private ProductPublishWriteOutcomeUnknownException(
            String operation,
            boolean priorWriteCompleted,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.operation = operation;
        this.priorWriteCompleted = priorWriteCompleted;
    }

    static ProductPublishWriteOutcomeUnknownException forProviderFailure(
            String operation,
            boolean priorWriteCompleted,
            RuntimeException failure
    ) {
        ProductPublishWriteOutcomeUnknownException existing = find(failure);
        if (existing != null) {
            return priorWriteCompleted ? existing.withPriorWriteCompleted() : existing;
        }
        String resolvedOperation = StringUtils.hasText(operation) ? operation.trim() : "Noon 写入";
        String message = priorWriteCompleted
                ? resolvedOperation + " 失败；此前已有写入成功，本次发布结果需要人工核对。"
                : resolvedOperation + " 请求结果未知，本次发布需要人工核对。";
        String detail = failure != null && StringUtils.hasText(failure.getMessage())
                ? shrink(failure.getMessage())
                : "";
        if (StringUtils.hasText(detail)) {
            message += " 原因：" + detail;
        }
        return new ProductPublishWriteOutcomeUnknownException(
                resolvedOperation,
                priorWriteCompleted,
                message,
                failure
        );
    }

    static void runProviderWrite(
            String operation,
            boolean priorWriteCompleted,
            Runnable providerWrite
    ) {
        try {
            providerWrite.run();
        } catch (RuntimeException failure) {
            ProductWriteAuthRequiredException authRequired =
                    ProductWriteAuthRequiredException.find(failure);
            if (authRequired != null) {
                throw priorWriteCompleted
                        ? authRequired.withWriteMayHaveOccurred()
                        : authRequired;
            }
            ProductPublishWriteOutcomeUnknownException existing = find(failure);
            if (existing != null) {
                throw priorWriteCompleted ? existing.withPriorWriteCompleted() : existing;
            }
            if (!priorWriteCompleted && hasDeterministicHttpResponse(failure)) {
                throw failure;
            }
            throw forProviderFailure(operation, priorWriteCompleted, failure);
        }
    }

    static void postJson(
            ProductNoonAdapter adapter,
            NoonSession session,
            String url,
            ObjectNode body,
            boolean authReplayAllowed,
            boolean priorWriteCompleted,
            String operation
    ) {
        runProviderWrite(
                operation,
                priorWriteCompleted,
                () -> adapter.postWriteJson(session, url, body, authReplayAllowed)
        );
    }

    static void postJson(
            ProductNoonAdapter adapter,
            NoonSession session,
            String url,
            ObjectNode body,
            Map<String, String> headers,
            boolean priorWriteCompleted,
            String operation
    ) {
        runProviderWrite(
                operation,
                priorWriteCompleted,
                () -> adapter.postWriteJson(session, url, body, false, headers)
        );
    }

    static RuntimeException afterStageFailure(
            String operation,
            boolean priorWriteCompleted,
            RuntimeException failure
    ) {
        ProductWriteAuthRequiredException authRequired =
                ProductWriteAuthRequiredException.find(failure);
        if (authRequired != null) {
            return priorWriteCompleted && !authRequired.isWriteMayHaveOccurred()
                    ? authRequired.withWriteMayHaveOccurred()
                    : failure;
        }
        ProductPublishWriteOutcomeUnknownException existing = find(failure);
        if (existing != null) {
            return priorWriteCompleted ? existing.withPriorWriteCompleted() : existing;
        }
        return priorWriteCompleted
                ? forProviderFailure(operation, true, failure)
                : failure;
    }

    String getOperation() {
        return operation;
    }

    boolean isPriorWriteCompleted() {
        return priorWriteCompleted;
    }

    boolean isWriteMayHaveOccurred() {
        return true;
    }

    ProductPublishWriteOutcomeUnknownException withPriorWriteCompleted() {
        if (priorWriteCompleted) {
            return this;
        }
        return new ProductPublishWriteOutcomeUnknownException(
                operation,
                true,
                operation + " 失败；此前已有写入成功，本次发布结果需要人工核对。",
                this
        );
    }

    static ProductPublishWriteOutcomeUnknownException find(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ProductPublishWriteOutcomeUnknownException) {
                return (ProductPublishWriteOutcomeUnknownException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean hasDeterministicHttpResponse(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                int status = ((NoonHttpException) current).getStatusCode();
                return status != 408
                        && status != 500
                        && status != 502
                        && status != 503
                        && status != 504;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String shrink(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 220 ? text : text.substring(0, 220) + "...";
    }
}

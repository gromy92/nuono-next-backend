package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

final class ProductListingIdentityLockManager {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductListingIdentityLockManager.class);
    private static final int LOCK_TIMEOUT_SECONDS = 5;

    private final ProductListingMapper mapper;

    ProductListingIdentityLockManager(ProductListingMapper mapper) {
        this.mapper = mapper;
    }

    List<String> acquireProductIdentityLocks(
            Long ownerUserId,
            String storeCode,
            String partnerSku,
            String barcode
    ) {
        List<String> lockKeys = new ArrayList<>();
        if (StringUtils.hasText(partnerSku)) {
            lockKeys.add(lockKey(
                    "psku",
                    ownerUserId,
                    storeCode,
                    partnerSku
            ));
        }
        if (StringUtils.hasText(barcode)) {
            lockKeys.add(lockKey(
                    "barcode",
                    ownerUserId,
                    storeCode,
                    barcode
            ));
        }
        Collections.sort(lockKeys);
        List<String> acquired = new ArrayList<>();
        try {
            for (String lockKey : lockKeys) {
                Integer result = mapper.acquireIdentityLock(
                        lockKey,
                        LOCK_TIMEOUT_SECONDS
                );
                if (!Integer.valueOf(1).equals(result)) {
                    throw new IllegalArgumentException(
                            "商品身份正在被其他上架任务校验，请稍后重试。"
                    );
                }
                acquired.add(lockKey);
            }
            return acquired;
        } catch (RuntimeException exception) {
            release(acquired);
            throw exception;
        }
    }

    List<String> acquireDraftSourceLock(
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        if (command == null
                || command.getDraftId() != null
                || !StringUtils.hasText(command.getSourceType())
                || command.getSourceRefId() == null) {
            return List.of();
        }
        String lockKey = lockKey(
                "source",
                ownerUserId,
                storeCode,
                command.getSourceType().trim()
                        + ":" + command.getSourceRefId()
        );
        Integer result = mapper.acquireIdentityLock(
                lockKey,
                LOCK_TIMEOUT_SECONDS
        );
        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalArgumentException(
                    "同一来源的商品上架草稿正在保存，请稍后重试。"
            );
        }
        return List.of(lockKey);
    }

    void release(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            return;
        }
        for (int index = lockKeys.size() - 1; index >= 0; index--) {
            try {
                mapper.releaseIdentityLock(lockKeys.get(index));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Failed to release product listing identity lock",
                        exception
                );
            }
        }
    }

    boolean deferReleaseUntilTransactionCompletion(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()
                || !TransactionSynchronizationManager
                .isActualTransactionActive()
                || !TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return false;
        }
        AtomicBoolean released = new AtomicBoolean(false);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        releaseOnce(lockKeys, released);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        releaseOnce(lockKeys, released);
                    }
                }
        );
        return true;
    }

    private void releaseOnce(
            List<String> lockKeys,
            AtomicBoolean released
    ) {
        if (released.compareAndSet(false, true)) {
            release(lockKeys);
        }
    }

    private String lockKey(
            String type,
            Long ownerUserId,
            String storeCode,
            String value
    ) {
        return type + ":" + ownerUserId + ":"
                + normalized(storeCode).toUpperCase(Locale.ROOT)
                + ":" + normalized(value).toUpperCase(Locale.ROOT);
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

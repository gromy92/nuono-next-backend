package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

final class ProductListingSharedRecoveryEvidence {
    private final ObjectMapper objectMapper;

    ProductListingSharedRecoveryEvidence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void requireExact(
            ProductListingTaskRecord realRun,
            Long ownerUserId,
            String expectedNoonResultJson,
            Long expectedRecoveryId
    ) {
        if (realRun == null
                || expectedRecoveryId == null
                || expectedNoonResultJson == null
                || !"REAL_RUN".equalsIgnoreCase(realRun.getMode())
                || !Objects.equals(ownerUserId, realRun.getOwnerUserId())
                || !Objects.equals(expectedNoonResultJson, realRun.getNoonResultJson())
                || !ProductListingWriteAuthRecovery.FAILURE_CODE.equalsIgnoreCase(
                        realRun.getFailureCode())
                || !Objects.equals(
                        expectedRecoveryId,
                        recoveryId(realRun.getNoonResultJson()))) {
            throw new ProductListingReauthenticationException(
                    "共享授权恢复任务状态已变化，请刷新后重试。");
        }
    }

    private Long recoveryId(String noonResultJson) {
        try {
            ProductListingNoonWriteResult result = objectMapper.readValue(
                    noonResultJson,
                    ProductListingNoonWriteResult.class
            );
            return result == null ? null : result.getRecoveryId();
        } catch (JsonProcessingException exception) {
            throw new ProductListingReauthenticationException(
                    "共享授权恢复任务证据无法解析，请人工核对。");
        }
    }
}

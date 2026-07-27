package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

final class ProductImageSuiteRetryCoordinator {
    private final ProductImageProfileMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private ProductWriteAuthRecovery authRecovery = ProductWriteAuthRecovery.disabled();

    ProductImageSuiteRetryCoordinator(
            ProductImageProfileMapper mapper,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    void setProductWriteAuthRecovery(ProductWriteAuthRecovery authRecovery) {
        if (authRecovery != null) {
            this.authRecovery = authRecovery;
        }
    }

    void approve(
            ProductImageProfileRecord profile,
            Long suiteId,
            Long ownerUserId,
            String storeCode,
            Long operatorUserId
    ) {
        ProductImageSuiteRecord suite = requireSuite(profile.getId(), suiteId, true);
        ProductImagePublishCheckpoint attempt =
                ProductImagePublishCheckpoint.start(mapper.selectSuiteAssets(suite.getId()));
        if (!StringUtils.hasText(mapper.selectSkuParentByProductMasterId(profile.getProductMasterId()))) {
            throw new IllegalArgumentException("该商品尚未在 Noon 上线，不能发布图片。");
        }
        if (mapper.startSuitePublishWorkflow(
                suite.getId(),
                profile.getId(),
                attempt.toJson(objectMapper),
                operatorUserId
        ) == 0) {
            throw new IllegalArgumentException("只有待审核或当前采用套图可以审核通过。");
        }
        eventPublisher.publishEvent(new ProductImagePublishSubmittedEvent(
                suite.getId(),
                ownerUserId,
                storeCode,
                operatorUserId,
                attempt.attemptId()
        ));
    }

    void retry(
            ProductImageProfileRecord profile,
            Long suiteId,
            Long ownerUserId,
            String storeCode,
            Long operatorUserId
    ) {
        ProductImageSuiteRecord observed = requireSuite(profile.getId(), suiteId, false);
        requireFailed(observed);
        requireAvailableForAuthRetry(profile, observed, ownerUserId, storeCode);
        ProductImageSuiteRecord suite = requireSuite(profile.getId(), suiteId, true);
        requireFailed(suite);
        requireAvailableForAuthRetry(profile, suite, ownerUserId, storeCode);
        boolean publishRetry = StringUtils.hasText(suite.getFailureStage())
                && suite.getFailureStage().toUpperCase(Locale.ROOT).startsWith("PUBLISH");
        if (publishRetry) {
            retryPublish(profile, suite, ownerUserId, storeCode, operatorUserId);
            return;
        }
        if (mapper.retryFailedSuiteWorkflow(
                suite.getId(),
                profile.getId(),
                ProductImageSuiteStatus.PENDING_GENERATION,
                operatorUserId
        ) == 0) {
            throw retryConflict();
        }
        eventPublisher.publishEvent(new ProductImageGenerationSubmittedEvent(
                suite.getId(), ownerUserId, storeCode, operatorUserId
        ));
    }

    private void retryPublish(
            ProductImageProfileRecord profile,
            ProductImageSuiteRecord suite,
            Long ownerUserId,
            String storeCode,
            Long operatorUserId
    ) {
        List<ProductImageSuiteAssetRecord> currentAssets = mapper.selectSuiteAssets(suite.getId());
        ProductImagePublishCheckpoint attempt = ProductImagePublishCheckpoint.renew(
                objectMapper,
                suite.getPublishManifestJson(),
                currentAssets
        );
        if (mapper.retryFailedSuitePublishWorkflow(
                suite.getId(),
                profile.getId(),
                attempt.toJson(objectMapper),
                operatorUserId
        ) == 0) {
            throw retryConflict();
        }
        eventPublisher.publishEvent(new ProductImagePublishSubmittedEvent(
                suite.getId(),
                ownerUserId,
                storeCode,
                operatorUserId,
                attempt.attemptId()
        ));
    }

    private void requireAvailableForAuthRetry(
            ProductImageProfileRecord profile,
            ProductImageSuiteRecord suite,
            Long ownerUserId,
            String storeCode
    ) {
        if (!StringUtils.hasText(suite.getFailureStage())
                || !"PUBLISH_AUTH_RECOVERY".equalsIgnoreCase(suite.getFailureStage().trim())) {
            return;
        }
        String canonicalStoreCode = StringUtils.hasText(profile.getStoreCode())
                ? profile.getStoreCode()
                : storeCode;
        authRecovery.requireAvailable(ownerUserId, canonicalStoreCode, canonicalStoreCode);
    }

    private void requireFailed(ProductImageSuiteRecord suite) {
        if (suite.getSuiteStatus() != ProductImageSuiteStatus.FAILED) {
            throw new IllegalArgumentException("只有失败任务可以重试。");
        }
    }

    private ProductImageSuiteRecord requireSuite(Long profileId, Long suiteId, boolean forUpdate) {
        ProductImageSuiteRecord suite = forUpdate
                ? mapper.selectSuiteByIdForUpdate(suiteId, profileId)
                : mapper.selectSuiteById(suiteId, profileId);
        if (suite == null) {
            throw new ProductImageProfileNotFoundException("商品图资料不存在或无权访问。");
        }
        return suite;
    }

    private IllegalStateException retryConflict() {
        return new IllegalStateException("该失败任务已被其他请求重试，请刷新后查看最新状态。");
    }
}

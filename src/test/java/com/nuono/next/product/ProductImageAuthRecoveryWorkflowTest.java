package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ProductImageAuthRecoveryWorkflowTest {
    private final ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private ProductImageProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductImageProfileService(
                mapper,
                mock(OperationsSkinMapper.class),
                mock(ProductPublicDetailMapper.class),
                mock(AiCapabilityService.class),
                eventPublisher
        );
    }

    @Test
    void incompleteProfileShouldBlockBeforeCreatingImageSuite() {
        ProductImageProfileRecord profile = profile();
        profile.setBrand(null);
        profile.setTitleEn(null);
        profile.setSpecSummary(null);
        profile.setProductFactText(null);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 3001L, 10003L)
        );

        assertTrue(failure.getMessage().contains("品牌"));
        assertTrue(failure.getMessage().contains("基础图片"));
        verify(mapper, never()).insertSuite(any());
    }

    @Test
    void approvalShouldLockSuiteAndFreezeOrderedAssetsInAttemptManifest() {
        ProductImageProfileRecord profile = profile();
        profile.setProductMasterId(9001L);
        ProductImageSuiteRecord suite = suite(ProductImageSuiteStatus.PENDING_REVIEW);
        ProductImageSuiteAssetRecord main = asset(
                5001L,
                "/api/product-images/assets/STR108065-NAE/main.png",
                "sha-main",
                10
        );
        ProductImageSuiteAssetRecord size = asset(
                5002L,
                "/api/product-images/assets/STR108065-NAE/size.png",
                "sha-size",
                20
        );
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteByIdForUpdate(9901L, 7001L)).thenReturn(suite);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(main, size));
        when(mapper.selectSkuParentByProductMasterId(9001L)).thenReturn("PARENT-1");
        when(mapper.startSuitePublishWorkflow(
                eq(9901L), eq(7001L), anyString(), eq(10003L)
        )).thenReturn(1);
        when(mapper.selectSuites(7001L)).thenReturn(List.of());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());

        service.approveSuite(307L, "STR108065-NAE", 7001L, 9901L, 10003L);

        ArgumentCaptor<String> manifest = ArgumentCaptor.forClass(String.class);
        verify(mapper).startSuitePublishWorkflow(
                eq(9901L), eq(7001L), manifest.capture(), eq(10003L)
        );
        ProductImagePublishCheckpoint checkpoint =
                ProductImagePublishCheckpoint.parse(new ObjectMapper(), manifest.getValue());
        assertEquals(
                List.of(main.getImageUrl(), size.getImageUrl()),
                checkpoint.approvedImageUrls()
        );
        assertTrue(manifest.getValue().contains("\"assetId\":5001"));
        assertTrue(manifest.getValue().contains("\"sha256\":\"sha-main\""));
        assertTrue(manifest.getValue().contains("\"assetId\":5002"));
        assertTrue(manifest.getValue().contains("\"sha256\":\"sha-size\""));
        ArgumentCaptor<ProductImagePublishSubmittedEvent> event =
                ArgumentCaptor.forClass(ProductImagePublishSubmittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertTrue(checkpoint.matchesAttempt(event.getValue().attemptId()));
    }

    @Test
    void authSuspendedSuiteShouldRetryPublishOnlyAfterFailedStateCas() {
        ProductImageProfileRecord profile = profile();
        ProductImageSuiteRecord suite = suite(ProductImageSuiteStatus.FAILED);
        suite.setFailureStage("PUBLISH_AUTH_RECOVERY");
        ProductImageSuiteAssetRecord asset = asset(
                5001L,
                "/api/product-images/assets/STR108065-NAE/main.png",
                "sha-main",
                10
        );
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteById(9901L, 7001L)).thenReturn(suite);
        when(mapper.selectSuiteByIdForUpdate(9901L, 7001L)).thenReturn(suite);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(asset));
        when(mapper.retryFailedSuitePublishWorkflow(
                eq(9901L), eq(7001L), anyString(), eq(10003L)
        )).thenReturn(1);
        when(mapper.selectSuites(7001L)).thenReturn(List.of());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());

        service.retrySuite(307L, "STR108065-NAE", 7001L, 9901L, 10003L);

        ArgumentCaptor<String> manifest = ArgumentCaptor.forClass(String.class);
        verify(mapper).retryFailedSuitePublishWorkflow(
                eq(9901L), eq(7001L), manifest.capture(), eq(10003L)
        );
        ProductImagePublishCheckpoint checkpoint =
                ProductImagePublishCheckpoint.parse(new ObjectMapper(), manifest.getValue());
        assertEquals(List.of(asset.getImageUrl()), checkpoint.approvedImageUrls());
        ArgumentCaptor<ProductImagePublishSubmittedEvent> event =
                ArgumentCaptor.forClass(ProductImagePublishSubmittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertTrue(checkpoint.matchesAttempt(event.getValue().attemptId()));
        verify(eventPublisher, never()).publishEvent(any(ProductImageGenerationSubmittedEvent.class));
    }

    @Test
    void publishingSuiteAssetsShouldBeImmutable() {
        ProductImageProfileRecord profile = profile();
        ProductImageSuiteRecord suite = suite(ProductImageSuiteStatus.PUBLISHING);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteById(9901L, 7001L)).thenReturn(suite);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.deleteSuiteAsset(
                        307L, "STR108065-NAE", 7001L, 9901L, 5001L, 10003L
                )
        );

        assertTrue(failure.getMessage().contains("正在发布"));
        verify(mapper, never()).deleteSuiteAsset(9901L, 5001L);
    }

    @Test
    void stalePublishingSuiteShouldBecomeRetryableFailure() {
        ProductImagePublishRecoveryScheduler scheduler =
                new ProductImagePublishRecoveryScheduler(mapper);
        ReflectionTestUtils.setField(scheduler, "staleMinutes", 30);
        when(mapper.failStalePublishingSuites(30, 0L)).thenReturn(1);

        scheduler.recover();

        verify(mapper).failStalePublishingSuites(30, 0L);
    }

    private ProductImageProfileRecord profile() {
        ProductImageProfileRecord profile = new ProductImageProfileRecord();
        profile.setId(7001L);
        profile.setOwnerUserId(307L);
        profile.setStoreCode("STR108065-NAE");
        profile.setPskuCode("PAPERSAYSB024");
        profile.setBrand("PAPERSAY");
        profile.setTitleEn("Magnetic Whiteboard Markers");
        profile.setSpecSummary("Standard product specification");
        profile.setProductFactText("Verified product facts");
        return profile;
    }

    private ProductImageSuiteRecord suite(ProductImageSuiteStatus status) {
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setId(9901L);
        suite.setProfileId(7001L);
        suite.setSuiteStatus(status);
        return suite;
    }

    private ProductImageSuiteAssetRecord asset(
            Long id,
            String imageUrl,
            String sha256,
            int sortOrder
    ) {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(id);
        asset.setImageUrl(imageUrl);
        asset.setSha256(sha256);
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
